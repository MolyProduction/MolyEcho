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
import com.module.notelycompose.MainActivity
import de.molyecho.notlyvoice.android.R

class TranscriptionForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(loading = true))
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MolyEcho:transcription"
        )
        @Suppress("WakelockTimeout")
        wakeLock?.acquire(45 * 60 * 1000L) // max 45 minutes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            // Explicitly (re-)establish the foreground notification as "loading" on every
            // new transcription start. This covers the case where the service is still alive
            // from a previous session (onCreate was not called again) and the notification
            // would otherwise stay in an outdated state.
            ACTION_START -> updateNotification(loading = true)
            ACTION_PHASE_LOADING -> updateNotification(loading = true)
            ACTION_PHASE_TRANSCRIBING -> updateNotification(loading = false)
            ACTION_COMPLETE -> {
                postCompletionNotification()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        wakeLock = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification(loading: Boolean) {
        // Use startForeground() to update the notification — NOT NotificationManager.notify().
        // nm.notify() is silently ignored for foreground service notifications on many Android
        // versions and OEM skins when the app is in background. startForeground() is the
        // correct and reliable way to update a foreground service's notification.
        startForeground(NOTIFICATION_ID, buildNotification(loading))
    }

    private fun buildNotification(loading: Boolean): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (loading)
            getString(R.string.notification_model_loading_title)
        else
            getString(R.string.notification_transcription_title)
        val text = if (loading)
            getString(R.string.notification_model_loading_text)
        else
            getString(R.string.notification_transcription_text)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun postCompletionNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        createDoneNotificationChannel(nm)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE_ID)
            .setContentTitle(getString(R.string.notification_transcription_done_title))
            .setContentText(getString(R.string.notification_transcription_done_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_DONE_ID, notification)
    }

    private fun createDoneNotificationChannel(nm: NotificationManager) {
        val channel = NotificationChannel(
            CHANNEL_DONE_ID,
            getString(R.string.notification_transcription_done_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transcription",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "START_TRANSCRIPTION"
        const val ACTION_PHASE_LOADING = "PHASE_LOADING"
        const val ACTION_PHASE_TRANSCRIBING = "PHASE_TRANSCRIBING"
        const val ACTION_COMPLETE = "ACTION_COMPLETE"
        private const val CHANNEL_ID = "transcription_channel"
        private const val CHANNEL_DONE_ID = "transcription_done_channel"
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_DONE_ID = 3
    }
}
