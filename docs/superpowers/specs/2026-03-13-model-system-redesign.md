# Model System Redesign — Remove Bundled Tiny Model

**Date:** 2026-03-13
**Branch:** german-fork
**Scope:** Model data layer, startup logic, model selection UI, string resources, asset removal

---

## Problem

The app currently ships with a ~75 MB German whisper model (`ggml-tiny-german.bin`) bundled inside the APK. This inflates the download size and is no longer the desired default. The redesign:

1. Promotes the q5_0 quantized model (574 MB) to be the new **"Schnell"** standard — downloaded on demand at first transcription attempt.
2. Restores the full-precision cstr model (1.62 GB) as the **"Genau"** option in Settings.
3. Removes all bundled-model plumbing (asset extraction on startup, DataStore tracking key).
4. Updates onboarding text to reflect that an internet connection is required on first use.

---

## Architecture

No structural changes to the model pipeline. `ModelDownloaderViewModel.checkTranscriptionAvailability()` already handles downloadable models correctly:

- Model absent + `url != null` → emit `AskForUserAcceptance()` (shows `DownloaderDialog` with progress bar + MB counter)
- Model present → emit `ModelsAreReady()`

The `url == null` branch becomes permanently unreachable for German models after this change — no code change required there. `Transcriber.extractFromAssets()` catches exceptions silently, so it is safe when no asset exists.

---

## Behavioral Change for Existing Users

Users who previously had `model_selection = 0` (the old "Deutsch – Schnell" / bundled tiny) stored in DataStore will now have that value resolve to the q5_0 model (new "Schnell"). Since q5_0 is not bundled, `checkTranscriptionAvailability()` will show the download dialog on their next transcription attempt. **This is the accepted behavior** — instant offline transcription is no longer supported.

---

## Files Changed

### 1. `shared/src/commonMain/kotlin/.../modelDownloader/ModelSelection.kt`

**Step A — Add the cstr "Genau" model** (currently absent from `models[]`):
Add `ggml-large-v3-turbo-german.bin` (1.62 GB, cstr URL) as a new entry.

**Step B — Remove the bundled tiny model** (was index 3, `url = null`).

**Step C — Remap constants:**
- `STANDARD_MODEL_SELECTION (0)` → q5_0 model ("Schnell", downloadable)
- `OPTIMIZED_MODEL_SELECTION (1)` → cstr model ("Genau", downloadable)
- Update inline comments on both constants (currently stale: "German Quick (bundled tiny)" / "German Accurate (turbo)")

**Step D — Update methods:**
- `getDefaultTranscriptionModel()` → returns q5_0 model (now index 3 after removal of bundled tiny)
- `getSelectedModel()` `else` branch → returns q5_0 model (was bundled tiny)
- `getSelectedModel()` `OPTIMIZED_MODEL_SELECTION -> models[4]` line stays at index 4 numerically unchanged — but its effective target shifts from q5_0 (old index 4) to cstr (new index 4 after Step A). Verify this during implementation.

**Step E — Remove `BUNDLED_GERMAN_MODEL_FILENAME` constant.**

**Final `models[]` after change:**

| Index | File | Size | URL repo |
|-------|------|------|----------|
| 0 | ggml-base-en.bin | 142 MB | ggerganov/whisper.cpp |
| 1 | ggml-small.bin | 465 MB | ggerganov/whisper.cpp |
| 2 | ggml-base-hi.bin | 140 MB | khidrew/whisper-base-hindi-ggml |
| 3 | ggml-large-v3-turbo-german-q5_0.bin | 574 MB | F1sk/whisper-large-v3-turbo-german-ggml-q5_0 |
| 4 | ggml-large-v3-turbo-german.bin | 1.62 GB | cstr/whisper-large-v3-turbo-german-ggml (URL: `.../ggml-model.bin`) |

> **Dependency note:** `ModelDownloaderViewModel` initialises `_uiState` with `modelSelection.getDefaultTranscriptionModel()`. Steps D and E above must be applied before any `ModelDownloaderViewModel` changes (though `ModelDownloaderViewModel` itself requires no code changes).

### 2. `shared/src/androidMain/kotlin/.../NoteApp.kt`

- Remove `extractBundledGermanModelIfNeeded()` method.
- Remove the `appScope` coroutine scope and its launch block.
- Remove import of `BUNDLED_GERMAN_MODEL_FILENAME`.

### 3. `shared/src/commonMain/kotlin/.../onboarding/data/PreferencesRepository.kt`

- Remove `KEY_BUNDLED_MODEL_VERSION` preferences key.
- Remove `BUNDLED_MODEL_VERSION` constant.
- Remove `isBundledModelExtracted()` and `setBundledModelExtracted()` methods.

### 4. `shared/src/commonMain/kotlin/.../notes/ui/settings/ModelSelectionScreen.kt`

**State variables:**
- Rename `turboReady` → `schnellReady`: checks `doesModelExists("ggml-large-v3-turbo-german-q5_0.bin")`
- Rename `turboSizeMB` → `schnellSizeMB`
- Add `genauReady`: checks `doesModelExists("ggml-large-v3-turbo-german.bin")`
- Add `genauSizeMB`: `getModelFileSizeBytes("ggml-large-v3-turbo-german.bin") / 1024 / 1024`

**German Quick card:**
- `statusReady = schnellReady` (remove hardcoded `true`)

**`ManageModelsSection` — signature and logic changes:**

Remove parameter: `turboReady`, `turboSizeMB`, `onDeleteTurbo`
Add parameters: `schnellReady`, `schnellSizeMB`, `onDeleteSchnell`, `genauReady`, `genauSizeMB`, `onDeleteGenau`

Inside the section:
- Remove the non-deletable bundled-model row entirely.
- Add deletable row for q5_0 ("Schnell") when `schnellReady`.
- Keep deletable row for cstr ("Genau") when `genauReady` (was `turboReady`).
- Keep deletable rows for multilingual models (unchanged).

`anyDeletable = schnellReady || genauReady || multiStandardReady || multiExtendedReady`

**Delete callbacks at call site:**
- `onDeleteSchnell`: `transcriber.deleteModel("ggml-large-v3-turbo-german-q5_0.bin")`, `schnellReady = false`, `schnellSizeMB = 0L`; if `currentMode == MODE_GERMAN_QUICK` → `setModelSelection(STANDARD_MODEL_SELECTION)` to trigger download dialog on next use (or `NO_MODEL_SELECTION` — either works; `STANDARD_MODEL_SELECTION` is cleaner as it preserves intent).
- `onDeleteGenau`: `transcriber.deleteModel("ggml-large-v3-turbo-german.bin")`, `genauReady = false`, `genauSizeMB = 0L`; if `currentMode == MODE_GERMAN_ACCURATE` → `setModelSelection(STANDARD_MODEL_SELECTION)`.

### 5. String resources

Both `values-de/strings.xml` and `values/strings.xml` require updates.

| Key | Old (DE) | New (DE) |
|-----|----------|----------|
| `speech_mode_german_quick_subtitle` | `Eingebettetes Modell · Sofort einsatzbereit · ~75 MB` | `~574 MB Download · Schnell & präzise` |
| `speech_mode_german_accurate_subtitle` | `Hohe Erkennungsqualität · Download ~574 MB` | `Höchste Genauigkeit · Download ~1,6 GB` |
| `onboarding_screen_three_title` | `Sofort\neinsatzbereit` | `Internet\nerforderlich` |
| `onboarding_screen_three_desc` | `Deutsches Sprachmodell ist eingebettet – Transkription ohne Download und ohne Internet` | `Das Sprachmodell (~574 MB) wird beim ersten Start automatisch heruntergeladen. Du benötigst eine aktive Internetverbindung.` |
| `onboarding_screen_four_desc` | `Lade das Deutsche Turbo-Modell in den Einstellungen für höchste Genauigkeit herunter` | `Für maximale Genauigkeit: Lade das Turbo-Modell (~1,6 GB) in den Einstellungen herunter` |

| Key | Old (EN) | New (EN) |
|-----|----------|----------|
| `speech_mode_german_quick_subtitle` | `Bundled model · Instant · ~75 MB` | `~574 MB download · Fast & precise` |
| `speech_mode_german_accurate_subtitle` | `High accuracy · Download ~574 MB` | `Highest accuracy · Download ~1.6 GB` |
| `onboarding_screen_three_title` | `Transcribe\nand Summarise` | `Internet\nRequired` |
| `onboarding_screen_three_desc` | `Convert voice notes to text and\nsummaries without internet` | `The speech model (~574 MB) is downloaded automatically on first launch. An active internet connection is required.` |
| `onboarding_screen_four_desc` | `Create and transcribe notes in\nyour preferred language` | `For maximum accuracy: download the Turbo model (~1.6 GB) in Settings` |

**Orphaned string:** `model_cannot_delete` (`"Eingebettet (kann nicht gelöscht werden)"`) becomes unused after the non-deletable bundled row is removed from `ManageModelsSection`. Leave it in place (no crash, no functional impact) unless a cleanup pass is desired.

### 6. `LanguageSelectionScreen.kt` (verify only — no code change)

Line 241 auto-sets `OPTIMIZED_MODEL_SELECTION` when the user selects Farsi. After the redesign `OPTIMIZED_MODEL_SELECTION` maps to the cstr model, but `getSelectedModel()` overrides this for Farsi anyway (`FARSI -> models.first { it.name == "ggml-small.bin" }`). No behavioral change. No code modification needed — just verify during review.

### 7. Asset removal

- Delete `shared/src/androidMain/assets/ggml-tiny-german.bin` (~75 MB).

---

## What Does NOT Change

- `TranscriptionViewModel`, `DownloaderDialog`, `ModelDownloaderViewModel` — no code changes.
- `Transcriber.android.kt` — `extractFromAssets()` fails silently when no asset exists. Safe.
- All UI layouts, navigation, themes, colors, iOS code.

---

## Constraints & Risks

- **Build size** decreases by ~75 MB (APK asset removed).
- **No ProGuard changes needed** — no new public classes introduced.
- **No manual steps** required beyond building and testing on a fresh install.
- **Existing-user migration** (`model_selection = 0`): accepted behavior — download dialog shown on next transcription attempt.
