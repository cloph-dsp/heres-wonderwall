package com.wonderwall.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wonderwall.app.bridge.AndroidBridge
import com.wonderwall.app.data.AppDatabase
import com.wonderwall.app.data.CachedAnalysis
import com.wonderwall.app.ui.screens.MyChordsScreen
import com.wonderwall.app.ui.screens.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var webView: WonderwallWebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null

    // File picker (for audio upload)
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
            filePathCallback = null
            webView.evaluateJavascript(
                "window.__audioPicked?.(${toJson(uri)});", null
            )
        }
    }

    // Permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val db: AppDatabase get() = (application as WonderwallApp).database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)

        setContent {
            val navController = rememberNavController()
            var isLoading by remember { mutableStateOf(true) }
            var errorMsg by remember { mutableStateOf<String?>(null) }
            val currentRoute by navController.currentBackStackEntryAsState()
            val route = currentRoute?.destination?.route ?: "web"

            var isRecording by remember { mutableStateOf(false) }
            var recordingElapsed by remember { mutableStateOf(0L) }

            val bridge = remember {
                AndroidBridge(
                    app = application,
                    onPickAudio = ::pickAudio,
                    onStartRecording = {
                        if (isRecording) return@AndroidBridge
                        isRecording = true
                        recordingElapsed = 0
                        startCachingRecording()
                    },
                    onStopRecording = { uriJson ->
                        isRecording = false
                        recordingElapsed = 0
                        val uri = Uri.parse(uriJson)
                        webView.post {
                            webView.evaluateJavascript(
                                "window.__audioRecorded?.(${toJson(uri)});", null
                            )
                        }
                    },
                    onCacheAnalysis = { json -> cacheAnalysis(json) },
                    onShare = ::share,
                    onClearCache = { lifecycleScope.launch { db.analysisDao().deleteAll() } },
                )
            }

            // Recording timer
            if (isRecording) {
                LaunchedEffect(Unit) {
                    val startMs = SystemClock.elapsedRealtime()
                    while (true) {
                        recordingElapsed = SystemClock.elapsedRealtime() - startMs
                        delay(100)
                        if (!isRecording) break
                    }
                }
            }

            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = route == "web",
                            onClick = {
                                if (route != "web") navController.navigate("web") { popUpTo("web") { inclusive = true } }
                            },
                            icon = { Text("🏠", fontSize = 18.sp) },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = route == "my-chords",
                            onClick = { navController.navigate("my-chords") },
                            icon = { Text("♪", fontSize = 18.sp) },
                            label = { Text("My Chords") }
                        )
                        NavigationBarItem(
                            selected = route == "settings",
                            onClick = { navController.navigate("settings") },
                            icon = { Text("⚙", fontSize = 18.sp) },
                            label = { Text("Settings") }
                        )
                    }
                }
            ) { pad ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad)
                ) {
                    NavHost(navController, startDestination = "web") {
                        composable("web") {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AndroidView(
                                    factory = { ctx ->
                                        WonderwallWebView(ctx).also { wv ->
                                            webView = wv
                                            wv.addJavascriptInterface(bridge, "AndroidBridge")
                                            wv.onLoadingChanged = { loading ->
                                                isLoading = loading
                                            }
                                            wv.onError = { desc ->
                                                errorMsg = desc
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )

                                if (isLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Text(
                                                "Loading...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Recording overlay
                                if (isRecording) {
                                    RecordingOverlay(
                                        elapsedMs = recordingElapsed,
                                        onStop = { stopCachingRecording(bridge) }
                                    )
                                }

                                errorMsg?.let { msg ->
                                    AlertDialog(
                                        onDismissRequest = { errorMsg = null },
                                        title = { Text("Connection Error") },
                                        text = { Text("Could not load the page.\n\n$msg\n\nTap Retry to try again.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                errorMsg = null
                                                webView.loadLocalIndex()
                                            }) { Text("Retry") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { errorMsg = null }) { Text("Dismiss") }
                                        }
                                    )
                                }
                            }
                        }
                        composable("my-chords") {
                            MyChordsScreen(
                                onOpenAnalysis = { navController.popBackStack() },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onOpenTarget = {
                                    webView.loadTargetUrl()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // -- Recording overlay composable -----------------------------------------

    @Composable
    private fun RecordingOverlay(elapsedMs: Long, onStop: () -> Unit) {
        val sec = elapsedMs / 1000
        val min = sec / 60
        val timeStr = "%02d:%02d".format(min, sec % 60)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // semi-transparent backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000))
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(32.dp)
            ) {
                // Pulsing dot
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF5350))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎤", fontSize = 28.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Recording",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    timeStr,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("■  Stop", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    // -- Incoming share intents -----------------------------------------------

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                webView.post {
                    webView.evaluateJavascript(
                        "window.__audioPicked?.(${toJson(uri)});", null
                    )
                }
            }
        }
    }

    // -- Audio picker ---------------------------------------------------------

    private fun pickAudio() {
        audioPickerLauncher.launch("audio/*")
    }

    // -- Audio recording (cache-based, no SAF dialog) -------------------------

    private fun startCachingRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        val dir = File(cacheDir, "recordings").also { it.mkdirs() }
        val file = File(dir, "recording_${System.currentTimeMillis()}.mp4")
        recordingFile = file

        MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
            mediaRecorder = this
        }
    }

    private fun stopCachingRecording(bridge: AndroidBridge) {
        mediaRecorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaRecorder = null
        val file = recordingFile ?: return
        if (!file.exists()) return
        bridge.onStopRecording(Uri.fromFile(file).toString())
    }

    override fun onPause() {
        super.onPause()
        mediaRecorder?.apply {
            try { stop() } catch (_: Exception) {}
            release()
        }
        mediaRecorder = null
    }

    // -- Storage --------------------------------------------------------------

    private fun cacheAnalysis(json: JSONObject) {
        lifecycleScope.launch {
            db.analysisDao().upsert(
                CachedAnalysis(
                    videoId = json.optString("videoId", ""),
                    title = json.optString("title", "Unknown"),
                    artist = json.optString("artist", ""),
                    chordsJson = json.optString("chords", "[]"),
                    key = json.optString("key", "?"),
                    bpm = json.optDouble("bpm", 0.0).toFloat(),
                )
            )
        }
    }

    // -- Share ----------------------------------------------------------------

    private fun share(text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        startActivity(Intent.createChooser(intent, title))
    }

    // -- Helpers --------------------------------------------------------------

    private fun toJson(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val name = cursor?.use {
            val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && it.moveToFirst()) it.getString(nameIdx) else "audio"
        } ?: "audio"

        return JSONObject().apply {
            put("uri", uri.toString())
            put("name", name)
            put("mimeType", contentResolver.getType(uri) ?: "audio/*")
        }.toString()
    }
}
