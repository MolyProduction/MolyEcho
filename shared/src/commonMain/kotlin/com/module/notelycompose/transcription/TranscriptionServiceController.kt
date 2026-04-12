package com.module.notelycompose.transcription

interface TranscriptionServiceController {
    val isServiceActive: Boolean   // true zwischen startTranscriptionService() und stopTranscriptionService()
    fun startTranscriptionService()
    fun stopTranscriptionService()
    fun notifyTranscriptionPhaseLoading()
    fun notifyTranscriptionPhaseTranscribing()
    fun notifyTranscriptionComplete()
}
