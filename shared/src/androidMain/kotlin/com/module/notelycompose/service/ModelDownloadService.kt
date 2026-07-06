package com.module.notelycompose.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.module.notelycompose.MainActivity
import de.molyecho.notlyvoice.android.R

/**
 * Foreground service (dataSync) that keeps the process alive while the Downloader streams
 * a model file. Without it, the cached-app freezer / Doze stops the process a few minutes
 * after the app goes to background and the 1–1.6 GB download stalls.
 *
 * The service itself performs no work — the transfer runs on the Downloader's own scope.
 * Progress arrives as intents (throttled by the Downloader) and is rendered into the
 * foreground notification via startForeground(), which is the reliable update path for
 * FGS notifications (NotificationManager.notify() is ignored on many OEM skins while the
 * app is in background).
 */
class ModelDownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(pct = -1, text = null))
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MolyEcho:modelDownload"
        )
        @Suppress("WakelockTimeout")
        wakeLock?.acquire(WAKELOCK_TIMEOUT_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground(NOTIFICATION_ID, buildNotification(pct = -1, text = null))
            ACTION_PROGRESS -> {
                val pct = intent.getIntExtra(EXTRA_PCT, -1)
                val text = intent.getStringExtra(EXTRA_TEXT)
                startForeground(NOTIFICATION_ID, buildNotification(pct, text))
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(pct: Int, text: String?): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_download_title))
            .setContentText(text ?: getString(R.string.notification_download_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (pct in 0..100) {
            builder.setProgress(100, pct, false)
        } else {
            builder.setProgress(100, 0, true)
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_download_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "START_MODEL_DOWNLOAD"
        private const val ACTION_PROGRESS = "MODEL_DOWNLOAD_PROGRESS"
        private const val ACTION_STOP = "STOP_MODEL_DOWNLOAD"
        private const val EXTRA_PCT = "pct"
        private const val EXTRA_TEXT = "text"
        private const val CHANNEL_ID = "model_download_channel"
        private const val NOTIFICATION_ID = 5
        // Generous cap: 1.6 GB on a slow connection can take well over an hour.
        private const val WAKELOCK_TIMEOUT_MS = 3 * 60 * 60 * 1000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ModelDownloadService::class.java).apply { action = ACTION_START }
            )
        }

        fun progress(context: Context, pct: Int, text: String) {
            context.startService(
                Intent(context, ModelDownloadService::class.java).apply {
                    action = ACTION_PROGRESS
                    putExtra(EXTRA_PCT, pct)
                    putExtra(EXTRA_TEXT, text)
                }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ModelDownloadService::class.java).apply { action = ACTION_STOP }
            )
        }
    }
}
