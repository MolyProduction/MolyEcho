package com.module.notelycompose.shareimport

import kotlinx.coroutines.flow.StateFlow

interface ShareImportCoordinator {
    val state: StateFlow<ShareImportState>
    /** Muss aufgerufen werden, nachdem Ready/Error konsumiert wurde → zurück zu Idle. */
    fun consumed()
}

sealed class ShareImportState {
    object Idle : ShareImportState()
    object Loading : ShareImportState()
    data class Ready(val noteId: Long) : ShareImportState()
    data class Error(val message: String) : ShareImportState()
}
