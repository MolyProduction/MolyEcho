# Background Operation & Notifications – Design Spec

**Datum:** 2026-03-15
**Branch:** german-fork
**Status:** Approved

---

## Übersicht

Diese Spec beschreibt vier zusammenhängende Änderungen, die MolyEcho besser für den Hintergrundbetrieb optimieren:

1. Android-Permissions für Notifications und Akku-Einstellungen
2. Neue Onboarding-Seite zur Berechtigungsanfrage
3. Angepasste letzte Onboarding-Seite mit direkter Navigation zur Modellauswahl
4. Download-Abschluss-Notification

---

## 1. Permissions & Manifest

### Neue Einträge in `shared/src/androidMain/AndroidManifest.xml`

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` wird **nicht** ins Manifest aufgenommen (Google-Play-Policy-Risiko für Nicht-VoIP/Alarm-Apps). Stattdessen öffnet der Akku-Button die allgemeinen Akku-Einstellungen via `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` – das erfordert keine Permission-Deklaration.

### Architektur: `PermissionHandler` Interface + `PermissionViewModel`

`commonMain` kann keine `androidMain`-Typen referenzieren. Lösung: Interface-Brücke.

**`PermissionHandler`** (neu, `commonMain`):
```kotlin
interface PermissionHandler {
    val isNotificationGranted: StateFlow<Boolean>
    val isBatteryOptimizationDisabled: StateFlow<Boolean>
    fun requestNotificationPermission()
    fun openBatterySettings()
    fun refresh()
}
```

**`PermissionLauncherHolder`** (neu, `androidMain`):
```kotlin
class PermissionLauncherHolder {
    var notificationLauncher: ActivityResultLauncher<String>? = null
}
```
Registriert in Koin als `single`. In `MainActivity.kt` wird `registerForActivityResult(ActivityResultContracts.RequestPermission())` aufgerufen und in `launcherHolder.notificationLauncher` gespeichert (analog zu `FileSaverLauncherHolder`).

**`PermissionViewModel`** (neu, `androidMain`), implementiert `PermissionHandler`:
- `val isNotificationGranted: StateFlow<Boolean>` – geprüft via `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)`
- `val isBatteryOptimizationDisabled: StateFlow<Boolean>` – geprüft via `PowerManager.isIgnoringBatteryOptimizations(packageName)`
- `fun requestNotificationPermission()` – ruft `launcherHolder.notificationLauncher?.launch(POST_NOTIFICATIONS)` auf
- `fun openBatterySettings()` – ruft `context.startActivity(Intent(ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))` auf
- `fun refresh()` – re-liest beide States als neue Werte in die MutableStateFlows

**Koin-Registrierung** (im Android-DI-Modul):
```kotlin
single { PermissionLauncherHolder() }
viewModel { PermissionViewModel(get(), get()) }  // registriert als PermissionViewModel::class
```

**Composable-Brücke:** `koinViewModel<Interface>()` ist zur Laufzeit ungültig, da `ViewModelProvider` keine Interfaces instanziieren kann. Lösung: `expect`/`actual`-Composable-Funktion:

```kotlin
// commonMain: PermissionHandlerProvider.kt
@Composable
expect fun rememberPermissionHandler(): PermissionHandler
```

```kotlin
// androidMain: PermissionHandlerProvider.android.kt
@Composable
actual fun rememberPermissionHandler(): PermissionHandler =
    koinViewModel<PermissionViewModel>()
```

`App.kt` (commonMain) ruft nur `rememberPermissionHandler()` auf – kein androidMain-Import nötig. Der `expect`/`actual`-Overhead ist minimal (2 kleine Dateien).

**Lifecycle-Refresh:**
`PermissionsOnboardingPage` nutzt `repeatOnLifecycle` aus `lifecycle-runtime-compose`. Diese Bibliothek ist im Versions-Katalog (`libs.lifecycle.runtime.compose`) vorhanden, aber noch **nicht** in `shared/build.gradle.kts` eingetragen. **Eine neue Abhängigkeit ist nötig:**

```kotlin
// shared/build.gradle.kts – commonMain.dependencies
implementation(libs.androidx.lifecycle.runtime.compose)
```
```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
LaunchedEffect(lifecycleOwner) {
    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        permissionHandler.refresh()
    }
}
```
Damit aktualisiert sich der Button-Zustand jedes Mal wenn der Nutzer von den System-Einstellungen zurückkommt.

---

## 2. Neue Onboarding-Berechtigungsseite (Seite 3)

### Position im Flow

```
Seite 0: Welcome (MolyEchoWelcomePage)
Seite 1: Feature 1 (OnboardingPageContent)
Seite 2: Feature 2 (OnboardingPageContent)
Seite 3: Berechtigungen (PermissionsOnboardingPage) ← NEU
Seite 4: Modellübersicht (ModelOverviewPage)
```

`pageCount` wird von 4 auf 5 erhöht. Im `when`-Block in `HorizontalPager` wird der neue Case `3 -> PermissionsOnboardingPage(...)` ergänzt; `pageCount - 1` bleibt weiterhin `ModelOverviewPage`.

### UI-Aufbau (`PermissionsOnboardingPage`)

Implementiert in `OnboardingWalkthrough.kt` (commonMain). Der `PermissionViewModel` wird als Parameter hereingegeben (injiziert von `App.kt`/`OnboardingWalkthrough`-Aufrufstelle). Layout von oben nach unten, scrollbar:

- **Icon:** MolyEcho-App-Icon (`Res.drawable.icon`), 88dp, ContentScale.Fit
- **Titel:** „Im Hintergrund aktiv" (28sp, Bold, PoppingsFontFamily, zentriert)
- **Erklärtext:** (16sp, Normal, `Color(0xFF555555)`, zentriert)
- **Karte 1 – Benachrichtigungen** (angepasste `PermissionCard`-Composable, ähnlich `ModelInfoCard`):
  - Linker Balken: MolyGreen
  - Titel: „Benachrichtigungen" (SemiBold, 16sp)
  - Beschreibung: „Damit du informiert wirst, wenn eine Transkription abgeschlossen ist."
  - Button rechts: `OutlinedButton` – „Erlauben" (grün) wenn `!isNotificationGranted`, „Erteilt ✓" (ausgegraut, `enabled = false`) wenn gewährt
  - `onClick = { permissionViewModel.requestNotificationPermission() }`
- **Karte 2 – Akku-Optimierung** (gleiche Struktur):
  - Titel: „Akku-Optimierung" (SemiBold, 16sp)
  - Beschreibung: „Verhindert, dass Android MolyEcho im Hintergrund stoppt."
  - Button rechts: „Einstellungen" (grün) wenn `!isBatteryOptimizationDisabled`, „Deaktiviert ✓" (ausgegraut) wenn whitegelistet
  - `onClick = { permissionViewModel.openBatterySettings() }`
- Lifecycle-Refresh via `LaunchedEffect` (s. Abschnitt 1)

### Strings (Compose Resources: `values/strings.xml` + `values-de/strings.xml`)

| Key | EN | DE |
|-----|----|----|
| `onboarding_permissions_title` | „Background Operation" | „Im Hintergrund aktiv" |
| `onboarding_permissions_desc` | „Transcriptions can take a few minutes depending on recording length. MolyEcho can continue working in the background – you'll be notified when the result is ready." | „Transkriptionen können je nach Aufnahmelänge einige Minuten dauern. MolyEcho kann dabei im Hintergrund weiterarbeiten – du wirst benachrichtigt, sobald das Ergebnis bereit ist." |
| `onboarding_permission_notifications_title` | „Notifications" | „Benachrichtigungen" |
| `onboarding_permission_notifications_desc` | „So you're informed when a transcription is complete." | „Damit du informiert wirst, wenn eine Transkription abgeschlossen ist." |
| `onboarding_permission_battery_title` | „Battery Optimization" | „Akku-Optimierung" |
| `onboarding_permission_battery_desc` | „Prevents Android from stopping MolyEcho in the background." | „Verhindert, dass Android MolyEcho im Hintergrund stoppt." |
| `onboarding_permission_allow` | „Allow" | „Erlauben" |
| `onboarding_permission_granted` | „Granted ✓" | „Erteilt ✓" |
| `onboarding_permission_battery_settings` | „Settings" | „Einstellungen" |
| `onboarding_permission_disabled` | „Disabled ✓" | „Deaktiviert ✓" |

### Verhalten

- Der „Weiter"-Button am unteren Rand ist immer aktiv – Berechtigungen sind optional
- Tippt der Nutzer „Erlauben": System-Dialog erscheint; nach Rückkehr (`ON_RESUME`) ruft `refresh()` den State neu ab
- Tippt der Nutzer „Einstellungen": `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` öffnet sich; nach Rückkehr (`ON_RESUME`) ruft `refresh()` den State neu ab

---

## 3. Letzte Onboarding-Seite – Navigation zur Modellauswahl

### Textänderung in `onboarding_models_subtitle` (Compose Resources)

| | Alt | Neu |
|---|---|---|
| DE | „Wähle in den Einstellungen das Modell, das zu dir passt." | „Wähle jetzt gleich dein Modell aus – du wirst direkt zur Modellauswahl weitergeleitet." |
| EN | „Choose the model that fits you best in Settings." | „Choose your model right now – you'll be taken directly to the model selection." |

### Neuer Callback in `OnboardingWalkthrough`

```kotlin
fun OnboardingWalkthrough(
    onFinish: () -> Unit = {},
    onFinishToModelSelection: () -> Unit = {},  // NEU
    ...
)
```

Der „Los geht's"-Button auf Seite 4 (letzte Seite, `pageCount - 1`) ruft `onFinishToModelSelection()` auf.
„Überspringen"-Button und Wischen rufen weiterhin `onFinish()`.

### Flag in `App.kt`

```kotlin
var openModelSelectionOnLaunch by remember { mutableStateOf(false) }

OnboardingWalkthrough(
    onFinish = { viewmodel.onCompleteOnboarding() },
    onFinishToModelSelection = {
        openModelSelectionOnLaunch = true
        viewmodel.onCompleteOnboarding()
    },
    ...
)

is OnboardingState.Completed ->
    NoteAppRoot(platformUiState, openModelSelection = openModelSelectionOnLaunch)
```

### `Routes.LanguageModelSelection` in `Home`-Graph verschieben

**Problem:** `Routes.LanguageModelSelection` ist aktuell in `navigation<Routes.DetailsGraph>` registriert. `DetailsGraph` hat `startDestination = Routes.Details`, weshalb ein direktes `navigate(Routes.LanguageModelSelection)` beim Back-Drücken zu `NoteDetailScreen(null)` führen würde.

**Lösung:** `Routes.LanguageModelSelection` und `Routes.LanguageModelExplanation` werden aus `navigation<Routes.DetailsGraph>` in `navigation<Routes.Home>` verschoben. Semantisch sind das Settings-Screens, nicht Detailansichten. Bestehende Navigation aus `SettingsScreen` (`navigateToModelSelection`) funktioniert weiterhin, da beide im gleichen NavHost-Scope sind.

### Navigation in `NoteAppRoot`

```kotlin
fun NoteAppRoot(platformUiState: PlatformUiState, openModelSelection: Boolean = false) {
    val navController = rememberNavController()
    LaunchedEffect(Unit) {
        if (openModelSelection) {
            navController.navigate(Routes.LanguageModelSelection)
        }
    }
    ...
}
```

`LaunchedEffect(Unit)` statt `LaunchedEffect(openModelSelection)` – der Effekt soll nur einmal beim ersten Composable-Start laufen, nicht bei späteren Recompositionen. Back-Stack-Ergebnis: NoteList → ModelSelection. Zurück landet auf der Notizliste.

---

## 4. Download-Abschluss-Notification

### Implementierungsort

`shared/src/androidMain/kotlin/.../platform/Downloader.android.kt`

### Neue Methode `postDownloadCompleteNotification()`

Aufgerufen wenn `finalStatus == DownloadManager.STATUS_SUCCESSFUL` in `trackDownloadProgress`.

Ablauf:
1. Notification-Channel `download_done_channel` erstellen (Importance: DEFAULT)
2. `PendingIntent` zur `MainActivity` (analog zu `TranscriptionForegroundService`)
3. Notification mit Auto-Cancel, Titel + Text aus Android-Ressourcen (`R.string.*`)
4. Auf Android 13+: `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)` prüfen – bei Fehlen wird die Notification still übersprungen (kein Absturz)

### Notification-IDs (vollständige Übersicht)

| ID | Verwendung |
|----|------------|
| 1 | Aufnahme läuft (AudioRecordingService) |
| 2 | Transkription läuft (TranscriptionForegroundService) |
| 3 | Transkription abgeschlossen (TranscriptionForegroundService) |
| 4 | Download abgeschlossen (Downloader) ← NEU |

### Neue Strings in `androidMain/res/values/strings.xml` und `values-de/strings.xml`

| Key | EN | DE |
|-----|----|----|
| `notification_download_done_title` | „Model downloaded" | „Modell heruntergeladen" |
| `notification_download_done_text` | „MolyEcho is ready for transcription" | „MolyEcho ist bereit zur Transkription" |
| `notification_download_done_channel_name` | „Download complete" | „Download abgeschlossen" |

---

## Dateien die geändert werden

| Datei | Änderung |
|-------|----------|
| `shared/src/androidMain/AndroidManifest.xml` | +1 permission (`POST_NOTIFICATIONS`) |
| `shared/src/androidMain/kotlin/.../MainActivity.kt` | `PermissionLauncherHolder` initialisieren (Notification-Launcher registrieren) |
| `shared/build.gradle.kts` | +`libs.androidx.lifecycle.runtime.compose` in `commonMain.dependencies` |
| `shared/src/commonMain/kotlin/.../permissions/PermissionHandler.kt` | NEU: `PermissionHandler`-Interface |
| `shared/src/commonMain/kotlin/.../permissions/PermissionHandlerProvider.kt` | NEU: `@Composable expect fun rememberPermissionHandler(): PermissionHandler` |
| `shared/src/androidMain/kotlin/.../permissions/PermissionHandlerProvider.android.kt` | NEU: `actual fun rememberPermissionHandler()` → `koinViewModel<PermissionViewModel>()` |
| `shared/src/androidMain/kotlin/.../permissions/PermissionLauncherHolder.kt` | NEU: Holder für `ActivityResultLauncher<String>` |
| `shared/src/androidMain/kotlin/.../permissions/PermissionViewModel.kt` | NEU: implementiert `PermissionHandler`, StateFlows, `requestNotificationPermission()`, `openBatterySettings()`, `refresh()` |
| `shared/src/androidMain/kotlin/.../di/AppModule.kt` | Koin: `PermissionLauncherHolder` als single, `PermissionViewModel` als viewModel |
| `shared/src/androidMain/kotlin/.../MainActivity.kt` | `PermissionLauncherHolder` initialisieren (Notification-Launcher registrieren) |
| `shared/src/commonMain/kotlin/.../App.kt` | `rememberPermissionHandler()` aufrufen, Flag-Logik, `NoteAppRoot`-Parameter; `Routes.LanguageModelSelection` + `LanguageModelExplanation` in `Home`-Graph verschieben |
| `shared/src/commonMain/kotlin/.../onboarding/ui/OnboardingWalkthrough.kt` | pageCount 4→5, `PermissionsOnboardingPage` + `PermissionCard`, neuer `onFinishToModelSelection`-Callback |
| `shared/src/commonMain/composeResources/values/strings.xml` | +10 neue Strings, 1 geänderter String (`onboarding_models_subtitle`) |
| `shared/src/commonMain/composeResources/values-de/strings.xml` | +10 neue Strings, 1 geänderter String |
| `shared/src/androidMain/res/values/strings.xml` | +3 neue Notification-Strings |
| `shared/src/androidMain/res/values-de/strings.xml` | +3 neue Notification-Strings |
| `shared/src/androidMain/kotlin/.../platform/Downloader.android.kt` | +`postDownloadCompleteNotification()`, Channel-Erstellung |

---

## Nicht im Scope

- iOS-spezifische Anpassungen (MolyEcho ist reine Android-App)
- Änderungen am bestehenden Transkriptions-Notification-Flow
- Persistierung des Permission-Status in DataStore
- In-App-Permission-Rationale-Dialog – System-Dialog reicht
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Permission (Play-Store-Risiko; allgemeine Akku-Einstellungen sind ausreichend)
