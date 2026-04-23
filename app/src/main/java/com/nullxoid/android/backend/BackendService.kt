package com.nullxoid.android.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

class BackendService : Service() {
    private var server: EmbeddedServer? = null
    private var started = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        server = EmbeddedServer(port = DEFAULT_PORT)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!started) {
            server?.start()
            started = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        started = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NullXoid Embedded Backend",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the on-device NullXoid backend available."
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("NullXoid local backend")
            .setContentText("Embedded backend running on 127.0.0.1:$DEFAULT_PORT")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "nullxoid_embedded_backend"
        private const val NOTIFICATION_ID = 1001
        private const val DEFAULT_PORT = 8090
    }
}
