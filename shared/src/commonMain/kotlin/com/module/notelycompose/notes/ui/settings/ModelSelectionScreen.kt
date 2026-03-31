package com.module.notelycompose.notes.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.modelDownloader.DownloaderDialog
import com.module.notelycompose.modelDownloader.DownloaderEffect
import com.module.notelycompose.modelDownloader.ModelDownloaderViewModel
import com.module.notelycompose.modelDownloader.ModelSelection
import com.module.notelycompose.modelDownloader.MULTILINGUAL_EXTENDED_SELECTION
import com.module.notelycompose.modelDownloader.NO_MODEL_SELECTION
import com.module.notelycompose.modelDownloader.OPTIMIZED_MODEL_SELECTION
import com.module.notelycompose.modelDownloader.STANDARD_MODEL_SELECTION
import com.module.notelycompose.notes.ui.detail.DownloadModelDialog
import com.module.notelycompose.notes.ui.detail.AndroidNoteTopBar
import com.module.notelycompose.notes.ui.detail.IOSNoteTopBar
import com.module.notelycompose.notes.ui.theme.LocalCustomColors
import com.module.notelycompose.onboarding.data.PreferencesRepository
import com.module.notelycompose.platform.getPlatform
import com.module.notelycompose.platform.Transcriber
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.delete_model
import de.molyecho.notlyvoice.resources.manage_models
import de.molyecho.notlyvoice.resources.model_label_german_accurate
import de.molyecho.notlyvoice.resources.model_label_german_quick
import de.molyecho.notlyvoice.resources.model_label_multilingual_extended
import de.molyecho.notlyvoice.resources.section_label_german
import de.molyecho.notlyvoice.resources.section_label_multilingual
import de.molyecho.notlyvoice.resources.manage_models_desc
import de.molyecho.notlyvoice.resources.model_cannot_delete
import de.molyecho.notlyvoice.resources.model_info_size
import de.molyecho.notlyvoice.resources.speech_mode_german_accurate_subtitle
import de.molyecho.notlyvoice.resources.speech_mode_german_accurate_title
import de.molyecho.notlyvoice.resources.speech_mode_german_quick_subtitle
import de.molyecho.notlyvoice.resources.speech_mode_german_quick_title
import de.molyecho.notlyvoice.resources.speech_mode_multilingual_subtitle
import de.molyecho.notlyvoice.resources.speech_mode_multilingual_title
import de.molyecho.notlyvoice.resources.speech_mode_status_download
import de.molyecho.notlyvoice.resources.speech_mode_status_ready
import de.molyecho.notlyvoice.resources.speech_recognition
import de.molyecho.notlyvoice.resources.speech_recognition_desc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val MODE_GERMAN_QUICK = 0
private const val MODE_GERMAN_ACCURATE = 1
private const val MODE_MULTILINGUAL_EXTENDED = 3

data class ModelOption(
    val title: String,
    val description: String,
    val size: String = ""
)

@Composable
fun ModelSelectionScreen(
    navigateBack: () -> Unit,
    navigateToModelExplanation: () -> Unit,
    preferencesRepository: PreferencesRepository = koinInject(),
    transcriber: Transcriber = koinInject(),
    modelSelectionHelper: ModelSelection = koinInject(),
    downloaderViewModel: ModelDownloaderViewModel = koinViewModel()
) {
    val modelSelection by preferencesRepository.getModelSelection().collectAsState(NO_MODEL_SELECTION)
    val coroutineScope = rememberCoroutineScope()

    // currentMode is derived solely from modelSelection — decoupled from the transcription
    // language so that multilingual models can also produce German output.
    val currentMode = when (modelSelection) {
        OPTIMIZED_MODEL_SELECTION       -> MODE_GERMAN_ACCURATE
        MULTILINGUAL_EXTENDED_SELECTION -> MODE_MULTILINGUAL_EXTENDED
        else                            -> MODE_GERMAN_QUICK
    }

    var schnellReady by remember { mutableStateOf(false) }
    var genauReady by remember { mutableStateOf(false) }
    var multiExtendedReady by remember { mutableStateOf(false) }
    var schnellSizeMB by remember { mutableStateOf(0L) }
    var genauSizeMB by remember { mutableStateOf(0L) }
    var multiExtendedSizeMB by remember { mutableStateOf(0L) }
    var showManageModels by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var pendingDownloadMode by remember { mutableStateOf<Int?>(null) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    val downloaderUiState by downloaderViewModel.uiState.collectAsState()

    LaunchedEffect("downloader_effects") {
        downloaderViewModel.effects.collect { effect ->
            when (effect) {
                is DownloaderEffect.ModelsAreReady -> navigateBack()
                is DownloaderEffect.ErrorEffect -> navigateBack() // fallback: download at transcription time
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            schnellReady = transcriber.doesModelExists("whisper-large-v3-turbo-german")
            genauReady = transcriber.doesModelExists("ggml-large-v3-turbo-german.bin")
            multiExtendedReady = transcriber.doesModelExists("ggml-small.bin")
            schnellSizeMB = transcriber.getModelFileSizeBytes("whisper-large-v3-turbo-german") / 1024 / 1024
            genauSizeMB = transcriber.getModelFileSizeBytes("ggml-large-v3-turbo-german.bin") / 1024 / 1024
            multiExtendedSizeMB = transcriber.getModelFileSizeBytes("ggml-small.bin") / 1024 / 1024
        }
    }

    fun selectMode(mode: Int) {
        if (isNavigating) return
        isNavigating = true
        coroutineScope.launch {
            // Save preference first — this is also the fallback if the download fails.
            val selectionConstant = when (mode) {
                MODE_GERMAN_QUICK          -> STANDARD_MODEL_SELECTION
                MODE_GERMAN_ACCURATE       -> OPTIMIZED_MODEL_SELECTION
                MODE_MULTILINGUAL_EXTENDED -> MULTILINGUAL_EXTENDED_SELECTION
                else                       -> STANDARD_MODEL_SELECTION
            }
            preferencesRepository.setModelSelection(selectionConstant)

            val isReady = when (mode) {
                MODE_GERMAN_QUICK          -> schnellReady
                MODE_GERMAN_ACCURATE       -> genauReady
                MODE_MULTILINGUAL_EXTENDED -> multiExtendedReady
                else                       -> true
            }
            if (isReady) {
                navigateBack()
            } else {
                // Model not yet downloaded — show confirmation dialog.
                isNavigating = false
                pendingDownloadMode = mode
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalCustomColors.current.bodyBackgroundColor)
    ) {
        if (getPlatform().isAndroid) {
            AndroidNoteTopBar(title = "", onNavigateBack = navigateBack)
        } else {
            IOSNoteTopBar(onNavigateBack = navigateBack)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(Res.string.speech_recognition),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = LocalCustomColors.current.bodyContentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(Res.string.speech_recognition_desc),
                fontSize = 15.sp,
                color = LocalCustomColors.current.bodyContentColor,
                modifier = Modifier.padding(bottom = 28.dp)
            )

            // ── German models section ──────────────────────────────────────
            SectionLabel(text = stringResource(Res.string.section_label_german))

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_quick_title),
                subtitle = stringResource(Res.string.speech_mode_german_quick_subtitle),
                statusText = if (schnellReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = schnellReady,
                isSelected = currentMode == MODE_GERMAN_QUICK,
                accentColor = Color(0xFF1565C0),
                onClick = { selectMode(MODE_GERMAN_QUICK) },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_german_accurate_title),
                subtitle = stringResource(Res.string.speech_mode_german_accurate_subtitle),
                statusText = if (genauReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = genauReady,
                isSelected = currentMode == MODE_GERMAN_ACCURATE,
                accentColor = Color(0xFF1565C0),
                onClick = { selectMode(MODE_GERMAN_ACCURATE) },
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // ── Multilingual section ───────────────────────────────────────
            SectionLabel(text = stringResource(Res.string.section_label_multilingual))

            SpeechModeCard(
                title = stringResource(Res.string.speech_mode_multilingual_title),
                subtitle = stringResource(Res.string.speech_mode_multilingual_subtitle),
                statusText = if (multiExtendedReady) stringResource(Res.string.speech_mode_status_ready)
                             else stringResource(Res.string.speech_mode_status_download),
                statusReady = multiExtendedReady,
                isSelected = currentMode == MODE_MULTILINGUAL_EXTENDED,
                accentColor = Color(0xFF546E7A),
                onClick = { selectMode(MODE_MULTILINGUAL_EXTENDED) },
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // ── Model management ──────────────────────────────────────────
            val anyDeletable = schnellReady || genauReady || multiExtendedReady
            if (anyDeletable) {
                ManageModelsSection(
                    showManageModels = showManageModels,
                    onToggle = { showManageModels = !showManageModels },
                    schnellReady = schnellReady,
                    schnellSizeMB = schnellSizeMB,
                    genauReady = genauReady,
                    genauSizeMB = genauSizeMB,
                    multiExtendedReady = multiExtendedReady,
                    multiExtendedSizeMB = multiExtendedSizeMB,
                    onDeleteSchnell = {
                        coroutineScope.launch {
                            transcriber.deleteModel("whisper-large-v3-turbo-german")
                            schnellReady = false
                            schnellSizeMB = 0L
                            if (currentMode == MODE_GERMAN_QUICK) {
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    },
                    onDeleteGenau = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-large-v3-turbo-german.bin")
                            genauReady = false
                            genauSizeMB = 0L
                            if (currentMode == MODE_GERMAN_ACCURATE) {
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    },
                    onDeleteMultiExtended = {
                        coroutineScope.launch {
                            transcriber.deleteModel("ggml-small.bin")
                            multiExtendedReady = false
                            multiExtendedSizeMB = 0L
                            if (currentMode == MODE_MULTILINGUAL_EXTENDED) {
                                preferencesRepository.setModelSelection(STANDARD_MODEL_SELECTION)
                            }
                        }
                    }
                )
            }
        }
    }

    pendingDownloadMode?.let { mode ->
        val selectionConstant = when (mode) {
            MODE_GERMAN_QUICK          -> STANDARD_MODEL_SELECTION
            MODE_GERMAN_ACCURATE       -> OPTIMIZED_MODEL_SELECTION
            MODE_MULTILINGUAL_EXTENDED -> MULTILINGUAL_EXTENDED_SELECTION
            else                       -> STANDARD_MODEL_SELECTION
        }
        val model = modelSelectionHelper.getModelBySelection(selectionConstant)
        DownloadModelDialog(
            transcriptionModel = model,
            onDownload = {
                pendingDownloadMode = null
                showDownloadProgress = true
                downloaderViewModel.downloadModelForSettings(model)
            },
            onCancel = {
                pendingDownloadMode = null
                navigateBack()
            }
        )
    }

    if (showDownloadProgress) {
        DownloaderDialog(
            transcriptionModel = downloaderUiState.selectedModel,
            downloaderUiState = downloaderUiState,
            onDismiss = {}
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = LocalCustomColors.current.bodyContentColor,
        modifier = Modifier.padding(bottom = 10.dp)
    )
}

@Composable
private fun SpeechModeCard(
    title: String,
    subtitle: String,
    statusText: String,
    statusReady: Boolean,
    isSelected: Boolean,
    accentColor: Color,
    onClick: (() -> Unit)?,
    expandedContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
                } else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = LocalCustomColors.current.modelSelectionBgColor
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, accentColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LocalCustomColors.current.bodyContentColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Text(
                        text = subtitle,
                        fontSize = 13.sp,
                        color = LocalCustomColors.current.modelSelectionDescColor,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(text = statusText, isReady = statusReady)
                    if (onClick != null) {
                        RadioButton(
                            selected = isSelected,
                            onClick = onClick,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = accentColor,
                                unselectedColor = Color.Gray
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (expandedContent != null) {
                expandedContent()
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, isReady: Boolean) {
    val bgColor = if (isReady) Color(0xFF1B5E20).copy(alpha = 0.1f) else Color(0xFF1565C0).copy(alpha = 0.1f)
    val textColor = if (isReady) Color(0xFF2E7D32) else Color(0xFF1565C0)
    Box(
        modifier = Modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SubOptionRow(
    label: String,
    isSelected: Boolean,
    isReady: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = isSelected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = Color(0xFF546E7A),
                unselectedColor = Color.Gray
            )
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = LocalCustomColors.current.bodyContentColor,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        if (!isReady) {
            StatusBadge(
                text = "⬇",
                isReady = false
            )
        }
    }
}

@Composable
private fun ManageModelsSection(
    showManageModels: Boolean,
    onToggle: () -> Unit,
    schnellReady: Boolean,
    schnellSizeMB: Long,
    genauReady: Boolean,
    genauSizeMB: Long,
    multiExtendedReady: Boolean,
    multiExtendedSizeMB: Long,
    onDeleteSchnell: () -> Unit,
    onDeleteGenau: () -> Unit,
    onDeleteMultiExtended: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = stringResource(Res.string.manage_models),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = LocalCustomColors.current.bodyContentColor
                )
                Text(
                    text = stringResource(Res.string.manage_models_desc),
                    fontSize = 13.sp,
                    color = LocalCustomColors.current.modelSelectionDescColor
                )
            }
            Text(
                text = if (showManageModels) "▲" else "▼",
                color = LocalCustomColors.current.modelSelectionDescColor
            )
        }

        AnimatedVisibility(
            visible = showManageModels,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (schnellReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_german_quick),
                        sizeMB = schnellSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteSchnell
                    )
                }
                if (genauReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_german_accurate),
                        sizeMB = genauSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteGenau
                    )
                }
                if (multiExtendedReady) {
                    ModelManagementRow(
                        name = stringResource(Res.string.model_label_multilingual_extended),
                        sizeMB = multiExtendedSizeMB,
                        isDeletable = true,
                        onDelete = onDeleteMultiExtended
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelManagementRow(
    name: String,
    sizeMB: Long,
    isDeletable: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, fontSize = 14.sp, color = LocalCustomColors.current.bodyContentColor)
            if (sizeMB > 0) {
                Text(
                    text = stringResource(Res.string.model_info_size, sizeMB.toString()),
                    fontSize = 12.sp,
                    color = LocalCustomColors.current.modelSelectionDescColor
                )
            }
        }
        if (isDeletable) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(Res.string.delete_model),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.model_cannot_delete),
                fontSize = 12.sp,
                color = LocalCustomColors.current.modelSelectionDescColor,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
