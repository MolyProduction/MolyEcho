package com.module.notelycompose.notes.ui.settings

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.resources.Res
import com.module.notelycompose.resources.accessibility
import com.module.notelycompose.resources.accessibility_a
import com.module.notelycompose.resources.accessibility_desc
import com.module.notelycompose.resources.accessibility_example
import com.module.notelycompose.resources.body_text_default
import com.module.notelycompose.resources.body_text_size
import com.module.notelycompose.resources.recording_ui_checkmark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

const val HIDE_TIME_ELAPSE = 1500L

@Composable
fun SettingsTextSizeScreen(
    navigateBack: () -> Unit
) {
    TextSizeSlider(
        modifier = Modifier,
        navigateBack = navigateBack
    )
}

@Composable
fun TextSizeSlider(
    modifier: Modifier = Modifier,
    navigateBack: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(0.5f) }
    var isProgressVisible by remember { mutableStateOf(false) }
    var isCheckMarkVisible by remember { mutableStateOf(false) }

    // Calculate text size based on slider value (12sp to 32sp range)
    val minTextSize = 12f
    val maxTextSize = 32f
    val currentTextSize = minTextSize + (maxTextSize - minTextSize) * sliderValue

    Column(
        modifier = modifier
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
            modifier = modifier
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
                        },
                        modifier = Modifier.weight(1f),
                        steps = 8, // Creates 9 positions total (0, 0.125, 0.25, 0.375, 0.5, 0.625, 0.75, 0.875, 1.0)
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
                text = stringResource(Res.string.accessibility_example),
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

    // LaunchedEffect to handle hiding the progress indicator
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

@Composable
internal fun SavingBodyTextCheckMark() {
    val pathColor = LocalCustomColors.current.bodyContentColor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalCustomColors.current.bodyBackgroundColor),
        contentAlignment = Alignment.Center
    ) {
        var animationPlayed by remember { mutableStateOf(false) }
        val pathProgress by animateFloatAsState(
            targetValue = if (animationPlayed) 1f else 0f,
            animationSpec = tween(
                durationMillis = 1000,
                easing = FastOutSlowInEasing
            ),
            label = stringResource(Res.string.recording_ui_checkmark)
        )

        LaunchedEffect(Unit) {
            animationPlayed = true
        }

        Canvas(modifier = Modifier.size(50.dp)) {
            val path = Path().apply {

                addArc(
                    Rect(
                        offset = Offset(0f, 0f),
                        size = Size(size.width, size.height)
                    ),
                    0f,
                    360f * pathProgress
                )

                if (pathProgress > 0.5f) {
                    val checkProgress = (pathProgress - 0.5f) * 2f
                    moveTo(size.width * 0.2f, size.height * 0.5f)
                    lineTo(
                        size.width * 0.45f,
                        size.height * 0.7f * checkProgress
                    )
                    lineTo(
                        size.width * 0.8f,
                        size.height * 0.3f * checkProgress
                    )
                }
            }

            drawPath(
                path = path,
                color = pathColor,
                style = Stroke(
                    width = 8f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
