package com.module.notelycompose.onboarding.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.module.notelycompose.notes.ui.theme.PoppingsFontFamily
import com.module.notelycompose.platform.presentation.PlatformUiState
import com.module.notelycompose.platform.presentation.PlatformViewModel
import kotlinx.coroutines.launch
import de.molyecho.notlyvoice.resources.Res
import de.molyecho.notlyvoice.resources.ic_topbar_logo
import de.molyecho.notlyvoice.resources.icon
import de.molyecho.notlyvoice.resources.molyecho_logo
import de.molyecho.notlyvoice.resources.model_label_german_accurate
import de.molyecho.notlyvoice.resources.model_label_german_quick
import de.molyecho.notlyvoice.resources.model_label_multilingual_extended
import de.molyecho.notlyvoice.resources.onboarding_android_one
import de.molyecho.notlyvoice.resources.onboarding_android_tablet_one
import de.molyecho.notlyvoice.resources.onboarding_android_tablet_two
import de.molyecho.notlyvoice.resources.onboarding_android_two
import de.molyecho.notlyvoice.resources.onboarding_get_started
import de.molyecho.notlyvoice.resources.onboarding_ios_one
import de.molyecho.notlyvoice.resources.onboarding_ios_tablet_one
import de.molyecho.notlyvoice.resources.onboarding_ios_tablet_two
import de.molyecho.notlyvoice.resources.onboarding_ios_two
import de.molyecho.notlyvoice.resources.onboarding_models_subtitle
import de.molyecho.notlyvoice.resources.onboarding_models_title
import de.molyecho.notlyvoice.resources.onboarding_next
import de.molyecho.notlyvoice.resources.onboarding_screen_one_desc
import de.molyecho.notlyvoice.resources.onboarding_screen_one_title
import de.molyecho.notlyvoice.resources.onboarding_screen_two_desc
import de.molyecho.notlyvoice.resources.onboarding_screen_two_title
import de.molyecho.notlyvoice.resources.onboarding_skip
import de.molyecho.notlyvoice.resources.onboarding_welcome_tagline
import de.molyecho.notlyvoice.resources.speech_mode_german_accurate_subtitle
import de.molyecho.notlyvoice.resources.speech_mode_german_quick_subtitle
import de.molyecho.notlyvoice.resources.speech_mode_multilingual_subtitle
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.module.notelycompose.permissions.PermissionHandler
import de.molyecho.notlyvoice.resources.onboarding_permission_allow
import de.molyecho.notlyvoice.resources.onboarding_permission_battery_desc
import de.molyecho.notlyvoice.resources.onboarding_permission_battery_settings
import de.molyecho.notlyvoice.resources.onboarding_permission_battery_title
import de.molyecho.notlyvoice.resources.onboarding_permission_disabled
import de.molyecho.notlyvoice.resources.onboarding_permission_granted
import de.molyecho.notlyvoice.resources.onboarding_permission_notifications_desc
import de.molyecho.notlyvoice.resources.onboarding_permission_notifications_title
import de.molyecho.notlyvoice.resources.onboarding_permissions_desc
import de.molyecho.notlyvoice.resources.onboarding_permissions_title
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton

private val MolyGreen = Color(0xFF5E8040)
private val OnboardingBackground = Color(0xFFF8F8F8)

data class OnboardingPage(
    val title: String,
    val description: String,
    val androidResources: Painter,
    val iOSResources: Painter
)

@Composable
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    onFinishToModelSelection: () -> Unit = {},
    permissionHandler: PermissionHandler? = null,
    platformState: PlatformUiState
) {
    val featurePages = listOf(
        OnboardingPage(
            title = stringResource(Res.string.onboarding_screen_one_title),
            description = stringResource(Res.string.onboarding_screen_one_desc),
            androidResources = when {
                platformState.isTablet -> painterResource(Res.drawable.onboarding_android_tablet_one)
                else -> painterResource(Res.drawable.onboarding_android_one)
            },
            iOSResources = when {
                platformState.isTablet -> painterResource(Res.drawable.onboarding_ios_tablet_one)
                else -> painterResource(Res.drawable.onboarding_ios_one)
            }
        ),
        OnboardingPage(
            title = stringResource(Res.string.onboarding_screen_two_title),
            description = stringResource(Res.string.onboarding_screen_two_desc),
            androidResources = when {
                platformState.isTablet -> painterResource(Res.drawable.onboarding_android_tablet_two)
                else -> painterResource(Res.drawable.onboarding_android_two)
            },
            iOSResources = when {
                platformState.isTablet -> painterResource(Res.drawable.onboarding_ios_tablet_two)
                else -> painterResource(Res.drawable.onboarding_ios_two)
            }
        )
    )

    val pageCount = 5
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onFinish,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Black),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = stringResource(Res.string.onboarding_skip),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(0.dp)
            ) { page ->
                when (page) {
                    0 -> MolyEchoWelcomePage()
                    3 -> permissionHandler?.let { PermissionsOnboardingPage(it) } ?: ModelOverviewPage()
                    pageCount - 1 -> ModelOverviewPage()
                    else -> OnboardingPageContent(
                        page = featurePages[page - 1],
                        isTablet = platformState.isTablet,
                        isAndroid = platformState.isAndroid
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PageIndicators(
                    pageCount = pageCount,
                    currentPage = pagerState.currentPage,
                    activeColor = MolyGreen,
                    inactiveColor = MolyGreen.copy(alpha = 0.3f)
                )

                Button(
                    onClick = {
                        if (pagerState.currentPage == pageCount - 1) {
                            onFinishToModelSelection()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MolyGreen),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == pageCount - 1)
                            stringResource(Res.string.onboarding_get_started)
                        else
                            stringResource(Res.string.onboarding_next),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun MolyEchoWelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.molyecho_logo),
            contentDescription = "MolyEcho Logo",
            modifier = Modifier.size(200.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(36.dp))
        Text(
            text = "MolyEcho",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboarding_welcome_tagline),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = PoppingsFontFamily(),
            color = MolyGreen,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OnboardingPageContent(
    page: OnboardingPage,
    isTablet: Boolean,
    isAndroid: Boolean
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 44.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VoiceNotePageContent(page, isTablet, isAndroid)
        }
        Image(
            painter = painterResource(Res.drawable.ic_topbar_logo),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 24.dp)
                .size(30.dp)
        )
    }
}

@Composable
fun VoiceNotePageContent(
    page: OnboardingPage,
    isTablet: Boolean,
    isAndroid: Boolean
) {
    val resource = if (isAndroid) page.androidResources else page.iOSResources
    val descriptionFontSize = if (isTablet) 20.sp else 18.sp
    val imageIllustrationWidth = if (isTablet) 800.dp else 360.dp

    Text(
        text = page.title,
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = PoppingsFontFamily(),
        color = Color(0xFF1A1A1A),
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        lineHeight = 32.sp
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = resource,
                contentDescription = "Image illustration",
                modifier = Modifier.width(imageIllustrationWidth),
                contentScale = ContentScale.FillWidth
            )
        }

        Text(
            text = page.description,
            fontSize = descriptionFontSize,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun ModelInfoCard(
    name: String,
    traits: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F5EA))
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MolyGreen)
        )
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = traits,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF555555)
            )
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    buttonLabel: String,
    isGranted: Boolean,
    onButtonClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F5EA)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(MolyGreen)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1A1A1A)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF555555)
            )
        }
        OutlinedButton(
            onClick = onButtonClick,
            enabled = !isGranted,
            modifier = Modifier.padding(end = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MolyGreen,
                disabledContentColor = Color(0xFF888888)
            )
        ) {
            Text(
                text = buttonLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PermissionsOnboardingPage(permissionHandler: PermissionHandler) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val isNotificationGranted by permissionHandler.isNotificationGranted.collectAsState()
    val isBatteryDisabled by permissionHandler.isBatteryOptimizationDisabled.collectAsState()

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            permissionHandler.refresh()
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Image(
            painter = painterResource(Res.drawable.icon),
            contentDescription = "MolyEcho",
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_permissions_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_permissions_desc),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        PermissionCard(
            title = stringResource(Res.string.onboarding_permission_notifications_title),
            description = stringResource(Res.string.onboarding_permission_notifications_desc),
            buttonLabel = if (isNotificationGranted)
                stringResource(Res.string.onboarding_permission_granted)
            else
                stringResource(Res.string.onboarding_permission_allow),
            isGranted = isNotificationGranted,
            onButtonClick = { permissionHandler.requestNotificationPermission() }
        )
        Spacer(modifier = Modifier.height(8.dp))
        PermissionCard(
            title = stringResource(Res.string.onboarding_permission_battery_title),
            description = stringResource(Res.string.onboarding_permission_battery_desc),
            buttonLabel = if (isBatteryDisabled)
                stringResource(Res.string.onboarding_permission_disabled)
            else
                stringResource(Res.string.onboarding_permission_battery_settings),
            isGranted = isBatteryDisabled,
            onButtonClick = { permissionHandler.openBatterySettings() }
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun ModelOverviewPage() {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Image(
            painter = painterResource(Res.drawable.icon),
            contentDescription = "MolyEcho",
            modifier = Modifier.size(88.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.onboarding_models_title),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PoppingsFontFamily(),
            color = Color(0xFF1A1A1A),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_models_subtitle),
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        ModelInfoCard(
            name = stringResource(Res.string.model_label_german_quick),
            traits = stringResource(Res.string.speech_mode_german_quick_subtitle)
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModelInfoCard(
            name = stringResource(Res.string.model_label_german_accurate),
            traits = stringResource(Res.string.speech_mode_german_accurate_subtitle)
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModelInfoCard(
            name = stringResource(Res.string.model_label_multilingual_extended),
            traits = stringResource(Res.string.speech_mode_multilingual_subtitle)
        )
        Spacer(modifier = Modifier.height(16.dp))
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
