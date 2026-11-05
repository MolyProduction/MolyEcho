package com.module.notelycompose.audio.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.module.notelycompose.audio.domain.AudioRecorderInteractor
import com.module.notelycompose.audio.domain.AudioRecorderPresentationState
import com.module.notelycompose.audio.ui.recorder.AudioRecorderUiState
import com.module.notelycompose.audio.ui.recorder.ScreenState
import com.module.notelycompose.dictation.DictationSession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AudioRecorderViewModel(
    private val interactor: AudioRecorderInteractor,
    private val dictationSession: DictationSession
) : ViewModel() {
    private val dictationSessionId = dictationSession.sessionId

    val audioRecorderPresentationState: StateFlow<AudioRecorderPresentationState> =
        interactor.state

    init {
        setupRecorder()
    }
    private val _screenState = MutableStateFlow(ScreenState.Initial)
    val uiState: StateFlow<ScreenState> = _screenState.asStateFlow()

    fun onStartRecording(noteId: Long?) {
        interactor.initState()
        interactor.onStartRecording(noteId,viewModelScope, {
            _screenState.update { ScreenState.Recording }
        })
    }

    fun onStopRecording() = viewModelScope.launch {
        // The interactor launches its own child coroutine (stop service, wait, read file path).
        // coroutineScope {} waits for it, so Success is only set once recordingPath is known.
        // Without this, MolyDiktat (no 2 s success delay) saved an empty path into the note.
        coroutineScope { interactor.onStopRecording(this) }
        _screenState.update { ScreenState.Success }
    }

    fun setupRecorder() {
        interactor.setupRecorder(viewModelScope)
    }

    fun finishRecorder() {
        interactor.finishRecorder(viewModelScope)
    }

    fun onPauseRecording() {
        interactor.onPauseRecording(viewModelScope)
    }

    fun onResumeRecording() {
        interactor.onResumeRecording(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        // MolyDiktat: Wird der Recorder ohne erfolgreiche Aufnahme verlassen (Berechtigung
        // abgelehnt, Zurück), endet das Diktat. Hier statt in einem DisposableEffect, weil
        // onCleared() beim Drehen NICHT läuft, die Compose-Entsorgung aber schon.
        if (_screenState.value != ScreenState.Success) dictationSession.finishIfCurrent(dictationSessionId)
        interactor.onCleared()
        onStopRecording()
        finishRecorder()
    }

    fun onRequestAudioPermission() {
        interactor.onRequestAudioPermission(viewModelScope)
    }

    fun onGetUiState(presentationState: AudioRecorderPresentationState): AudioRecorderUiState {
        return interactor.onGetUiState(presentationState)
    }
}
