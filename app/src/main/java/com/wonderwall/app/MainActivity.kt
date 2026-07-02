package com.wonderwall.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.ValueCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var webView: WonderwallWebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var mediaRecorder: MediaRecorder? = null

    // File picker (for audio upload)
    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            filePathCallback?.onReceiveValue(arrayOf(uri))
            filePathCallback = null
            // Notify web JS
            webView.evaluateJavascript(
                "window.__audioPicked?.(${toJson(uri)});", null
            )
        }
    }

    // Audio recording
    private val recordLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("audio/mp4")
    ) { uri: Uri? ->
        if (uri != null) startRecording(uri)
    }

    // Permissions
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> /* retry the pending action */ }

    private val db: AppDatabase get() = (application as WonderwallApp).database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming audio share intents
        handleIncomingIntent(intent)

        setContent {
            val navController = rememberNavController()
            var isLoading by remember { mutableStateOf(true) }
            var errorMsg by remember { mutableStateOf<String?>(null) }
            val currentRoute by navController.currentBackStackEntryAsState()
            val isHome = currentRoute?.destination?.route == "web"

            val bridge = remember {
                AndroidBridge(
                    app = application,
                    onPickAudio = ::pickAudio,
                    onRecordAudio = ::requestRecord,
                    onCacheAnalysis = { json -> cacheAnalysis(json) },
                    onShare = ::share,
                    onClearCache = { lifecycleScope.launch { db.analysisDao().deleteAll() } },
                )
            }

            Scaffold(
                bottomBar = {
                    if (isHome) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = true,
                                onClick = { },
                                icon = { Text("🏠") },
                                label = { Text("Home") }
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { navController.navigate("my-chords") },
                                icon = { Text("♪") },
                                label = { Text("My Chords") }
                            )
                            NavigationBarItem(
                                selected = false,
                                onClick = { navController.navigate("settings") },
                                icon = { Text("⚙") },
                                label = { Text("Settings") }
                            )
                        }
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
                                onOpenAnalysis = { videoId ->
                                    navController.popBackStack()
                                },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    // -- Incoming share intents -----------------------------------------------

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { uri ->
                // Auto-upload: pass the URI to the web app
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

    // -- Audio recording ------------------------------------------------------

    private fun requestRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        val filename = "recording_${System.currentTimeMillis()}.mp4"
        recordLauncher.launch(filename)
    }

    private fun startRecording(uri: Uri) {
        val resolver = contentResolver
        val fd = resolver.openFileDescriptor(uri, "w") ?: return
        MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(fd.fileDescriptor)
            prepare()
            start()
            mediaRecorder = this
        }
    }

    override fun onPause() {
        super.onPause()
        mediaRecorder?.apply {
            stop()
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
