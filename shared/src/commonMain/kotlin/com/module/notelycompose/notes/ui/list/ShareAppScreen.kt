package com.module.notelycompose.notes.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.platform.HandlePlatformBackNavigation
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.platform.presentation.PlatformViewModel
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.share_app_body
import de.molyecho.notlyvoice.resources.share_app_button
import de.molyecho.notlyvoice.resources.share_app_image_description
import de.molyecho.notlyvoice.resources.share_app_message
import de.molyecho.notlyvoice.resources.share_app_title
import de.molyecho.notlyvoice.resources.share_card
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Nativer "App weiterempfehlen"-Screen: Hinweis, dass MolyEcho nur durch
 * Weiterempfehlung sichtbar wird, plus QR-Teilen-Karte und Share-Button.
 */
@Composable
fun ShareAppScreen(
    onNavigateBack: () -> Unit,
    platformViewModel: PlatformViewModel = koinViewModel()
) {
    val shareMessage = stringResource(Res.string.share_app_message)
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalCustomColors.current.bodyBackgroundColor)
    ) {
        if (getPlatform().isAndroid) {
            AndroidNoteTopBar(
                title = stringResource(Res.string.share_app_title),
                onNavigateBack = onNavigateBack
            )
        } else {
            IOSNoteTopBar(
                onNavigateBack = onNavigateBack
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.share_app_body),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = LocalCustomColors.current.bodyContentColor
            )

            Spacer(modifier = Modifier.height(20.dp))

            Image(
                painter = painterResource(Res.drawable.share_card),
                contentDescription = stringResource(Res.string.share_app_image_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        val imageBytes = Res.readBytes("drawable/share_card.png")
                        platformViewModel.shareImageWithText(imageBytes, shareMessage)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = stringResource(Res.string.share_app_button),
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    HandlePlatformBackNavigation(enabled = true) {
        onNavigateBack()
    }
}
