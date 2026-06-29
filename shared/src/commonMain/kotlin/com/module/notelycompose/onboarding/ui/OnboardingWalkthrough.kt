package com.module.notelycompose.onboarding.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.module.notelycompose.modelDownloader.DownloaderEffect
import com.module.notelycompose.modelDownloader.ModelDownloaderViewModel
import com.module.notelycompose.notes.ui.theme.PoppingsFontFamily
import com.module.notelycompose.permissions.NotificationPermissionState
import com.module.notelycompose.permissions.PermissionHandler
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.molyecho_logo
import de.molyecho.notlyvoice.resources.onboarding_battery_body
import de.molyecho.notlyvoice.resources.onboarding_battery_cta
import de.molyecho.notlyvoice.resources.onboarding_battery_not_granted
import de.molyecho.notlyvoice.resources.onboarding_battery_skip
import de.molyecho.notlyvoice.resources.onboarding_battery_title
import de.molyecho.notlyvoice.resources.onboarding_model_body
import de.molyecho.notlyvoice.resources.onboarding_model_cancel
import de.molyecho.notlyvoice.resources.onboarding_model_cta
import de.molyecho.notlyvoice.resources.onboarding_model_downloading_title
import de.molyecho.notlyvoice.resources.onboarding_model_error
import de.molyecho.notlyvoice.resources.onboarding_model_finish
import de.molyecho.notlyvoice.resources.onboarding_model_info_minimize
import de.molyecho.notlyvoice.resources.onboarding_model_info_once
import de.molyecho.notlyvoice.resources.onboarding_model_info_size
import de.molyecho.notlyvoice.resources.onboarding_model_info_wifi
import de.molyecho.notlyvoice.resources.onboarding_model_progress
import de.molyecho.notlyvoice.resources.onboarding_model_retry
import de.molyecho.notlyvoice.resources.onboarding_model_skip
import de.molyecho.notlyvoice.resources.onboarding_model_subtitle
import de.molyecho.notlyvoice.resources.onboarding_model_success
import de.molyecho.notlyvoice.resources.onboarding_model_title
import de.molyecho.notlyvoice.resources.onboarding_notif_body
import de.molyecho.notlyvoice.resources.onboarding_notif_cta
import de.molyecho.notlyvoice.resources.onboarding_notif_cta_retry
import de.molyecho.notlyvoice.resources.onboarding_notif_denied_hint
import de.molyecho.notlyvoice.resources.onboarding_notif_open_settings
import de.molyecho.notlyvoice.resources.onboarding_notif_permanent_body
import de.molyecho.notlyvoice.resources.onboarding_notif_permanent_title
import de.molyecho.notlyvoice.resources.onboarding_notif_skip
import de.molyecho.notlyvoice.resources.onboarding_notif_title
import de.molyecho.notlyvoice.resources.onboarding_welcome_body
import de.molyecho.notlyvoice.resources.onboarding_welcome_cta
import de.molyecho.notlyvoice.resources.onboarding_welcome_subtitle
import de.molyecho.notlyvoice.resources.onboarding_welcome_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val MolyGreen = Color(0xFF5E8040)
private val OnboardingBackground = Color(0xFFF8F8F8)

private enum class ModelDownloadScreenState { INFO, DOWNLOADING, SUCCESS, ERROR }

@Composable
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    permissionHandler: PermissionHandler
) {
    var currentScreen by remember { mutableStateOf(0) }
    val totalScreens = 4

    BackHandler {
        if (currentScreen > 0) currentScreen -= 1
        // Screen 0: BackHandler tut nichts → Exit blockiert
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(OnboardingBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            PageIndicators(
                pageCount = totalScreens,
                currentPage = currentScreen,
                activeColor = MolyGreen,
                inactiveColor = MolyGreen.copy(alpha = 0.3f),
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    0 -> OnboardingScreen1Welcome(onNext = { currentScreen = 1 })
                    1 -> OnboardingScreen2Notifications(
                        permissionHandler = permissionHandler,
                        onNext = { currentScreen = 2 },
                        onSkip = { currentScreen = 2 }
                    )
                    2 -> OnboardingScreen3Battery(
                        permissionHandler = permissionHandler,
                        onNext = { currentScreen = 3 },
                        onSkip = { currentScreen = 3 }
                    )
                    3 -> OnboardingScreen4Model(onFinish = onFinish)
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────
// Screen 1: Willkommen
// ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen1Welcome(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(Res.drawable.molyecho_logo),
            contentDescription = null,
            modifier = Modifier.size(180.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(Res.string.onboarding_welcome_title),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_welcome_subtitle),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = PoppingsFontFamily(),
            color = MolyGreen,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_welcome_body),
            fontSize = 16.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        OnboardingPrimaryButton(
            text = stringResource(Res.string.onboarding_welcome_cta),
            onClick = onNext
        )
    }
}

// ──────────────────────────────────────────────────────────────
// Screen 2: Benachrichtigungen (Pflicht)
// ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen2Notifications(
    permissionHandler: PermissionHandler,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val isGranted by permissionHandler.isNotificationGranted.collectAsState()
    val notifState by permissionHandler.notificationPermissionState.collectAsState()
    var showCheckmark by remember { mutableStateOf(false) }

    // Wenn erteilt (auch sofort auf Android < 13): kurze Animation, dann weiter
    LaunchedEffect(isGranted) {
        if (isGranted) {
            showCheckmark = true
            delay(700)
            onNext()
        }
    }

    // refresh() bei Rückkehr aus System-Einstellungen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionHandler.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        if (showCheckmark) {
            AnimatedVisibility(
                visible = showCheckmark,
                enter = scaleIn() + fadeIn()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MolyGreen,
                    modifier = Modifier.size(80.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MolyGreen,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.onboarding_notif_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_notif_body),
            fontSize = 16.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )

        if (notifState == NotificationPermissionState.DENIED_ONCE) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_notif_denied_hint),
                fontSize = 14.sp,
                color = Color(0xFFB00020),
                textAlign = TextAlign.Center
            )
        }

        if (notifState == NotificationPermissionState.DENIED_PERMANENT) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_notif_permanent_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB00020),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.onboarding_notif_permanent_body),
                fontSize = 14.sp,
                color = Color(0xFF555555),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!showCheckmark) {
            if (notifState == NotificationPermissionState.DENIED_PERMANENT) {
                OnboardingPrimaryButton(
                    text = stringResource(Res.string.onboarding_notif_open_settings),
                    onClick = { permissionHandler.openNotificationSettings() }
                )
            } else {
                OnboardingPrimaryButton(
                    text = when (notifState) {
                        NotificationPermissionState.DENIED_ONCE -> stringResource(Res.string.onboarding_notif_cta_retry)
                        else -> stringResource(Res.string.onboarding_notif_cta)
                    },
                    onClick = { permissionHandler.requestNotificationPermission() }
                )
            }

            // Exit-Link – erst nach erster Ablehnung sichtbar
            if (notifState != NotificationPermissionState.NOT_ASKED) {
                Spacer(modifier = Modifier.height(16.dp))
                OnboardingTextLink(
                    text = stringResource(Res.string.onboarding_notif_skip),
                    onClick = onSkip
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ──────────────────────────────────────────────────────────────
// Screen 3: Energiesparmodus (Pflicht)
// ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen3Battery(
    permissionHandler: PermissionHandler,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    val isExempt by permissionHandler.isBatteryOptimizationDisabled.collectAsState()
    var hasReturnedFromSettings by remember { mutableStateOf(false) }
    var showCheckmark by remember { mutableStateOf(false) }

    // Wenn Ausnahme nach Rückkehr aus Einstellungen erteilt → Animation + weiter
    LaunchedEffect(isExempt) {
        if (isExempt && hasReturnedFromSettings) {
            showCheckmark = true
            delay(700)
            onNext()
        }
    }

    // refresh() on ON_RESUME – Rückkehr aus den Akku-Einstellungen erkennen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionHandler.refresh()
                hasReturnedFromSettings = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        if (showCheckmark) {
            AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MolyGreen,
                    modifier = Modifier.size(80.dp)
                )
            }
        } else {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MolyGreen,
                modifier = Modifier.size(72.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(Res.string.onboarding_battery_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_battery_body),
            fontSize = 16.sp,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )

        if (hasReturnedFromSettings && !isExempt && !showCheckmark) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.onboarding_battery_not_granted),
                fontSize = 14.sp,
                color = Color(0xFFB00020),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!showCheckmark) {
            OnboardingPrimaryButton(
                text = stringResource(Res.string.onboarding_battery_cta),
                onClick = { permissionHandler.openBatterySettings() }
            )

            // Exit-Link – nur nach Rückkehr ohne Grant sichtbar
            if (hasReturnedFromSettings && !isExempt) {
                Spacer(modifier = Modifier.height(16.dp))
                OnboardingTextLink(
                    text = stringResource(Res.string.onboarding_battery_skip),
                    onClick = onSkip
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ──────────────────────────────────────────────────────────────
// Screen 4: Modell herunterladen (Optional)
// ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingScreen4Model(onFinish: () -> Unit) {
    val downloaderViewModel = koinViewModel<ModelDownloaderViewModel>()
    val downloaderUiState by downloaderViewModel.uiState.collectAsState()
    var downloadState by remember { mutableStateOf(ModelDownloadScreenState.INFO) }

    LaunchedEffect(Unit) {
        downloaderViewModel.effects.collect { effect ->
            when (effect) {
                is DownloaderEffect.DownloadEffect -> downloadState = ModelDownloadScreenState.DOWNLOADING
                is DownloaderEffect.ModelsAreReady -> downloadState = ModelDownloadScreenState.SUCCESS
                is DownloaderEffect.ErrorEffect -> downloadState = ModelDownloadScreenState.ERROR
                else -> {}
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        when (downloadState) {
            ModelDownloadScreenState.INFO -> {
                Text(
                    text = stringResource(Res.string.onboarding_model_title),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PoppingsFontFamily(),
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.onboarding_model_subtitle),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MolyGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5EA))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = stringResource(Res.string.onboarding_model_info_size), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(Res.string.onboarding_model_info_wifi), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(Res.string.onboarding_model_info_once), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = stringResource(Res.string.onboarding_model_info_minimize), fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.onboarding_model_body),
                    fontSize = 14.sp,
                    color = Color(0xFF555555),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                OnboardingPrimaryButton(
                    text = stringResource(Res.string.onboarding_model_cta),
                    onClick = { downloaderViewModel.startDownload() }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OnboardingTextLink(
                    text = stringResource(Res.string.onboarding_model_skip),
                    onClick = onFinish
                )
            }

            ModelDownloadScreenState.DOWNLOADING -> {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = stringResource(Res.string.onboarding_model_downloading_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF1A1A1A),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { downloaderUiState.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MolyGreen,
                    trackColor = MolyGreen.copy(alpha = 0.2f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        Res.string.onboarding_model_progress,
                        downloaderUiState.downloaded,
                        downloaderUiState.total
                    ),
                    fontSize = 14.sp,
                    color = Color(0xFF555555)
                )
                Spacer(modifier = Modifier.height(24.dp))
                OnboardingTextLink(
                    text = stringResource(Res.string.onboarding_model_cancel),
                    onClick = {
                        downloaderViewModel.cancelDownload()
                        downloadState = ModelDownloadScreenState.INFO
                    }
                )
            }

            ModelDownloadScreenState.SUCCESS -> {
                Spacer(modifier = Modifier.height(48.dp))
                AnimatedVisibility(visible = true, enter = scaleIn() + fadeIn()) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MolyGreen,
                        modifier = Modifier.size(80.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(Res.string.onboarding_model_success),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MolyGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                OnboardingPrimaryButton(
                    text = stringResource(Res.string.onboarding_model_finish),
                    onClick = onFinish
                )
            }

            ModelDownloadScreenState.ERROR -> {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = stringResource(Res.string.onboarding_model_error),
                    fontSize = 16.sp,
                    color = Color(0xFFB00020),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                OnboardingPrimaryButton(
                    text = stringResource(Res.string.onboarding_model_retry),
                    onClick = {
                        downloadState = ModelDownloadScreenState.DOWNLOADING
                        downloaderViewModel.startDownload()
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                OnboardingTextLink(
                    text = stringResource(Res.string.onboarding_model_skip),
                    onClick = onFinish
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ──────────────────────────────────────────────────────────────
// Shared UI-Komponenten
// ──────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MolyGreen),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun OnboardingTextLink(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF888888))
    ) {
        Text(text = text, fontSize = 14.sp)
    }
}

@Composable
fun PageIndicators(
    pageCount: Int,
    currentPage: Int,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val animatedWidth by animateDpAsState(
                targetValue = if (isActive) 32.dp else 8.dp,
                animationSpec = tween(300, easing = EaseInOutQuad),
                label = "indicator_width"
            )
            val animatedColor by animateColorAsState(
                targetValue = if (isActive) activeColor else inactiveColor,
                animationSpec = tween(300, easing = EaseInOutQuad),
                label = "indicator_color"
            )
            Box(
                modifier = Modifier
                    .width(animatedWidth)
                    .height(8.dp)
                    .background(animatedColor, RoundedCornerShape(4.dp))
            )
        }
    }
}
