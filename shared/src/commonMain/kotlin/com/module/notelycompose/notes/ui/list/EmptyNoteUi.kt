package com.module.notelycompose.notes.ui.list

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.common.EmptyStateView
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.empty_list_description
import de.molyecho.notlyvoice.resources.empty_list_description_tablet
import de.molyecho.notlyvoice.resources.empty_list_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun EmptyNoteUi(
    isTablet: Boolean
) {
    val descriptionText = if (isTablet) {
        stringResource(Res.string.empty_list_description_tablet)
    } else {
        stringResource(Res.string.empty_list_description)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .offset(y = (-40).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyStateView(
            text = stringResource(Res.string.empty_list_title),
            imageSize = if (isTablet) 160.dp else 140.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = descriptionText,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
