# Changelog – MolyEcho

> Dieses Dokument listet alle Änderungen gegenüber dem Upstream-Projekt
> **[NotelyVoice](https://github.com/Notely-Voice/NotelyVoice)** auf.
>
> MolyEcho ist ein GPL-3.0-Fork von NotelyVoice.
> Originalwerk: Copyright (C) 2025 NotelyVoice ([Tosin Onikute](https://github.com/tosinonikute/NotelyVoice))

---

## Übersicht

MolyEcho basiert auf NotelyVoice und enthält **102 zusätzliche Commits** (11.–15. März 2026), die das Projekt in eine eigenständige, deutsch-optimierte Notiz- und Transkriptions-App verwandeln. Die Änderungen lassen sich in folgende Bereiche gliedern:

---

## 1. Projekt-Umbenennung & Branding

- Projektname von NotelyVoice zu **MolyEcho** geändert (`78c2545`, `dd2712b`, `dae3d09`)
- Package-Name für den deutschen Fork angepasst (`78c2545`)
- Eigene Branding-Assets hinzugefügt: App-Icon, Maskottchen, Logo (`c44e8b8`)
- README vollständig auf Deutsch neu geschrieben mit MolyEcho-Identität (`4ae23c7`, `07ad158`)
- README um App-Icon, Screenshots und vollständige Feature-Liste erweitert (`af4be52`, `ff0fb73`)
- NotelyVoice-Assets in der Doku durch MolyEcho-Branding ersetzt (`a5cd6f4`)
- GitHub Pages mit Hilfe-Inhalten und FAQ erstellt (`80c865a`, `086a34b`, `80434bc`)

## 2. Deutsche Lokalisierung

- Vollständige deutsche String-Übersetzungen (`971a134`)
- Deutsch als Standardsprache gesetzt (`08dc238`)
- Hartcodierte Strings durch Ressourcen-Referenzen ersetzt (`1f49830`)
- Onboarding- und Modell-Subtitel-Strings auf Deutsch aktualisiert (`6bb7060`)
- Deutsches First-Start-Erlebnis optimiert (`127d0c6`)

## 3. Sprachmodelle – Neugestaltung

### Neue deutsche Whisper-Modelle
- Deutsches Tiny-Whisper-Modell (~75 MB) gebündelt ins APK (`21a639f`)
- Download für großes deutsches Turbo-Modell von HuggingFace (`9fe1d9f`)
- Eigens quantisiertes Standardmodell (ggml-large-v3-turbo-german-q5_0, ~574 MB) dokumentiert (`81bb87c`)

### Modellauswahl-Redesign
- ModelSelection komplett neu geschrieben: Bundled/Base-EN entfernt, cstr-Genau-Modell ergänzt (`5fc7ac0`)
- Gebündeltes Modell-Startup-Extraction und zugehörige DataStore-Keys entfernt (`c7fe2f2`)
- ModelSelectionScreen: Schnell/Genau-Zustände, einzelne Mehrsprachig-Karte (`44d7cdb`)
- MULTILINGUAL_STANDARD auf erweitertes Modell umgemappt (`ca943fb`)
- Gebündeltes ggml-tiny-german.bin-Asset entfernt zugunsten reinem Download (~75 MB gespart) (`d6b342f`)
- Modell-Beschreibungen mit präziser Positionierung für alle 3 Modelle neu gestaltet (`3c62207`)
- Modell wird bei Auswahl in den Einstellungen sofort heruntergeladen (`2608fa4`)
- Hindi/Gujarati-Modell entfernt und zwei Dead-Code-Inkonsistenzen behoben (`a046591`, `1b3394f`)

### Design-Spezifikationen
- Model-System-Redesign-Spec und Implementierungsplan erstellt (`5ea1a5c`, `8100250`, `5217485`, `b93f3d1`)
- Konvertierungsanleitung für whisper-tiny-german-1224 GGML-Modell (`54ab84f`, `cdd8d61`)

## 4. Onboarding-Redesign

- Onboarding komplett neu gestaltet: 4 Seiten, grüner Akzent, Modellübersicht (`afa52c6`)
- Permissions-Onboarding als Seite 3 von 5 eingebaut (`212a28c`)
- PermissionCard- und PermissionsOnboardingPage-Composables (`e7cc4ea`)
- PermissionHandler-Interface, ViewModel und Launcher (`a3184d4`, `8e4ae55`, `aa9f46e`, `e662900`, `0f9998c`)
- Permissions in App.kt verdrahtet, Modell-Routes in Home-Graph verschoben (`93f3a20`)
- Logo/Titel-Überlappung auf Onboarding-Feature-Pages behoben (`d9093f1`, `486d7e0`)

## 5. UI-Anpassungen & Theming

### Akzentfarben-System
- AccentTheme-Enum, DataStore-Key und Theme-Wiring hinzugefügt (`3f53557`)
- Akzentfarben-Picker in den Einstellungen (`4706c07`)
- Border-Rendering für AccentColorOption korrigiert (`d814539`)
- Schriftgrößen-Scope, Bold-Titel und Akzentfarben-Halo angepasst (`705c743`)

### Screen-Updates für MolyEcho
- App-Theme und Onboarding aktualisiert (`2aa8853`)
- Notiz-Detail-UI aktualisiert (`225fb37`)
- Notiz-Listen-UI aktualisiert (`c0c1069`)
- Einstellungen und Teilen-UI aktualisiert (`816ca86`)
- Audio-, Export-, Transkriptions- und Downloader-UI aktualisiert (`ac8c87b`)
- Android-Plattform-Code aktualisiert (`f02fa06`)

## 6. Transkriptions-Engine – Bugfixes & Performance

### Speichermanagement
- Whisper-Modell nach 10 Min. Inaktivität automatisch freigeben (`29eb83f`)
- Geladenes Whisper-Modell cachen, alten Context bei Wechsel freigeben (`8b1f541`)
- WhisperContext.release(): Executor herunterfahren gegen Thread-Leak (`0f9bcb6`)
- WhisperContext in TranscriptionViewModel.onCleared() freigeben (`725086a`)

### Performance
- Chunk-Overlap von 10 % auf 20 % erhöht für bessere Transkription an Grenzen (`a110456`)
- CPU-Thread-Count in WhisperCpuConfig cachen statt /proc/cpuinfo wiederholt zu lesen (`de14b5c`)
- Flash Attention, Model-Loading-Performance und Crash-Fix (`95d2548`)

### Bugfixes
- Cursor-Leak im Downloader mit .use{}-Blöcken verhindert (`afe2e1f`)
- GlobalScope- und MainScope-Leaks in ExportSelectionInteractorImpl ersetzt (`1b4beeb`)
- Inaktivitäts-Timer-Race in initialize() behoben (`8cf6b00`)
- onCleared()-Finish-Coroutine mit try-catch abgesichert (`9fd33ab`)
- Drei Transcription-Model-Loading-Bugs behoben (`51f3fe6`)
- Fortschrittsbalken bleibt nicht mehr nach Transkription hängen (Race Condition) (`d27f5e1`)
- Falscher „Audio file too large"-Fehler nach Antippen der Completion-Notification verhindert (`113b7d8`)

## 7. Hintergrund-Aufnahme & Benachrichtigungen

- TranscriptionForegroundService und Plattform-Bridge hinzugefügt (`7602669`)
- TranscriptionViewModel an ForegroundService gekoppelt (`03d9d36`)
- START_STICKY + Null-Intent-Guard + Notification-Verbesserungen in AudioRecordingService (`04f92be`)
- Stopp-Aufnahme-Bestätigungsdialog bei Back-Navigation (`835ecb9`)
- POST_NOTIFICATIONS-Permission im Manifest (`60dd509`)
- Lifecycle-Runtime-Compose-Dependency (`bc11ae4`)
- Benachrichtigung bei Abschluss des Modell-Downloads (`6e7e242`)
- Design-Spezifikationen für Background-Reliability und Notifications (`ee1f8ef`, `368dbf0`, `648c86a`, `b2f9390`)

## 8. iOS-Entfernung

- Alle iOS-Teile entfernt – MolyEcho ist Android-only (`03ed7a6`)
- iOS-Referenzen aus Dokumentation entfernt (`0e7ee3d`)

## 9. Sonstiges

### Chore / Refactoring
- Native Whisper-Library (LibWhisper, JNI) aktualisiert (`b09a8d5`)
- Verwaiste Multilingual-Standard-String-Ressourcen entfernt (`8bd4209`)
- Dead-Code ACTION_STOP aus TranscriptionForegroundService entfernt (`c0bccfc`)


---

## Lizenz

GNU General Public License v3.0 – siehe [LICENSE](LICENSE).

```
MolyEcho
Copyright (C) 2026  Contributors
Based on NotelyVoice
Copyright (C) 2025 NotelyVoice (Tosin Onikute)
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
