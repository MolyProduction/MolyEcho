package com.module.notelycompose.transcription

import android.content.Context
import android.content.Intent
import com.module.notelycompose.dictation.DictationFailure
import com.module.notelycompose.dictation.DictationResultStore
import com.module.notelycompose.dictation.copyToClipboard
import com.module.notelycompose.service.TranscriptionForegroundService

class AndroidTranscriptionServiceController(
    private val context: Context,
    private val resultStore: DictationResultStore
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

    override fun notifyDictationResult(text: String, autoCopy: Boolean, notify: Boolean) {
        // Ablegen und Kopieren unabhängig vom Service-Zustand: der Text darf nie verloren gehen.
        val stored = runCatching { resultStore.write(text) }.isSuccess
        // Scheitert das Kopieren (OEM-Sperre), bleibt es still; die Benachrichtigung bietet es erneut an.
        val copied = autoCopy && copyToClipboard(context, text)
        if (!isServiceActive) return
        isServiceActive = false
        sendToService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_COMPLETE
                putExtra(TranscriptionForegroundService.EXTRA_DICTATION, true)
                putExtra(TranscriptionForegroundService.EXTRA_NOTIFY, notify && stored)
                putExtra(TranscriptionForegroundService.EXTRA_COPIED, copied)
            }
        )
    }

    override fun notifyDictationFailed(reason: DictationFailure) {
        if (!isServiceActive) return
        isServiceActive = false
        sendToService(
            Intent(context, TranscriptionForegroundService::class.java).apply {
                action = TranscriptionForegroundService.ACTION_COMPLETE
                putExtra(TranscriptionForegroundService.EXTRA_DICTATION, true)
                putExtra(TranscriptionForegroundService.EXTRA_FAILURE, reason.name)
            }
        )
    }

    private fun sendToService(intent: Intent) {
        try {
            context.startService(intent)
        } catch (e: Exception) {
            // Hintergrund-Startbeschränkung o.ä.: Service wegen Fehler nicht hängen lassen.
            context.stopService(Intent(context, TranscriptionForegroundService::class.java))
        }
    }
}
