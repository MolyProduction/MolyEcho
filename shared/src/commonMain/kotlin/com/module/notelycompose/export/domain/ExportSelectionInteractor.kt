package com.module.notelycompose.export.domain

interface ExportSelectionInteractor {
    fun exportAllSelection(
        texts: List<String>,
        titles: List<String>,
        audioPath: List<String>,
        shouldExportAudio: Boolean,
        shouldExportTxt: Boolean,
        onResult: (Result<String>) -> Unit
    )
}
