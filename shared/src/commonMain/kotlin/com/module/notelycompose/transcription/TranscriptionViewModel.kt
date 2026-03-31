package com.module.notelycompose.transcription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.module.notelycompose.core.debugPrintln
import com.module.notelycompose.modelDownloader.ModelSelection
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.Transcriber
import com.module.notelycompose.summary.Text2Summary
import com.module.notelycompose.transcription.textAnalysis.getSegmenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

const val SPACE_STR = " "

class TranscriptionViewModel(
    private val transcriber: Transcriber,
    private val preferencesRepository: PreferencesRepository,
    private val modelSelection: ModelSelection,
    private val serviceController: TranscriptionServiceController
) : ViewModel() {
    private val _uiState = MutableStateFlow(TranscriptionUiState())
    val uiState: StateFlow<TranscriptionUiState> = _uiState

    fun initRecognizer() {
        viewModelScope.launch(Dispatchers.IO) {
            val modelFileName = modelSelection.getSelectedModel()
            transcriber.initialize(modelFileName.name, modelFileName.format)
        }
    }


    fun startRecognizer(filePath: String) {
        debugPrintln{"startRecognizer ========================="}
        serviceController.startTranscriptionService()
        viewModelScope.launch(Dispatchers.IO) {
            val audioDurationSeconds = transcriber.getAudioDurationSeconds(filePath)
            // Show loading indicator immediately so the user sees feedback.
            _uiState.update { current ->
                current.copy(inTranscription = true, isModelLoading = true, showLongRunningHint = audioDurationSeconds > 20)
            }
            // Yield so the Main thread has a chance to render the loading state before
            // initialize() returns (which may be instant if model is already cached).
            yield()
            // Initialize the correct model sequentially before transcribing.
            // This prevents the race condition where start() runs before the model is loaded.
            val modelFileName = modelSelection.getSelectedModel()
            transcriber.initialize(modelFileName.name, modelFileName.format)

            // Model is loaded (or was already cached) — update notification and UI
            serviceController.notifyTranscriptionPhaseTranscribing()
            _uiState.update { it.copy(isModelLoading = false) }

            val transcriptionLanguage = preferencesRepository.getDefaultTranscriptionLanguage().first()
                .ifBlank { "de" } // defensive: ensure non-empty language code
            val segmenter = getSegmenter(transcriptionLanguage)
            transcriber.start(
                filePath, transcriptionLanguage, onProgress = { progress ->
                    debugPrintln{"progress ========================= $progress"}
                    _uiState.update { current ->
                        current.copy(
                            progress = progress
                        )
                    }
                }, onNewSegment = { _, _, text ->

                    val delimiter = if(_uiState.value.originalText.endsWith(".")) "\n\n" else SPACE_STR
                    debugPrintln{"\n text ========================= $text"}
                    _uiState.update { current ->
                        current.copy(
                            originalText = segmenter.segmentText("${_uiState.value.originalText.trim()}$delimiter${text.trim()}".trim()).joinToString("\n\n"),
                            partialText = text
                        )
                    }

                },
                onComplete = {
                    // Signal service to post completion notification and stop itself
                    serviceController.notifyTranscriptionComplete()
                    debugPrintln{"\n completed ========================= "}
                    _uiState.update {current ->
                        current.copy(
                            inTranscription = false,
                            progress = 100
                        )
                    }
                },
                onError = {
                    serviceController.stopTranscriptionService()
                    debugPrintln{"\n error ========================= "}
                    _uiState.update {current ->
                        current.copy(
                            inTranscription = false,
                            isModelLoading = false,
                            progress = 100,
                            hasError = true
                        )
                    }
                })
        }

    }

    fun stopRecognizer() {
        _uiState.update { current ->
            current.copy(inTranscription = false, isModelLoading = false)
        }
        viewModelScope.launch {
            transcriber.stop()
        }
    }

    fun finishRecognizer() {
        serviceController.stopTranscriptionService()
        _uiState.update { current ->
            current.copy(
                inTranscription = false,
                isModelLoading = false,
                originalText = "",
                finalText = "",
                partialText = "",
                summarizedText = "",
                progress = 0
            )
        }
        viewModelScope.launch {
            transcriber.finish()
        }
    }

    fun summarize() {
        if (_uiState.value.viewOriginalText) {
            viewModelScope.launch {
                val summarizedText = Text2Summary.summarize(_uiState.value.originalText, 0.7f)
                _uiState.update { current ->
                    current.copy(viewOriginalText = false, summarizedText = summarizedText)
                }

            }
        } else {
            _uiState.update { current ->
                current.copy(viewOriginalText = true)
            }
        }

    }

    override fun onCleared() {
        // onCleared() may be called while model loading is in progress (user swiped app
        // away). We must NOT stop the service here — finish() uses a Mutex to wait for
        // the JNI call. This coroutine: (1) aborts transcription, (2) waits for model
        // loading to complete, (3) only then stops the service.
        _uiState.update { it.copy(inTranscription = false, isModelLoading = false) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transcriber.stop()
                transcriber.finish()
            } catch (e: Throwable) {
                e.printStackTrace()
            } finally {
                serviceController.stopTranscriptionService()
            }
        }
    }
}
