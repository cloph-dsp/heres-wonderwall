package com.wonderwall.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.wonderwall.app.bridge.AndroidBridge
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle incoming audio share intents
        handleIncomingIntent(intent)

        setContent {
            val bridge = remember {
                AndroidBridge(
                    app = application,
                    onPickAudio = ::pickAudio,
                    onRecordAudio = ::requestRecord,
                    onCacheAnalysis = ::cacheAnalysis,
                    onShare = ::share,
                )
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { pad ->
                AndroidView(
                    factory = { ctx ->
                        WonderwallWebView(ctx).also { wv ->
                            webView = wv
                            wv.addJavascriptInterface(bridge, "AndroidBridge")
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(pad),
                    update = { /* WebView chameleon — no updates needed */ }
                )
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
            recordingFile = File(cacheDir, "recording_temp.mp4")
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
        // TODO: store to Room DB
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
