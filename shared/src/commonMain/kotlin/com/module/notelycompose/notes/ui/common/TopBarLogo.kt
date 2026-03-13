package com.module.notelycompose.notes.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.ic_topbar_logo
import org.jetbrains.compose.resources.painterResource

@Composable
fun TopBarLogo(
    size: Dp = 36.dp,
    tinted: Boolean = true
) {
    val tintColor = if (tinted) LocalCustomColors.current.bodyContentColor else null
    Image(
        painter = painterResource(Res.drawable.ic_topbar_logo),
        contentDescription = "MolyEcho",
        modifier = Modifier.size(size),
        colorFilter = tintColor?.let { ColorFilter.tint(it) }
    )
}
