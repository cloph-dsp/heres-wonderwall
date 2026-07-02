package com.wonderwall.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

@SuppressLint("SetJavaScriptEnabled")
class WonderwallWebView(context: Context) : WebView(context) {

    companion object {
        // Default URL — user can set their own in settings
        var targetUrl: String = "https://chordmini.me"
    }

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = settings.userAgentString + " Wonderwall/1.0"
        }

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Could show loading indicator here
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectBridgeScript()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean = false
        }

        webChromeClient = WebChromeClient() // handles progress, permissions, etc.

        loadUrl(targetUrl)
    }

    /**
     * Injects a tiny JS shim so the web app can detect it's inside the Android wrapper.
     * The real bridge methods come from [AndroidBridge] via @JavascriptInterface.
     */
    private fun injectBridgeScript() {
        evaluateJavascript("""
            (function() {
                if (window.AndroidBridge || window.__wonderwall) return;
                window.__wonderwall = { isApp: true, platform: 'android' };
            })();
        """.trimIndent(), null)
    }
}
