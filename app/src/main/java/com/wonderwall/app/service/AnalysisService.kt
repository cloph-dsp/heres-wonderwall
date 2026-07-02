package com.wonderwall.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.wonderwall.app.MainActivity

class AnalysisService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Analyzing song"
        val progress = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Here's Wonderwall")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .apply {
                if (progress >= 0) {
                    setProgress(100, progress, false)
                } else {
                    setProgress(0, 0, true) // indeterminate
                }
            }
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Analysis Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "analysis_progress"
        const val NOTIFICATION_ID = 1
        const val EXTRA_TITLE = "title"
        const val EXTRA_PROGRESS = "progress"

        fun start(ctx: Context, title: String, progress: Int = -1) {
            val intent = Intent(ctx, AnalysisService::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress)
            }
            ctx.startForegroundService(intent)
        }

        fun updateProgress(ctx: Context, progress: Int, title: String) {
            start(ctx, title, progress)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, AnalysisService::class.java))
        }
    }
}
