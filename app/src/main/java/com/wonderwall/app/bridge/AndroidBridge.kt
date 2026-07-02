package com.wonderwall.app.bridge

import android.app.Application
import android.webkit.JavascriptInterface
import com.wonderwall.app.service.AnalysisService
import org.json.JSONObject

class AndroidBridge(
    private val app: Application,
    private val onPickAudio: () -> Unit,
    private val onStartRecording: () -> Unit,
    val onStopRecording: (String) -> Unit,
    private val onCacheAnalysis: (JSONObject) -> Unit,
    private val onShare: (String, String) -> Unit,
    private val onClearCache: () -> Unit = {},
) {
    @JavascriptInterface
    fun pickAudio() {
        onPickAudio()
    }

    @JavascriptInterface
    fun startRecording() {
        onStartRecording()
    }

    @JavascriptInterface
    fun stopRecording() {
        onStopRecording("")
    }

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
        return true
    }

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
