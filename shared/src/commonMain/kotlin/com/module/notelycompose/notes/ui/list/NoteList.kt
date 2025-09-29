package com.module.notelycompose.notes.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.module.notelycompose.export.ui.SelectAllToExportUi
import com.module.notelycompose.notes.ui.list.model.NoteUiModel

@Composable
fun NoteList(
    noteList: List<NoteUiModel>,
    onNoteClicked: (Long) -> Unit,
    onNoteDeleteClicked: (NoteUiModel) -> Unit,
    isSelectAllAction: Boolean,
    onCancelSelectionAction: () -> Unit
) {
    var isAllChecked by remember { mutableStateOf(false) }
    var selectedNoteIds by remember { mutableStateOf(setOf<Long>()) }

    if (isAllChecked) {
        selectedNoteIds = noteList.map { it.id }.toSet()
    } else if (selectedNoteIds.size == noteList.size) {
        selectedNoteIds = setOf()
    }

    SelectAllToExportUi(
        isSelectAllAction = isSelectAllAction,
        onSelectAllChecked = { isAllChecked = it },
        onCancelSelectionAction = onCancelSelectionAction
    )
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 300.dp),
        modifier = Modifier.padding(top = 8.dp, start = 20.dp, end = 20.dp),
        verticalItemSpacing = 8.dp,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(items = noteList) { index, note ->
            NoteItem(
                note = note,
                onNoteClick = {
                    onNoteClicked(note.id)
                },
                onDeleteClick = {
                    onNoteDeleteClicked(note)
                },
                isChecked = selectedNoteIds.contains(note.id),
                onCheckedChange = { noteId, checked ->
                    selectedNoteIds = if (checked) {
                        selectedNoteIds + noteId
                    } else {
                        selectedNoteIds - noteId
                    }
                },
                isSelectAllAction = isSelectAllAction,
                onNoteLongPress = onCancelSelectionAction
            )
        }
    }
}