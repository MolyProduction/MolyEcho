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
import com.module.notelycompose.dictation.DictationCopyReceiver
import com.module.notelycompose.dictation.DictationFailure
import com.module.notelycompose.dictation.DictationIntents
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
                val failure = intent.getStringExtra(EXTRA_FAILURE)
                when {
                    // MolyDiktat: Fehlerfälle werden immer gemeldet, Erfolg nur mit Option C.
                    failure != null -> postDictationFailureNotification(failure)
                    intent.getBooleanExtra(EXTRA_DICTATION, false) &&
                        intent.getBooleanExtra(EXTRA_NOTIFY, false) ->
                        postDictationReadyNotification(intent.getBooleanExtra(EXTRA_COPIED, false))
                    else -> postCompletionNotification()
                }
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

    private fun openAppPendingIntent(requestCode: Int): PendingIntent = PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** „Text bereit": Tipp kopiert den Text (Broadcast, keine Activity - Trampolin-Sperre). */
    private fun postDictationReadyNotification(alreadyCopied: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        createDictationChannel(nm)
        val copyIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_DICTATION_COPY,
            Intent(this, DictationCopyReceiver::class.java).apply {
                action = DictationIntents.ACTION_COPY_RESULT
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = getString(
            if (alreadyCopied) R.string.notification_dictation_ready_text_copied
            else R.string.notification_dictation_ready_text_tap
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DICTATION_ID)
            .setContentTitle(getString(R.string.notification_dictation_ready_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(copyIntent)
            .addAction(
                0,
                getString(R.string.notification_dictation_action_open),
                openAppPendingIntent(REQUEST_DICTATION_OPEN)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_DICTATION_ID, notification)
    }

    private fun postDictationFailureNotification(reason: String) {
        val nm = getSystemService(NotificationManager::class.java)
        createDictationChannel(nm)
        val text = getString(
            when (runCatching { DictationFailure.valueOf(reason) }.getOrDefault(DictationFailure.FAILED)) {
                DictationFailure.NO_TEXT -> R.string.notification_dictation_failed_no_text
                DictationFailure.MODEL_MISSING -> R.string.notification_dictation_failed_model
                DictationFailure.FAILED -> R.string.notification_dictation_failed_generic
            }
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DICTATION_ID)
            .setContentTitle(getString(R.string.notification_dictation_failed_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent(REQUEST_DICTATION_OPEN))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_DICTATION_ID, notification)
    }

    private fun createDictationChannel(nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DICTATION_ID,
                getString(R.string.notification_dictation_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            )
        )
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
            getString(R.string.notification_transcription_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "START_TRANSCRIPTION"
        const val ACTION_PHASE_LOADING = "PHASE_LOADING"
        const val ACTION_PHASE_TRANSCRIBING = "PHASE_TRANSCRIBING"
        const val ACTION_COMPLETE = "ACTION_COMPLETE"
        // MolyDiktat: Zusatzdaten für ACTION_COMPLETE
        const val EXTRA_DICTATION = "extra_dictation"
        const val EXTRA_NOTIFY = "extra_dictation_notify"
        const val EXTRA_COPIED = "extra_dictation_copied"
        const val EXTRA_FAILURE = "extra_dictation_failure"
        const val NOTIFICATION_DICTATION_ID = 4
        private const val CHANNEL_DICTATION_ID = "dictation_ready_channel"
        private const val REQUEST_DICTATION_COPY = 10
        private const val REQUEST_DICTATION_OPEN = 11
        private const val CHANNEL_ID = "transcription_channel"
        private const val CHANNEL_DONE_ID = "transcription_done_channel"
        private const val NOTIFICATION_ID = 2
        private const val NOTIFICATION_DONE_ID = 3
    }
}
