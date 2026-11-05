package com.module.notelycompose.transcription

import com.module.notelycompose.dictation.DictationFailure

interface TranscriptionServiceController {
    val isServiceActive: Boolean   // true zwischen startTranscriptionService() und stopTranscriptionService()
    fun startTranscriptionService()
    fun stopTranscriptionService()
    fun notifyTranscriptionPhaseLoading()
    fun notifyTranscriptionPhaseTranscribing()
    fun notifyTranscriptionComplete()

    /**
     * MolyDiktat: Ergebnis ablegen, optional in die Zwischenablage kopieren und den Service
     * beenden. [notify] = true zeigt „Text bereit" mit Kopier-Aktion, sonst die
     * Standard-Fertig-Benachrichtigung.
     */
    fun notifyDictationResult(text: String, autoCopy: Boolean, notify: Boolean)

    /** MolyDiktat: Kein Ergebnis - Hinweis anzeigen und den Service beenden. */
    fun notifyDictationFailed(reason: DictationFailure)
}
