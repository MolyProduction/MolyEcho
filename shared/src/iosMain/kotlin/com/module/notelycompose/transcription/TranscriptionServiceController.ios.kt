package com.module.notelycompose.transcription

class IosTranscriptionServiceController : TranscriptionServiceController {
    override fun startTranscriptionService() {
        // No-op: iOS does not have Android background service limits
    }

    override fun stopTranscriptionService() {
        // No-op
    }
}
