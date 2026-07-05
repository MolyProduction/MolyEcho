package com.module.notelycompose.transcription

data class TranscriptionUiState(
    val inTranscription: Boolean = false,
    val isModelLoading: Boolean = false,
    val viewOriginalText: Boolean = true,
    val finalText: String = "",
    val partialText: String = "",
    val summarizedText: String = "",
    val originalText: String = "",
    val progress: Int = 0,
    val downloaded: String = "0 MB ",
    val total: String = "0 MB",
    val hasError: Boolean = false,
    val showLongRunningHint: Boolean = false,
    val modelNotAvailable: Boolean = false,  // Modell nicht geladen → stumm zurücknavigieren
    // Anzahl der Audio-Abschnitte, die bei der Transkription fehlgeschlagen sind.
    // > 0 bedeutet: Transkript ist unvollständig — Hinweis im UI anzeigen.
    val failedChunks: Int = 0
)

sealed class TranscriptionEffect() {
     object DownloadEffect : TranscriptionEffect()
}