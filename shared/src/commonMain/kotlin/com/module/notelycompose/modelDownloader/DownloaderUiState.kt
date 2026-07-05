package com.module.notelycompose.modelDownloader

/**
 * Lifecycle of the current download as VM-held state. Screens that render the download
 * inline (onboarding) derive their UI from this instead of keeping a local copy — a local
 * copy resets on recomposition/back-navigation while the download keeps running.
 */
enum class DownloadStatus { IDLE, DOWNLOADING, SUCCESS, ERROR }

data class DownloaderUiState(
    val selectedModel: TranscriptionModel,
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,
    val downloaded: String = "0 MB ",
    val total: String = "0 MB",
    /** True if the active network is metered — screens show a mobile-data warning. */
    val isMeteredNetwork: Boolean = false
)

sealed class DownloaderEffect() {
    class DownloadEffect : DownloaderEffect()
    class ModelsAreReady : DownloaderEffect()
    class AskForUserAcceptance : DownloaderEffect()
    class ErrorEffect : DownloaderEffect()
    class CheckingEffect : DownloaderEffect()

    /** User cancelled the download — not an error, dialogs should simply close. */
    class DownloadCancelled : DownloaderEffect()
}
