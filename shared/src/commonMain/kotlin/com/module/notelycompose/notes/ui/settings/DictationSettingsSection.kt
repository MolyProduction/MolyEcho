package com.module.notelycompose.notes.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.PlatformUtils
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.dictation_settings_auto_copy
import de.molyecho.notlyvoice.resources.dictation_settings_auto_copy_desc
import de.molyecho.notlyvoice.resources.dictation_settings_auto_transcribe
import de.molyecho.notlyvoice.resources.dictation_settings_auto_transcribe_desc
import de.molyecho.notlyvoice.resources.dictation_settings_description
import de.molyecho.notlyvoice.resources.dictation_settings_notify_ready
import de.molyecho.notlyvoice.resources.dictation_settings_notify_ready_desc
import de.molyecho.notlyvoice.resources.dictation_settings_pin_button
import de.molyecho.notlyvoice.resources.dictation_settings_pin_unsupported
import de.molyecho.notlyvoice.resources.dictation_settings_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Einstellungen für MolyDiktat: drei Optionen (A/B/C) und die Verknüpfung auf dem Startbildschirm. */
@Composable
fun DictationSettingsSection(
    preferencesRepository: PreferencesRepository = koinInject(),
    platformUtils: PlatformUtils = koinInject()
) {
    val scope = rememberCoroutineScope()
    val autoTranscribe by preferencesRepository.getDictationAutoTranscribe().collectAsState(true)
    val autoCopy by preferencesRepository.getDictationAutoCopy().collectAsState(true)
    val notifyReady by preferencesRepository.getDictationNotifyReady().collectAsState(true)
    var pinUnsupported by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(Res.string.dictation_settings_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCustomColors.current.bodyContentColor,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = stringResource(Res.string.dictation_settings_description),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = LocalCustomColors.current.bodyContentColor,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    LocalCustomColors.current.settingsBodyBorderColor,
                    RoundedCornerShape(12.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = LocalCustomColors.current.bodyBackgroundColor
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                DictationSwitchRow(
                    title = stringResource(Res.string.dictation_settings_auto_transcribe),
                    description = stringResource(Res.string.dictation_settings_auto_transcribe_desc),
                    checked = autoTranscribe,
                    enabled = true,
                    onCheckedChange = { scope.launch { preferencesRepository.setDictationAutoTranscribe(it) } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                // B und C greifen nur nach automatischer Transkription (Option A).
                DictationSwitchRow(
                    title = stringResource(Res.string.dictation_settings_auto_copy),
                    description = stringResource(Res.string.dictation_settings_auto_copy_desc),
                    checked = autoCopy,
                    enabled = autoTranscribe,
                    onCheckedChange = { scope.launch { preferencesRepository.setDictationAutoCopy(it) } }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DictationSwitchRow(
                    title = stringResource(Res.string.dictation_settings_notify_ready),
                    description = stringResource(Res.string.dictation_settings_notify_ready_desc),
                    checked = notifyReady,
                    enabled = autoTranscribe,
                    onCheckedChange = { scope.launch { preferencesRepository.setDictationNotifyReady(it) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { pinUnsupported = !platformUtils.requestPinDictationShortcut() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(Res.string.dictation_settings_pin_button))
        }
        if (pinUnsupported) {
            Text(
                text = stringResource(Res.string.dictation_settings_pin_unsupported),
                fontSize = 13.sp,
                color = LocalCustomColors.current.bodyContentColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DictationSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = LocalCustomColors.current.bodyContentColor.copy(alpha = alpha)
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = LocalCustomColors.current.bodyContentColor.copy(alpha = alpha)
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = LocalCustomColors.current.bodyContentColor
            )
        )
    }
}
