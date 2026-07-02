# Keep the JS bridge methods accessible from WebView
-keepclassmembers class com.wonderwall.app.bridge.AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
