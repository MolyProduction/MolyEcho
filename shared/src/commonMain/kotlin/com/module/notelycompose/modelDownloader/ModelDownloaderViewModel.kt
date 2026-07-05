package com.module.notelycompose.modelDownloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.module.notelycompose.platform.Downloader
import com.module.notelycompose.platform.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class ModelDownloaderViewModel(
    private val downloader: Downloader,
    private val transcriber: Transcriber,
    private val modelSelection: ModelSelection
):ViewModel(){
    private var _uiState: MutableStateFlow<DownloaderUiState> = MutableStateFlow(DownloaderUiState(modelSelection.getDefaultTranscriptionModel()))

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val selectedModel = modelSelection.getSelectedModel()
            _uiState.update { it.copy(selectedModel = selectedModel) }
        }
    }
    val uiState: StateFlow<DownloaderUiState> = _uiState

    private val _effects = MutableSharedFlow<DownloaderEffect>()
    val effects: SharedFlow<DownloaderEffect> = _effects


    fun checkTranscriptionAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            _effects.emit(DownloaderEffect.CheckingEffect())

            // A download may already be in flight (e.g. started from onboarding or settings).
            // Attach to it as a pure observer — never start a second transfer. The loop covers
            // multi-file models: after one file finishes, the owning coroutine starts the next
            // file a moment later, so keep observing until no download is running anymore and
            // then decide based on what is actually on disk.
            var attached = false
            while (downloader.hasRunningDownload()) {
                if (!attached) {
                    attached = true
                    _uiState.update { it.copy(status = DownloadStatus.DOWNLOADING) }
                    _effects.emit(DownloaderEffect.DownloadEffect())
                }
                var cancelled = false
                var failed = false
                downloader.trackDownloadProgress(
                    uiState.value.selectedModel.name,
                    onProgressUpdated = { progress, downloadedMB, totalMB ->
                        _uiState.update { current ->
                            current.copy(progress = progress.toFloat(), downloaded = downloadedMB, total = totalMB)
                        }
                    },
                    onSuccess = { /* one file finished — re-check below whether more follow */ },
                    onCancelled = { cancelled = true },
                    onFailed = { failed = true }
                )
                if (cancelled) {
                    _uiState.update { it.copy(status = DownloadStatus.IDLE, progress = 0f) }
                    _effects.emit(DownloaderEffect.DownloadCancelled())
                    return@launch
                }
                if (failed) {
                    _uiState.update { it.copy(status = DownloadStatus.ERROR) }
                    _effects.emit(DownloaderEffect.ErrorEffect())
                    return@launch
                }
                // Bridge the gap in which a multi-file sequence starts its next file.
                delay(NEXT_FILE_GRACE_MS)
            }

            // Read the current model selection fresh from DataStore to avoid using
            // a stale default from the ViewModel's initial state before init completes.
            val currentModel = modelSelection.getSelectedModel()
            if (!transcriber.doesModelExists(currentModel.name)) {
                if (!currentModel.isDownloadRequired) {
                    // Bundled model (no URL) — treat as always available, no download needed
                    _effects.emit(DownloaderEffect.ModelsAreReady())
                } else {
                    // Update uiState so the download dialog shows the correct model info
                    // and an up-to-date mobile-data warning.
                    _uiState.update {
                        it.copy(selectedModel = currentModel, isMeteredNetwork = downloader.isNetworkMetered())
                    }
                    _effects.emit(DownloaderEffect.AskForUserAcceptance())
                }
            } else {
                if (attached) {
                    _uiState.update { it.copy(status = DownloadStatus.SUCCESS, progress = 100f) }
                }
                _effects.emit(DownloaderEffect.ModelsAreReady())
            }
        }
    }

    fun startDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            if (downloader.hasRunningDownload()) {
                trackDownload()
                return@launch
            }
            val model = uiState.value.selectedModel
            if (model.downloadFiles != null) {
                startMultiFileDownload(model)
            } else {
                val modelUrl = model.url ?: run {
                    _effects.emit(DownloaderEffect.ErrorEffect())
                    return@launch
                }
                downloader.startDownload(modelUrl, model.name)
                trackDownload()
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            downloader.cancelDownload()
        }
    }

    /**
     * Re-reads whether the active network is metered. Screens call this before showing
     * a download decision so the mobile-data warning reflects the current connection.
     */
    fun refreshNetworkStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val metered = downloader.isNetworkMetered()
            _uiState.update { it.copy(isMeteredNetwork = metered) }
        }
    }

    fun downloadModelForSettings(model: TranscriptionModel) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = DownloaderUiState(model)
            if (model.downloadFiles != null) {
                startMultiFileDownload(model)
            } else {
                val modelUrl = model.url ?: run {
                    _effects.emit(DownloaderEffect.ModelsAreReady())
                    return@launch
                }
                downloader.startDownload(modelUrl, model.name)
                trackDownload()
            }
        }
    }

    private suspend fun trackDownload() {
        _uiState.update { it.copy(status = DownloadStatus.DOWNLOADING) }
        _effects.emit(DownloaderEffect.DownloadEffect())
        downloader.trackDownloadProgress(
            uiState.value.selectedModel.name,
            onProgressUpdated = { progress, downloadedMB, totalMB ->
                _uiState.update { current ->
                    current.copy(
                        progress = progress.toFloat(),
                        downloaded = downloadedMB,
                        total = totalMB
                    )
                }
            },
            onSuccess = {
                downloader.notifyDownloadComplete()
                _uiState.update { it.copy(status = DownloadStatus.SUCCESS, progress = 100f) }
                viewModelScope.launch { _effects.emit(DownloaderEffect.ModelsAreReady()) }
            },
            onCancelled = {
                _uiState.update { it.copy(status = DownloadStatus.IDLE, progress = 0f) }
                viewModelScope.launch { _effects.emit(DownloaderEffect.DownloadCancelled()) }
            },
            onFailed = {
                _uiState.update { it.copy(status = DownloadStatus.ERROR) }
                viewModelScope.launch { _effects.emit(DownloaderEffect.ErrorEffect()) }
            }
        )
    }

    private suspend fun startMultiFileDownload(model: TranscriptionModel) {
        val files = model.downloadFiles ?: return
        val totalBytes = files.sumOf { it.sizeBytes }
        _uiState.update { it.copy(status = DownloadStatus.DOWNLOADING) }
        _effects.emit(DownloaderEffect.DownloadEffect())
        var failed = false
        var cancelled = false
        var completedBytes = 0L

        for (file in files) {
            // Store each file in a subdirectory named after the model. Files that already
            // exist from a previous (partially failed) run are skipped by the Downloader.
            val destPath = "${model.name}/${file.fileName}"

            downloader.startDownload(file.url, destPath)

            downloader.trackDownloadProgress(
                destPath,
                onProgressUpdated = { progress, _, _ ->
                    // Byte-weighted aggregate progress: files differ heavily in size
                    // (encoder ~643 MB, decoder ~344 MB, tokens ~1 MB).
                    val currentBytes = completedBytes + file.sizeBytes * progress / 100
                    val aggregateProgress = (currentBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
                    _uiState.update { current ->
                        current.copy(
                            progress = aggregateProgress.toFloat(),
                            downloaded = formatWholeMb(currentBytes),
                            total = formatWholeMb(totalBytes)
                        )
                    }
                },
                onSuccess = { /* download of this file succeeded, loop continues */ },
                onCancelled = { cancelled = true },
                onFailed = { failed = true }
            )

            if (failed || cancelled) break
            completedBytes += file.sizeBytes
        }

        when {
            cancelled -> {
                _uiState.update { it.copy(status = DownloadStatus.IDLE, progress = 0f) }
                _effects.emit(DownloaderEffect.DownloadCancelled())
            }
            failed -> {
                _uiState.update { it.copy(status = DownloadStatus.ERROR) }
                _effects.emit(DownloaderEffect.ErrorEffect())
            }
            else -> {
                downloader.notifyDownloadComplete()
                _uiState.update { it.copy(status = DownloadStatus.SUCCESS, progress = 100f) }
                _effects.emit(DownloaderEffect.ModelsAreReady())
            }
        }
    }

    private fun formatWholeMb(bytes: Long): String = "${bytes / 1024 / 1024} MB"

    companion object {
        /** Gap in which a multi-file sequence is expected to start its next file. */
        private const val NEXT_FILE_GRACE_MS = 500L
    }
}
