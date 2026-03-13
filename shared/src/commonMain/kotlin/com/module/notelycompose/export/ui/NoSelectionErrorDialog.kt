package com.module.notelycompose.export.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.batch_export_no_selection_made
import de.molyecho.notlyvoice.resources.cancel
import de.molyecho.notlyvoice.resources.error
import org.jetbrains.compose.resources.stringResource

@Composable
fun NoSelectionErrorDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    text = stringResource(Res.string.error),
                    color = LocalCustomColors.current.bodyContentColor
                )
            },
            text = {
                Text(
                    text = stringResource(Res.string.batch_export_no_selection_made),
                    color = LocalCustomColors.current.bodyContentColor
                )
            },
            confirmButton = {
                androidx.compose.material.TextButton(
                    onClick = onDismiss,
                    colors = androidx.compose.material.ButtonDefaults.buttonColors(
                        backgroundColor = LocalCustomColors.current.shareDialogBackgroundColor,
                        contentColor = LocalCustomColors.current.bodyContentColor
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.padding(8.dp)
                ) {
                    androidx.compose.material.Text(text = stringResource(Res.string.cancel))
                }
            },
            containerColor = LocalCustomColors.current.shareDialogBackgroundColor,
            titleContentColor = LocalCustomColors.current.bodyContentColor,
            shape = RectangleShape
        )
    }
}
