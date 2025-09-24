package com.module.notelycompose.notes.ui.settings

import androidx.compose.runtime.Composable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.platform.getPlatform

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

    // Calculate text size based on slider value (12sp to 32sp range)
    val minTextSize = 12f
    val maxTextSize = 32f
    val currentTextSize = minTextSize + (maxTextSize - minTextSize) * sliderValue

    Column(
        modifier = modifier
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

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // content start

            // Header
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Body Text Size",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )

                Text(
                    text = "Use the slider to set the preferred writing body size for the note editor, customise your accessibility.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }

            // Slider Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Slider with A labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Small A
                    Text(
                        text = "A",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    // Slider with tick marks
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
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
                        text = "A",
                        fontSize = 24.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Default label
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // Example text that changes size
            Text(
                text = "Example",
                fontSize = currentTextSize.sp,
                color = Color.Black,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                textAlign = TextAlign.Center
            )

            // content end
        }


    }
}

