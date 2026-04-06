package com.module.notelycompose.shareimport

import android.net.Uri
import audio.converter.AudioConverter
import android.content.Context
import com.module.notelycompose.notes.domain.InsertNoteUseCase
import com.module.notelycompose.notes.domain.model.TextAlignDomainModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.module.notelycompose.ShareIntentBus

class AndroidShareImportCoordinator(
    private val context: Context,
    private val audioConverter: AudioConverter,
    private val insertNoteUseCase: InsertNoteUseCase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : ShareImportCoordinator {

    private val _state = MutableStateFlow<ShareImportState>(ShareImportState.Idle)
    override val state: StateFlow<ShareImportState> = _state.asStateFlow()

    private val helper = ShareImportHelper(context, audioConverter)

    init {
        scope.launch {
            ShareIntentBus.incoming.collect { uri -> processUri(uri) }
        }
    }

    private suspend fun processUri(uri: Uri) {
        _state.value = ShareImportState.Loading
        _state.value = try {
            val wavPath = helper.importSharedUri(uri)
            val noteId = insertNoteUseCase.execute(
                title = "",
                content = "",
                starred = false,
                formatting = emptyList(),
                textAlign = TextAlignDomainModel.Left,
                recordingPath = wavPath
            ) ?: error("Notiz konnte nicht gespeichert werden")
            ShareImportState.Ready(noteId)
        } catch (e: UnsupportedAudioFormatException) {
            ShareImportState.Error(e.message ?: "Nicht unterstütztes Format")
        } catch (e: Exception) {
            ShareImportState.Error("Audiodatei konnte nicht verarbeitet werden.")
        }
    }

    override fun consumed() {
        _state.value = ShareImportState.Idle
    }
}
