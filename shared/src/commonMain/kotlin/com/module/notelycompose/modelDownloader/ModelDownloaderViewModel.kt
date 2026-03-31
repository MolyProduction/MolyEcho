package com.module.notelycompose.modelDownloader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.Downloader
import com.module.notelycompose.platform.Transcriber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
            _uiState.value = DownloaderUiState(selectedModel)
        }
    }
    val uiState: StateFlow<DownloaderUiState> = _uiState

    private val _effects = MutableSharedFlow<DownloaderEffect>()
    val effects: SharedFlow<DownloaderEffect> = _effects


    fun checkTranscriptionAvailability() {
        viewModelScope.launch(Dispatchers.IO) {
            _effects.emit(DownloaderEffect.CheckingEffect())

            if (downloader.hasRunningDownload()) {
                trackDownload()
            } else {
                // Read the current model selection fresh from DataStore to avoid using
                // a stale default from the ViewModel's initial state before init completes.
                val currentModel = modelSelection.getSelectedModel()
                if (!transcriber.doesModelExists(currentModel.name)) {
                    if (!currentModel.isDownloadRequired) {
                        // Bundled model (no URL) — treat as always available, no download needed
                        _effects.emit(DownloaderEffect.ModelsAreReady())
                    } else {
                        // Update uiState so the download dialog shows the correct model info.
                        _uiState.value = DownloaderUiState(currentModel)
                        _effects.emit(DownloaderEffect.AskForUserAcceptance())
                    }
                } else {
                    _effects.emit(DownloaderEffect.ModelsAreReady())
                }
            }
        }
    }

    fun startDownload() {
        viewModelScope.launch(Dispatchers.IO) {
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
        }, onSuccess = {
            viewModelScope.launch {
                _effects.emit(DownloaderEffect.ModelsAreReady())
            }

        }, onFailed = {
            viewModelScope.launch { _effects.emit(DownloaderEffect.ErrorEffect()) }
        })
    }

    private suspend fun startMultiFileDownload(model: TranscriptionModel) {
        // KNOWN LIMITATION: If the app is killed between files, hasRunningDownload() may detect
        // the last file's download ID and call trackDownload() with the top-level model name,
        // which will complete for that one file and emit ModelsAreReady with an incomplete directory.
        // doesModelExists() will return false on next launch and re-trigger the full download.
        // Full resume support would require persisting per-file progress state.
        val files = model.downloadFiles ?: return
        val totalFiles = files.size
        _effects.emit(DownloaderEffect.DownloadEffect())
        var success = true

        for ((index, fileEntry) in files.withIndex()) {
            if (!success) break
            val (fileName, url) = fileEntry
            // Store each file in a subdirectory named after the model
            val destPath = "${model.name}/$fileName"

            downloader.startDownload(url, destPath)

            downloader.trackDownloadProgress(
                destPath,
                onProgressUpdated = { progress, downloadedMB, totalMB ->
                    val aggregateProgress = ((index * 100 + progress) / totalFiles).coerceIn(0, 100)
                    _uiState.update { current ->
                        current.copy(
                            progress = aggregateProgress.toFloat(),
                            downloaded = downloadedMB,
                            total = totalMB
                        )
                    }
                },
                onSuccess = { /* download of this file succeeded, loop continues */ },
                onFailed = { _ ->
                    success = false
                    viewModelScope.launch { _effects.emit(DownloaderEffect.ErrorEffect()) }
                }
            )
        }

        if (success) {
            _effects.emit(DownloaderEffect.ModelsAreReady())
        }
    }
}
