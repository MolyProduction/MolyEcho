package com.module.notelycompose.transcription

import android.content.Context
import android.content.Intent
import com.module.notelycompose.service.TranscriptionForegroundService

class AndroidTranscriptionServiceController(
    private val context: Context
) : TranscriptionServiceController {

    override fun startTranscriptionService() {
        val intent = Intent(context, TranscriptionForegroundService::class.java).apply {
            action = TranscriptionForegroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    override fun stopTranscriptionService() {
        context.stopService(Intent(context, TranscriptionForegroundService::class.java))
    }
}
