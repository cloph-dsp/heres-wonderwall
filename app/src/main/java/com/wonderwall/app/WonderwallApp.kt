package com.wonderwall.app

package com.wonderwall.app

import android.app.Application
import android.webkit.WebView

class WonderwallApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }
}
