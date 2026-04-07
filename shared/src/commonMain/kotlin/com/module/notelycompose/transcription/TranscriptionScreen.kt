package com.module.notelycompose.transcription

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.presentation.detail.TextEditorViewModel
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.platform.HandlePlatformBackNavigation
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.resources.vectors.IcChevronLeft
import com.module.notelycompose.resources.vectors.Images
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.molyecho_logo
import de.molyecho.notlyvoice.resources.top_bar_back
import de.molyecho.notlyvoice.resources.transcription_dialog_append
import de.molyecho.notlyvoice.resources.transcription_dialog_original
import de.molyecho.notlyvoice.resources.transcription_dialog_summarize
import de.molyecho.notlyvoice.resources.transcription_dialog_error_got_it
import de.molyecho.notlyvoice.resources.transcription_dialog_error_audio_file_title
import de.molyecho.notlyvoice.resources.transcription_dialog_error_audio_file_desc
import de.molyecho.notlyvoice.resources.transcription_loading_model
import de.molyecho.notlyvoice.resources.transcription_long_running_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TranscriptionScreen(
    navigateBack: () -> Unit,
    viewModel: TranscriptionViewModel = koinViewModel(),
    editorViewModel: TextEditorViewModel
) {

    val scrollState = rememberScrollState()
    val transcriptionUiState by viewModel.uiState.collectAsState()
    val editorState by editorViewModel.editorPresentationState.collectAsState()


    LaunchedEffect(transcriptionUiState.originalText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    // When the app process is killed while TranscriptionScreen is in the back stack and
    // the user taps the completion notification, Android recreates MainActivity and restores
    // the nav back stack. At that moment NoteDetailScreen (which sits below in the stack)
    // has not yet been composed, so onGetNoteById() hasn't been called and recordingPath
    // is still the default empty string. Calling startRecognizer("") would trigger a
    // FileNotFoundException caught as a generic Exception, showing the misleading
    // "audio file too large" error. Guard against this by navigating back immediately;
    // the user will land on NoteDetailScreen which will load the note data normally.
    LaunchedEffect(Unit) {
        val path = editorState.recording.recordingPath
        if (path.isBlank()) {
            navigateBack()
            return@LaunchedEffect
        }
        viewModel.startRecognizer(path)
    }
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopRecognizer()
            viewModel.finishRecognizer()
        }
    }

    // Auto-save transcription text to the note database the moment transcription completes.
    // This runs while the foreground service (and its WakeLock) are still active, so the
    // text is persisted to the DB before the process becomes killable.
    val wasTranscribing = remember { mutableStateOf(false) }
    val autoSaved = remember { mutableStateOf(false) }
    LaunchedEffect(transcriptionUiState.inTranscription) {
        val currentlyTranscribing = transcriptionUiState.inTranscription
        if (!currentlyTranscribing && wasTranscribing.value && !autoSaved.value) {
            val text = transcriptionUiState.originalText
            if (text.isNotBlank()) {
                val existingText = editorState.content.text
                val separator = if (existingText.isNotBlank()) "\n" else ""
                editorViewModel.onUpdateContent(
                    TextFieldValue("$existingText$separator$text")
                )
                autoSaved.value = true
            }
        }
        wasTranscribing.value = currentlyTranscribing
    }

        Card(
            backgroundColor = LocalCustomColors.current.bodyBackgroundColor,
            elevation = 0.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 48.dp)
            ) {
                Box(modifier = Modifier.align(Alignment.Start)
                    .padding(start = 4.dp, bottom = 12.dp, top = 4.dp)) {
                    BackButton(onNavigateBack = {
                        viewModel.stopRecognizer()
                        viewModel.finishRecognizer()
                        navigateBack()
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .border(
                            2.dp,
                            LocalCustomColors.current.bodyContentColor,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                ) {
                    Text(
                        text = if(transcriptionUiState.viewOriginalText) transcriptionUiState.originalText else transcriptionUiState.summarizedText,
                        color = LocalCustomColors.current.bodyContentColor,
                        style = TextStyle(fontSize = editorState.bodyTextSize.sp)
                    )
                }
                if (transcriptionUiState.isModelLoading) {
                    val infiniteTransition = rememberInfiniteTransition(label = "logo_pulse")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "logo_pulse_alpha"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            painter = painterResource(Res.drawable.molyecho_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(top = 8.dp)
                                .alpha(pulseAlpha),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = stringResource(Res.string.transcription_loading_model),
                            color = LocalCustomColors.current.bodyContentColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            strokeCap = StrokeCap.Round
                        )
                    }
                } else if (transcriptionUiState.inTranscription && transcriptionUiState.progress == 0) {
                    LinearProgressIndicator(
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                        strokeCap = StrokeCap.Round
                    )
                } else if (transcriptionUiState.inTranscription && transcriptionUiState.progress in 1..99) {
                    SmoothLinearProgressBar((transcriptionUiState.progress / 100f))
                }

                if (transcriptionUiState.showLongRunningHint
                    && transcriptionUiState.inTranscription
                    && !transcriptionUiState.isModelLoading
                ) {
                    LongRunningHintCard()
                }

//                FloatingActionButton(
//                    modifier = Modifier.padding(vertical = 8.dp),
//                    shape = CircleShape,
//                    onClick = {
//                        if (!transcriptionUiState.isListening) {
//                            onRecognitionStart()
//                        } else {
//                            onRecognitionStopped()
//                        }
//                    },
//                    backgroundColor = if (transcriptionUiState.isListening) Color.Red else Color.Green
//                ) {
//                    Icon(
//                        imageVector = Images.Icons.IcRecorder,
//                        contentDescription = stringResource(Res.string.note_detail_recorder),
//                        tint = LocalCustomColors.current.bodyContentColor
//                    )
//                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !transcriptionUiState.inTranscription,
                        border = BorderStroke(
                            width = 2.dp,
                            color = if(!transcriptionUiState.inTranscription) {
                                LocalCustomColors.current.bodyContentColor
                            } else {
                                LocalCustomColors.current.bodyContentColor.copy(alpha = 0.38f)
                            }
                        ),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalCustomColors.current.bodyContentColor,
                            disabledContentColor = LocalCustomColors.current.bodyContentColor.copy(alpha = 0.38f)
                        ),
                        content = {
                            Text(
                                stringResource(Res.string.transcription_dialog_append)
                            )
                        },
                        onClick = {
                            // Skip if auto-save already wrote the text to the note DB to
                            // prevent duplication.
                            if (!autoSaved.value) {
                                val result = if (transcriptionUiState.viewOriginalText) transcriptionUiState.originalText else transcriptionUiState.summarizedText
                                editorViewModel.onUpdateContent(TextFieldValue("${editorState.content.text}\n$result"))
                            }
                            navigateBack()
                        }
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !transcriptionUiState.inTranscription,
                        border = BorderStroke(
                            width = 2.dp,
                            color = if(!transcriptionUiState.inTranscription) {
                                LocalCustomColors.current.bodyContentColor
                            } else {
                                LocalCustomColors.current.bodyContentColor.copy(alpha = 0.38f)
                            }
                        ),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = LocalCustomColors.current.bodyContentColor,
                            disabledContentColor = LocalCustomColors.current.bodyContentColor.copy(alpha = 0.38f)
                        ),
                        content = {
                            Text(
                                if(transcriptionUiState.viewOriginalText) stringResource(Res.string.transcription_dialog_summarize) else
                                    stringResource(Res.string.transcription_dialog_original),
                                fontSize = 12.sp
                            )
                        }, onClick = {
                            viewModel.summarize()
                        })
                }

            }
        }

    HandlePlatformBackNavigation(enabled = true) {
        navigateBack()
    }

    if(transcriptionUiState.hasError) {
        AlertDialog(
            onDismissRequest = navigateBack,
            confirmButton = {
                TextButton(onClick = navigateBack) {
                    Text(stringResource(Res.string.transcription_dialog_error_got_it))
                }
            },
            title = { Text(stringResource(Res.string.transcription_dialog_error_audio_file_title)) },
            text = { Text(stringResource(Res.string.transcription_dialog_error_audio_file_desc)) }
        )
    }

}
@Composable
fun BackButton(
    onNavigateBack: () -> Unit
) {
    if (getPlatform().isAndroid) {
        IconButton(
            onClick = onNavigateBack,
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(Res.string.top_bar_back),
                tint = LocalCustomColors.current.bodyContentColor
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable { onNavigateBack() }
        ) {
            androidx.compose.material.Icon(
                imageVector = Images.Icons.IcChevronLeft,
                contentDescription = stringResource(Res.string.top_bar_back),
                modifier = Modifier.size(28.dp),
                tint = LocalCustomColors.current.bodyContentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.material.Text(
                text = stringResource(Res.string.top_bar_back),
                style = androidx.compose.material.MaterialTheme.typography.body1,
                color = LocalCustomColors.current.bodyContentColor
            )
        }
    }
}


@Composable
private fun LongRunningHintCard() {
    androidx.compose.material3.Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = LocalCustomColors.current.bodyBackgroundColor
        ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Image(
                painter = painterResource(Res.drawable.molyecho_logo),
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.transcription_long_running_hint),
                color = LocalCustomColors.current.bodyContentColor,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SmoothLinearProgressBar(progress: Float) {
    // Animate the progress value for smooth transitions
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500) // Adjust duration as needed
    )

    LinearProgressIndicator(
        progress,
        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
        strokeCap = StrokeCap.Round
    )
}





