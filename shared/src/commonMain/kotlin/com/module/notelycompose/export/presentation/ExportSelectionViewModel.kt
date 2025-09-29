package com.module.notelycompose.export.presentation

import androidx.lifecycle.ViewModel
import com.module.notelycompose.export.domain.ExportSelectionInteractor

class ExportSelectionViewModel(
    private val exportSelectionInteractor: ExportSelectionInteractor
) : ViewModel() {

    fun exportSelection() {
        exportSelectionInteractor.exportAllSelection()
    }

    fun exportTextSelectionOnly() {
        exportSelectionInteractor.exportTextSelectionOnly()
    }
}
