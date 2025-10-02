package com.module.notelycompose.export.domain

import android.content.Context

class ExportSelectionInteractorImpl(
    private val context: Context
): ExportSelectionInteractor {

    override fun exportAllSelection(
        audioPath: List<String>,
        texts: List<String>
    ) {

    }

    override fun exportTextSelectionOnly() {
        TODO("Not yet implemented")
    }
}
