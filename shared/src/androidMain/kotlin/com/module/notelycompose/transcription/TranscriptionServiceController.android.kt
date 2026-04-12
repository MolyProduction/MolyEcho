package com.module.notelycompose.transcription

import android.content.Context
import android.content.Intent
import com.module.notelycompose.service.TranscriptionForegroundService

class AndroidTranscriptionServiceController(
    private val context: Context
) : TranscriptionServiceController {

    @Volatile
    override var isServiceActive: Boolean = false
        private set

    override fun startTranscriptionService() {
        isServiceActive = true
        val intent = Intent(context, TranscriptionForegroundService::class.java).apply {
            action = TranscriptionForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    override fun stopTranscriptionService() {
        isServiceActive = false
        context.stopService(Intent(context, TranscriptionForegroundService::class.java))
    }

    override fun notifyTranscriptionPhaseLoading() {
        if (!isServiceActive) return
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_PHASE_LOADING
            }
        )
    }

    override fun notifyTranscriptionPhaseTranscribing() {
        if (!isServiceActive) return
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_PHASE_TRANSCRIBING
            }
        )
    }

    override fun notifyTranscriptionComplete() {
        if (!isServiceActive) return
        isServiceActive = false
        context.startService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_COMPLETE
            }
        )
    }
}
