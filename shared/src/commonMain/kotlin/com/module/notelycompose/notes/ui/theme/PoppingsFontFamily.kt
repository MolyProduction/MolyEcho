package com.module.notelycompose.notes.ui.theme

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.poppins_bold
import de.molyecho.notlyvoice.resources.poppins_regular

@Composable
fun PoppingsFontFamily() = FontFamily(
    Font(Res.font.poppins_regular, weight = FontWeight.Normal),
    Font(Res.font.poppins_bold, weight = FontWeight.Bold)
)
