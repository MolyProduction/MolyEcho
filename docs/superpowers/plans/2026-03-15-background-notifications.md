# Background Operation & Notifications Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add notification permission + battery optimization onboarding, a new permissions onboarding screen, direct post-onboarding navigation to model selection, and a download completion notification.

**Architecture:** A `PermissionHandler` interface in commonMain bridges to `PermissionViewModel` in androidMain via `@Composable expect/actual`. Koin wires the concrete class; the commonMain side never imports androidMain types. The onboarding pager grows from 4 to 5 pages and `Routes.LanguageModelSelection` moves to the `Home` graph for a clean back-stack.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Koin 4.x, AndroidX DownloadManager, NotificationManagerCompat, `lifecycle-runtime-compose`

**Spec:** `docs/superpowers/specs/2026-03-15-background-notifications-design.md`

---

## Chunk 1: Foundation — Manifest, Build, Strings

### Task 1: Add POST_NOTIFICATIONS permission to manifest

**Files:**
- Modify: `shared/src/androidMain/AndroidManifest.xml`

- [ ] **Step 1: Add permission**

In `shared/src/androidMain/AndroidManifest.xml`, add after the `android.permission.WAKE_LOCK` line:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/androidMain/AndroidManifest.xml
git commit -m "feat: add POST_NOTIFICATIONS permission to manifest"
```

---

### Task 2: Add lifecycle-runtime-compose dependency

**Files:**
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Add to commonMain.dependencies**

In `shared/build.gradle.kts`, inside the `commonMain.dependencies { ... }` block, add after the `datastore` lines:

```kotlin
implementation(libs.androidx.lifecycle.runtime.compose)
```

The catalog alias `androidx-lifecycle-runtime-compose` maps to `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.1` — a KMP artifact that provides `LocalLifecycleOwner` and `repeatOnLifecycle` in commonMain.

- [ ] **Step 2: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/build.gradle.kts
git commit -m "feat: add lifecycle-runtime-compose dependency"
```

---

### Task 3: Add strings

**Files:**
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/composeResources/values-de/strings.xml`
- Modify: `shared/src/androidMain/res/values/strings.xml`
- Modify: `shared/src/androidMain/res/values-de/strings.xml`

- [ ] **Step 1: Add EN Compose Resource strings**

In `shared/src/commonMain/composeResources/values/strings.xml`, add before `</resources>`:

```xml
    <string name="onboarding_permissions_title">Background Operation</string>
    <string name="onboarding_permissions_desc">Transcriptions can take a few minutes depending on recording length. MolyEcho can continue working in the background – you\'ll be notified when the result is ready.</string>
    <string name="onboarding_permission_notifications_title">Notifications</string>
    <string name="onboarding_permission_notifications_desc">So you\'re informed when a transcription is complete.</string>
    <string name="onboarding_permission_battery_title">Battery Optimization</string>
    <string name="onboarding_permission_battery_desc">Prevents Android from stopping MolyEcho in the background.</string>
    <string name="onboarding_permission_allow">Allow</string>
    <string name="onboarding_permission_granted">Granted ✓</string>
    <string name="onboarding_permission_battery_settings">Settings</string>
    <string name="onboarding_permission_disabled">Disabled ✓</string>
```

Also update the **existing** `onboarding_models_subtitle` key:
```xml
<!-- OLD: -->
<string name="onboarding_models_subtitle">Choose the model that fits you best in Settings.</string>
<!-- NEW: -->
<string name="onboarding_models_subtitle">Choose your model right now – you\'ll be taken directly to the model selection.</string>
```

- [ ] **Step 2: Add DE Compose Resource strings**

In `shared/src/commonMain/composeResources/values-de/strings.xml`, add before `</resources>`:

```xml
    <string name="onboarding_permissions_title">Im Hintergrund aktiv</string>
    <string name="onboarding_permissions_desc">Transkriptionen können je nach Aufnahmelänge einige Minuten dauern. MolyEcho kann dabei im Hintergrund weiterarbeiten – du wirst benachrichtigt, sobald das Ergebnis bereit ist.</string>
    <string name="onboarding_permission_notifications_title">Benachrichtigungen</string>
    <string name="onboarding_permission_notifications_desc">Damit du informiert wirst, wenn eine Transkription abgeschlossen ist.</string>
    <string name="onboarding_permission_battery_title">Akku-Optimierung</string>
    <string name="onboarding_permission_battery_desc">Verhindert, dass Android MolyEcho im Hintergrund stoppt.</string>
    <string name="onboarding_permission_allow">Erlauben</string>
    <string name="onboarding_permission_granted">Erteilt ✓</string>
    <string name="onboarding_permission_battery_settings">Einstellungen</string>
    <string name="onboarding_permission_disabled">Deaktiviert ✓</string>
```

Also update the **existing** `onboarding_models_subtitle`:
```xml
<!-- OLD: -->
<string name="onboarding_models_subtitle">Wähle in den Einstellungen das Modell, das zu dir passt.</string>
<!-- NEW: -->
<string name="onboarding_models_subtitle">Wähle jetzt gleich dein Modell aus – du wirst direkt zur Modellauswahl weitergeleitet.</string>
```

- [ ] **Step 3: Add Android Resource strings for download notification**

In `shared/src/androidMain/res/values/strings.xml`, add before `</resources>`:

```xml
    <string name="notification_download_done_title">Model downloaded</string>
    <string name="notification_download_done_text">MolyEcho is ready for transcription</string>
    <string name="notification_download_done_channel_name">Download complete</string>
```

In `shared/src/androidMain/res/values-de/strings.xml`, add before `</resources>`:

```xml
    <string name="notification_download_done_title">Modell heruntergeladen</string>
    <string name="notification_download_done_text">MolyEcho ist bereit zur Transkription</string>
    <string name="notification_download_done_channel_name">Download abgeschlossen</string>
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/composeResources/values/strings.xml \
        shared/src/commonMain/composeResources/values-de/strings.xml \
        shared/src/androidMain/res/values/strings.xml \
        shared/src/androidMain/res/values-de/strings.xml
git commit -m "feat: add permission onboarding and download notification strings"
```

---

## Chunk 2: Permission Infrastructure

**Task order matters here:** create the concrete types before creating files that reference them.
Order: PermissionHandler (interface) → PermissionLauncherHolder → PermissionViewModel → PermissionHandlerProvider (actual) → DI + MainActivity

---

### Task 4: Create PermissionHandler interface (commonMain)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/module/notelycompose/permissions/PermissionHandler.kt`

- [ ] **Step 1: Create the interface**

```kotlin
package com.module.notelycompose.permissions

import kotlinx.coroutines.flow.StateFlow

interface PermissionHandler {
    val isNotificationGranted: StateFlow<Boolean>
    val isBatteryOptimizationDisabled: StateFlow<Boolean>
    fun requestNotificationPermission()
    fun openBatterySettings()
    fun refresh()
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/permissions/PermissionHandler.kt
git commit -m "feat: add PermissionHandler interface"
```

---

### Task 5: Create PermissionLauncherHolder (androidMain)

**Files:**
- Create: `shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionLauncherHolder.kt`

Same pattern as `FileSaverLauncherHolder` in the project root androidMain package.

- [ ] **Step 1: Create the holder**

```kotlin
package com.module.notelycompose.permissions

import androidx.activity.result.ActivityResultLauncher

class PermissionLauncherHolder {
    var notificationLauncher: ActivityResultLauncher<String>? = null
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionLauncherHolder.kt
git commit -m "feat: add PermissionLauncherHolder"
```

---

### Task 6: Create PermissionViewModel (androidMain)

**Files:**
- Create: `shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionViewModel.kt`

- [ ] **Step 1: Create the ViewModel**

```kotlin
package com.module.notelycompose.permissions

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PermissionViewModel(
    private val app: Application,
    private val launcherHolder: PermissionLauncherHolder
) : ViewModel(), PermissionHandler {

    private val _isNotificationGranted = MutableStateFlow(checkNotificationGranted())
    override val isNotificationGranted: StateFlow<Boolean> = _isNotificationGranted

    private val _isBatteryOptimizationDisabled = MutableStateFlow(checkBatteryOptimizationDisabled())
    override val isBatteryOptimizationDisabled: StateFlow<Boolean> = _isBatteryOptimizationDisabled

    override fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcherHolder.notificationLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below API 33 notifications are granted by default — update state immediately
            _isNotificationGranted.value = true
        }
    }

    override fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        app.startActivity(intent)
    }

    override fun refresh() {
        _isNotificationGranted.value = checkNotificationGranted()
        _isBatteryOptimizationDisabled.value = checkBatteryOptimizationDisabled()
    }

    private fun checkNotificationGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkBatteryOptimizationDisabled(): Boolean {
        val pm = app.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(app.packageName)
    }
}
```

- [ ] **Step 2: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionViewModel.kt
git commit -m "feat: add PermissionViewModel implementing PermissionHandler"
```

---

### Task 7: Create expect/actual PermissionHandlerProvider

**Files:**
- Create: `shared/src/commonMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.kt`
- Create: `shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.android.kt`

`PermissionViewModel` now exists (Task 6), so the actual can reference it safely.

- [ ] **Step 1: Create commonMain expect**

```kotlin
// shared/src/commonMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.kt
package com.module.notelycompose.permissions

import androidx.compose.runtime.Composable

@Composable
expect fun rememberPermissionHandler(): PermissionHandler
```

- [ ] **Step 2: Create androidMain actual**

```kotlin
// shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.android.kt
package com.module.notelycompose.permissions

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel

@Composable
actual fun rememberPermissionHandler(): PermissionHandler =
    koinViewModel<PermissionViewModel>()
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.kt \
        shared/src/androidMain/kotlin/com/module/notelycompose/permissions/PermissionHandlerProvider.android.kt
git commit -m "feat: add expect/actual rememberPermissionHandler composable"
```

---

### Task 8: Register PermissionViewModel in Koin + wire MainActivity

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/di/Modules.android.kt`
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/MainActivity.kt`

- [ ] **Step 1: Add Koin registrations**

In `shared/src/androidMain/kotlin/com/module/notelycompose/di/Modules.android.kt`:

Add imports (after the existing imports):
```kotlin
import com.module.notelycompose.permissions.PermissionLauncherHolder
import com.module.notelycompose.permissions.PermissionViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
```

Inside `actual val platformModule = module { ... }`, add after the `FolderPickerHandler` block:
```kotlin
    // permissions
    single { PermissionLauncherHolder() }
    viewModel { PermissionViewModel(get(), get()) }
```

Note: `viewModel { }` (with lambda) is from `org.koin.androidx.viewmodel.dsl.viewModel` — different from `viewModelOf` which is used for zero-arg constructors. Both are valid Koin DSL.

- [ ] **Step 2: Wire PermissionLauncherHolder in MainActivity**

In `shared/src/androidMain/kotlin/com/module/notelycompose/MainActivity.kt`:

Add import (the `ActivityResultContracts` import already exists — only add what is missing):
```kotlin
import com.module.notelycompose.permissions.PermissionLauncherHolder
```

Add field after the existing launcher holder fields (`fileSaverLauncherHolder`, `folderPickerLauncherHolder`):
```kotlin
private val permissionLauncherHolder by inject<PermissionLauncherHolder>()
```

Add a new private method:
```kotlin
private fun setupNotificationPermissionLauncher() {
    permissionLauncherHolder.notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Result handled via PermissionViewModel.refresh() on ON_RESUME
        }
}
```

Call it in `onCreate`, after `setupFolderPickerLauncher()`:
```kotlin
setupNotificationPermissionLauncher()
```

- [ ] **Step 3: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/androidMain/kotlin/com/module/notelycompose/di/Modules.android.kt \
        shared/src/androidMain/kotlin/com/module/notelycompose/MainActivity.kt
git commit -m "feat: register PermissionViewModel in Koin and wire launcher in MainActivity"
```

---

## Chunk 3: Onboarding UI

### Task 9: Add PermissionsOnboardingPage + PermissionCard composables

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/ui/OnboardingWalkthrough.kt`

- [ ] **Step 1: Add new imports**

At the top of `OnboardingWalkthrough.kt`, add these imports (after the existing import block):

```kotlin
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
```

Note: Do NOT add `import kotlinx.coroutines.flow.collectAsState` — use `androidx.compose.runtime.collectAsState` which is already available via existing compose imports.

- [ ] **Step 2: Add PermissionCard composable**

Add this new composable after `ModelInfoCard` (around line 354):

```kotlin
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
```

- [ ] **Step 3: Add PermissionsOnboardingPage composable**

Add this after `PermissionCard`:

```kotlin
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
```

- [ ] **Step 4: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/ui/OnboardingWalkthrough.kt
git commit -m "feat: add PermissionCard and PermissionsOnboardingPage composables"
```

---

### Task 10: Wire PermissionsOnboardingPage into OnboardingWalkthrough pager

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/ui/OnboardingWalkthrough.kt`

- [ ] **Step 1: Update OnboardingWalkthrough signature**

Change the function signature — find:

```kotlin
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    platformState: PlatformUiState
)
```

Replace with:

```kotlin
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    onFinishToModelSelection: () -> Unit = {},
    platformState: PlatformUiState,
    permissionHandler: PermissionHandler? = null
)
```

- [ ] **Step 2: Change pageCount from 4 to 5**

Find:
```kotlin
val pageCount = 4
```

Replace with:
```kotlin
val pageCount = 5
```

- [ ] **Step 3: Update the pager when-block**

Find:

```kotlin
when (page) {
    0 -> MolyEchoWelcomePage()
    pageCount - 1 -> ModelOverviewPage()
    else -> OnboardingPageContent(
        page = featurePages[page - 1],
        isTablet = platformState.isTablet,
        isAndroid = platformState.isAndroid
    )
}
```

Replace with:

```kotlin
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
```

Note: `featurePages` has 2 entries (indices 0 and 1) covering pages 1 and 2. Page 3 is the permissions page — the `else` branch is never reached for page=3. The fallback when `permissionHandler == null` shows `ModelOverviewPage()` instead of crashing — **do not** use `featurePages[page - 1]` here as `featurePages[2]` would be an `IndexOutOfBoundsException`.

- [ ] **Step 4: Update last-page button to call onFinishToModelSelection**

Find:

```kotlin
if (pagerState.currentPage == pageCount - 1) {
    onFinish()
} else {
```

Replace with:

```kotlin
if (pagerState.currentPage == pageCount - 1) {
    onFinishToModelSelection()
} else {
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/onboarding/ui/OnboardingWalkthrough.kt
git commit -m "feat: wire PermissionsOnboardingPage into onboarding pager (page 3 of 5)"
```

---

### Task 11: Update App.kt — permission handler, navigation, route move

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/module/notelycompose/App.kt`

- [ ] **Step 1: Add import**

Add at the top of `App.kt`:

```kotlin
import com.module.notelycompose.permissions.rememberPermissionHandler
```

- [ ] **Step 2: Add permissionHandler and flag inside App composable**

After `val platformUiState by platformViewModel.state.collectAsState()`, add:

```kotlin
val permissionHandler = rememberPermissionHandler()
var openModelSelectionOnLaunch by remember { mutableStateOf(false) }
```

- [ ] **Step 3: Update OnboardingWalkthrough call**

Find:

```kotlin
is OnboardingState.NotCompleted -> {
    OnboardingWalkthrough(
        onFinish = {
            viewmodel.onCompleteOnboarding()
        },
        platformState = platformUiState
    )
}
```

Replace with:

```kotlin
is OnboardingState.NotCompleted -> {
    OnboardingWalkthrough(
        onFinish = {
            viewmodel.onCompleteOnboarding()
        },
        onFinishToModelSelection = {
            openModelSelectionOnLaunch = true
            viewmodel.onCompleteOnboarding()
        },
        platformState = platformUiState,
        permissionHandler = permissionHandler
    )
}
```

- [ ] **Step 4: Pass openModelSelection to NoteAppRoot**

Find:

```kotlin
is OnboardingState.Completed -> NoteAppRoot(platformUiState)
```

Replace with:

```kotlin
is OnboardingState.Completed -> NoteAppRoot(platformUiState, openModelSelection = openModelSelectionOnLaunch)
```

- [ ] **Step 5: Update NoteAppRoot signature and add LaunchedEffect**

Find:

```kotlin
@Composable
fun NoteAppRoot(platformUiState: PlatformUiState) {
    val navController = rememberNavController()
```

Replace with:

```kotlin
@Composable
fun NoteAppRoot(platformUiState: PlatformUiState, openModelSelection: Boolean = false) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        if (openModelSelection) {
            navController.navigate(Routes.LanguageModelSelection)
        }
    }
```

- [ ] **Step 6: Move LanguageModelSelection + LanguageModelExplanation to Home graph**

In `NoteAppRoot`, find these two blocks inside `navigation<Routes.DetailsGraph> { ... }`:

```kotlin
composableWithVerticalSlide<Routes.LanguageModelSelection> {
    ModelSelectionScreen(
        navigateBack = { navController.popBackStack() },
        navigateToModelExplanation = { navController.navigateSingleTop(Routes.LanguageModelExplanation) }
    )
}
composableWithVerticalSlide<Routes.LanguageModelExplanation> {
    ModelExplanationScreen(
        navigateBack = { navController.popBackStack() }
    )
}
```

Remove them from `navigation<Routes.DetailsGraph>` and paste them inside `navigation<Routes.Home> { ... }`, directly after the `composableWithVerticalSlide<Routes.SettingsText>` block.

- [ ] **Step 7: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/module/notelycompose/App.kt
git commit -m "feat: wire permission handler in onboarding, navigate to model selection after onboarding"
```

---

## Chunk 4: Download Completion Notification

### Task 12: Add download completion notification to Downloader

**Files:**
- Modify: `shared/src/androidMain/kotlin/com/module/notelycompose/platform/Downloader.android.kt`

- [ ] **Step 1: Add imports**

At the top of `Downloader.android.kt`, add after the existing imports:

```kotlin
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.module.notelycompose.MainActivity
import de.molyecho.notlyvoice.android.R
```

- [ ] **Step 2: Add constants to companion object**

In the `companion object` at the bottom of `Downloader`, add:

```kotlin
private const val CHANNEL_DOWNLOAD_DONE_ID = "download_done_channel"
private const val NOTIFICATION_DOWNLOAD_DONE_ID = 4
```

- [ ] **Step 3: Add postDownloadCompleteNotification method**

Add this private method inside the `Downloader` class, after `getErrorTextFromReason`:

```kotlin
private fun postDownloadCompleteNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            mainContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }

    val nm = mainContext.getSystemService(NotificationManager::class.java)

    val channel = NotificationChannel(
        CHANNEL_DOWNLOAD_DONE_ID,
        mainContext.getString(R.string.notification_download_done_channel_name),
        NotificationManager.IMPORTANCE_DEFAULT
    )
    nm.createNotificationChannel(channel)

    val pendingIntent = PendingIntent.getActivity(
        mainContext,
        0,
        Intent(mainContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    val notification = NotificationCompat.Builder(mainContext, CHANNEL_DOWNLOAD_DONE_ID)
        .setContentTitle(mainContext.getString(R.string.notification_download_done_title))
        .setContentText(mainContext.getString(R.string.notification_download_done_text))
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    nm.notify(NOTIFICATION_DOWNLOAD_DONE_ID, notification)
}
```

- [ ] **Step 4: Call postDownloadCompleteNotification on success**

In `trackDownloadProgress`, find:

```kotlin
when (finalStatus) {
    DownloadManager.STATUS_SUCCESSFUL -> onSuccess()
```

Replace with:

```kotlin
when (finalStatus) {
    DownloadManager.STATUS_SUCCESSFUL -> {
        postDownloadCompleteNotification()
        onSuccess()
    }
```

- [ ] **Step 5: Verify build**

```bash
./gradlew :shared:assembleDebug
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add shared/src/androidMain/kotlin/com/module/notelycompose/platform/Downloader.android.kt
git commit -m "feat: post download completion notification when model download finishes"
```

---

## Final Verification

- [ ] **Run full release build**

```bash
./gradlew :shared:assembleRelease
```
Expected: BUILD SUCCESSFUL

- [ ] **Manual smoke test checklist**

Install debug APK on Android 13+ device (clear app data to re-trigger onboarding):

1. Onboarding shows 5 pages: Welcome → Feature1 → Feature2 → Berechtigungen → Modellübersicht
2. On page 4 (Berechtigungen): tap "Erlauben" → system notification dialog appears → grant → button shows "Erteilt ✓" after returning
3. Tap "Einstellungen" → battery settings opens → navigate back → button reflects state
4. "Weiter" on Berechtigungen-page always works (optional permissions)
5. On page 5 (Modellübersicht): tap "Los geht's" → app opens with ModelSelection on top of NoteList
6. Press Back from ModelSelection → lands on NoteList (not NoteDetailScreen)
7. Re-install, "Überspringen" → lands directly on NoteList without ModelSelection
8. From Settings → Modellauswahl → trigger a model download → after download completes: notification "Modell heruntergeladen" appears

- [ ] **Final commit (if any uncommitted files remain)**

```bash
git add -A
git commit -m "feat: background operation — permission onboarding, model selection navigation, download notification"
```
