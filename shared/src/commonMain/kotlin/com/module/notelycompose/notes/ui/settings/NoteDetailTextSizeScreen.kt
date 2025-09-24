package com.module.notelycompose.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.extension.intBodyFontSizes
import com.module.notelycompose.notes.presentation.detail.TextEditorViewModel
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.resources.Res
import com.module.notelycompose.resources.accessibility_a
import com.module.notelycompose.resources.accessibility_desc
import com.module.notelycompose.resources.accessibility_example
import com.module.notelycompose.resources.body_text_default
import com.module.notelycompose.resources.body_text_size
import com.module.notelycompose.resources.recording_ui_checkmark
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val HIDE_TIME_ELAPSE = 1500L
private const val MIN_TEXT_SIZE = 12f
private const val MAX_TEXT_SIZE = 32f

// Function to convert text size to slider value (0.0 to 1.0)
private fun textSizeToSliderValue(textSize: Float): Float {
    return ((textSize - MIN_TEXT_SIZE) / (MAX_TEXT_SIZE - MIN_TEXT_SIZE)).coerceIn(0f, 1f)
}

// Function to convert slider value to text size
private fun sliderValueToTextSize(sliderValue: Float): Float {
    return MIN_TEXT_SIZE + (MAX_TEXT_SIZE - MIN_TEXT_SIZE) * sliderValue
}

@Composable
fun NoteDetailTextSizeScreen(
    navigateBack: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject(),
    editorViewModel: TextEditorViewModel
) {

    var sliderValue by remember { mutableFloatStateOf(0.2f) } // Default to 14f position
    var isProgressVisible by remember { mutableStateOf(false) }
    var isCheckMarkVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val editorState by editorViewModel.editorPresentationState.collectAsState()

    // Load the saved text size and set slider position
    LaunchedEffect(Unit) {
        val savedTextSize = preferencesRepository.getBodyTextSize().first()
        sliderValue = textSizeToSliderValue(savedTextSize)
    }

    // Calculate current text size based on slider value
    val currentTextSize = sliderValueToTextSize(sliderValue)

    Column(
        modifier = Modifier
            .background(LocalCustomColors.current.bodyBackgroundColor)
            .fillMaxWidth()
    ) {
        if (getPlatform().isAndroid) {
            AndroidNoteTopBar(
                title = "",
                onNavigateBack = navigateBack
            )
        } else {
            IOSNoteTopBar(
                onNavigateBack = navigateBack
            )
        }

        if (isProgressVisible) {
            LinearProgressIndicator(
                modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth(),
                strokeCap = StrokeCap.Round
            )
        } else {
            Spacer(
                modifier = Modifier.padding(vertical = 14.dp).fillMaxWidth()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // content start

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.body_text_size),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = LocalCustomColors.current.bodyContentColor
                )

                Text(
                    text = stringResource(Res.string.accessibility_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.accessibility_a),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            if(sliderValue != newValue) {
                                isProgressVisible = true
                            }
                            sliderValue = newValue
                            coroutineScope.launch {
                                preferencesRepository.setBodyTextSize(sliderValueToTextSize(newValue))
                                editorViewModel.onUpdateContent(TextFieldValue(editorState.content.text))
                            }
                        },
                        modifier = Modifier.weight(1f),
                        steps = 8, // Creates 9 positions total
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF007AFF),
                            activeTrackColor = Color(0xFF007AFF),
                            inactiveTrackColor = Color(0xFFE5E5EA)
                        )
                    )

                    // Large A
                    Text(
                        text = stringResource(Res.string.accessibility_a),
                        fontSize = 24.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = stringResource(Res.string.body_text_default),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Example text that changes size
            Text(
                text = stringResource(Res.string.accessibility_example, currentTextSize.intBodyFontSizes()),
                fontSize = currentTextSize.sp,
                color = LocalCustomColors.current.bodyContentColor,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )

            if(isCheckMarkVisible) {
                Row (
                    modifier = Modifier.padding(top = 50.dp)
                ) {
                    SavingBodyTextCheckMark()
                }
            }

            // content end
        }
    }

    LaunchedEffect(sliderValue) {
        if (isProgressVisible) {
            delay(HIDE_TIME_ELAPSE)
            isProgressVisible = false
            isCheckMarkVisible = true
            delay(HIDE_TIME_ELAPSE)
            isCheckMarkVisible = false
        }
    }
}

