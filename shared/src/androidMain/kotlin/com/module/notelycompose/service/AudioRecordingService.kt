package com.module.notelycompose.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.os.Build
import de.molyecho.notlyvoice.android.R
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import audio.recorder.AudioRecorder
import com.module.notelycompose.MainActivity
import com.module.notelycompose.Arguments.NOTE_ID_PARAM
import com.module.notelycompose.audio.domain.SaveAudioNoteInteractor
import com.module.notelycompose.extensions.restartMainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AudioRecordingService : Service() {
    private val audioRecorder by inject<AudioRecorder>()
    private val saveAudioNoteInteractor by inject<SaveAudioNoteInteractor>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var noteId: Long? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        coroutineScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Restarted after process kill with no pending intent — nothing to record, stop cleanly
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> {
                noteId = intent.extras?.getLong(NOTE_ID_PARAM)
                startRecording()
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_STOP_FROM_TILE -> handleTileStop()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "recording_channel",
            "Recording",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun startRecording() {
        if (audioRecorder.hasRecordingPermission()) {
            audioRecorder.startRecording()
        }
    }

    private fun pauseRecording() {
        audioRecorder.pauseRecording()
    }

    private fun resumeRecording() {
        audioRecorder.resumeRecording()
    }

    private fun stopRecording() {
        audioRecorder.stopRecording()
        stopSelf()
    }

    private fun handleTileStop() {
        audioRecorder.stopRecording()
        coroutineScope.launch {
            saveAudioNoteInteractor.save(noteId)
            noteId = null
            stopSelf()
            this@AudioRecordingService.restartMainActivity()
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, "recording_channel")
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        var isRunning = false
        const val ACTION_START = "START_RECORDING"
        const val ACTION_PAUSE = "PAUSE_RECORDING"
        const val ACTION_RESUME = "RESUME_RECORDING"
        const val ACTION_STOP = "STOP_RECORDING"
        const val ACTION_STOP_FROM_TILE = "STOP_RECORDING_FROM_TILE"
    }
}
