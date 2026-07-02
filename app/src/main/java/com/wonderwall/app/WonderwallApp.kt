package com.wonderwall.app

import android.app.Application
import android.webkit.WebView
import com.wonderwall.app.data.AppDatabase

class WonderwallApp : Application() {
    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
