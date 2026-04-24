package com.nullxoid.android.backend

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.nullxoid.android.backend.engine.EchoEngine
import com.nullxoid.android.backend.engine.LlmEngine
import com.nullxoid.android.backend.engine.OllamaEngine
import com.nullxoid.android.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class BackendService : Service() {
    private var server: EmbeddedServer? = null
    private var started = false
    private lateinit var settingsStore: SettingsStore

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        settingsStore = SettingsStore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!started) {
            server = EmbeddedServer(port = DEFAULT_PORT, engine = buildEngine()).also { it.start() }
            started = true
        }
        return START_STICKY
    }

    private fun buildEngine(): LlmEngine = runBlocking {
        when (settingsStore.embeddedEngine.first()) {
            SettingsStore.EMBEDDED_ENGINE_OLLAMA -> OllamaEngine(
                baseUrl = settingsStore.ollamaUrl.first(),
                model = settingsStore.ollamaModel.first()
            )
            else -> EchoEngine()
        }
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
