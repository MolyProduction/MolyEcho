package com.module.notelycompose.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// UI-Anpassung: Wärmere Akzente (Erdtöne / warmes Grün / gedämpftes Gold)
// Light – primary:          #6200EE → #5C6B35 (Olive-Grün)
// Light – primaryVariant:   #3700B3 → #3D4A22 (Dunkel-Olive)
// Light – secondary:        #03DAC5 → #7A6835 (gedämpftes Gold-Braun)
// Dark  – primary:          #BB86FC → #A5C47A (Salbei-Grün)
// Dark  – primaryVariant:   #3700B3 → #3D4A22 (Dunkel-Olive)
// Dark  – secondary:        #03DAC5 → #C9A95C (warmes Gold)
private val LightColorPalette = lightColors(
    primary = Color(0xFF5C6B35),
    primaryVariant = Color(0xFF3D4A22),
    secondary = Color(0xFF7A6835)
)

private val DarkColorPalette = darkColors(
    primary = Color(0xFFA5C47A),
    primaryVariant = Color(0xFF3D4A22),
    secondary = Color(0xFFC9A95C)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette
    val customColors = if (darkTheme) DarkCustomColors else LightCustomColors
    val typography = Typography(
        body1 = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        )
    )
    val shapes = Shapes(
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(0.dp)
    )

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colors = colors,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
