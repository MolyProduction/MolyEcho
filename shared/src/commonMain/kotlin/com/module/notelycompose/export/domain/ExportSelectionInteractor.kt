package com.module.notelycompose.export.domain

interface ExportSelectionInteractor {
    fun exportAllSelection(
        audioPath: List<String>,
        texts: List<String>
    )
    fun exportTextSelectionOnly()
}
