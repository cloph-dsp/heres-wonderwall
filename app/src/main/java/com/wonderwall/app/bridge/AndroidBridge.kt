package com.wonderwall.app.bridge

import android.app.Application
import android.webkit.JavascriptInterface
import com.wonderwall.app.service.AnalysisService
import org.json.JSONObject

/**
 * JS bridge exposed as `window.AndroidBridge` inside the WebView.
 *
 * The web frontend calls these methods when running inside the app.
 * Every method falls back gracefully when called from a regular browser.
 */
class AndroidBridge(
    private val app: Application,
    private val onPickAudio: () -> Unit,
    private val onRecordAudio: () -> Unit,
    private val onCacheAnalysis: (JSONObject) -> Unit,
    private val onShare: (String, String) -> Unit,
    private val onClearCache: () -> Unit = {},
) {
    // -- Audio ----------------------------------------------------------------

    @JavascriptInterface
    fun pickAudio() {
        // Triggers SAF file picker from the Activity
        onPickAudio()
    }

    @JavascriptInterface
    fun recordAudio(maxDurationSec: Int) {
        // Triggers MediaRecorder from the Activity
        onRecordAudio()
    }

    // -- Storage --------------------------------------------------------------

    @JavascriptInterface
    fun saveAnalysis(json: String): Boolean {
        return try {
            onCacheAnalysis(JSONObject(json))
            true
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun isOnline(): Boolean {
        // Let the web layer handle connectivity — this just reflects it
        return true
    }

    // -- Share ----------------------------------------------------------------

    @JavascriptInterface
    fun shareText(text: String, title: String) {
        onShare(text, title)
    }

    @JavascriptInterface
    fun shareImage(base64Png: String, title: String) {
        onShare(base64Png, title)
    }

    @JavascriptInterface
    fun clearAllAnalyses(): Boolean {
        return try {
            onClearCache()
            true
        } catch (_: Exception) {
            false
        }
    }

    // -- Notifications --------------------------------------------------------

    @JavascriptInterface
    fun showAnalysisProgress(progress: Int) {
        AnalysisService.updateProgress(app, progress, "Analyzing...")
    }

    @JavascriptInterface
    fun showAnalysisComplete(videoId: String) {
        AnalysisService.stop(app)
    }

    @JavascriptInterface
    fun cancelNotification() {
        AnalysisService.stop(app)
    }
}
