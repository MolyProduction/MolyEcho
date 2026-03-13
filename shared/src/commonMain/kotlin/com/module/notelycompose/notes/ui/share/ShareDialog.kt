package com.module.notelycompose.notes.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.share_options
import de.molyecho.notlyvoice.resources.share_text
import de.molyecho.notlyvoice.resources.share_audio_recording
import de.molyecho.notlyvoice.resources.close
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShareDialog(
    onShareAudioRecording: () -> Unit,
    onShareTexts: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.share_options),
                style = MaterialTheme.typography.h6
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onShareAudioRecording()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = LocalCustomColors.current.shareDialogButtonColor,
                        contentColor = LocalCustomColors.current.bodyBackgroundColor
                    )
                ) {
                    Text(stringResource(Res.string.share_audio_recording))
                }

                Button(
                    onClick = {
                        onShareTexts()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = LocalCustomColors.current.shareDialogButtonColor,
                        contentColor = LocalCustomColors.current.bodyBackgroundColor
                    )
                ) {
                    Text(stringResource(Res.string.share_text))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = LocalCustomColors.current.shareDialogBackgroundColor,
                    contentColor = LocalCustomColors.current.bodyContentColor
                )
            ) {
                Text(
                    text = stringResource(Res.string.close)
                )
            }
        },
        backgroundColor = LocalCustomColors.current.shareDialogBackgroundColor,
        contentColor = LocalCustomColors.current.bodyContentColor
    )
}
