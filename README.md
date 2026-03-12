# MolyEcho – Deutsche Spracherkennung

Ein Fork von [NotelyVoice](https://github.com/tosinonikute/NotelyVoice), optimiert für deutsche Spracherkennung mit eingebettetem Offline-Modell.

**Paketname:** `de.molyecho.notlyvoice.android`  
**Version:** 1.2.6  
**Lizenz:** GPL-3.0

---

## Features

- **Sofort einsatzbereit** – Deutsches Tiny-Modell (75 MB) ist direkt im APK eingebettet, kein Download nötig
- **Offline-Transkription** – Sprache wird vollständig auf dem Gerät verarbeitet, keine Cloud-Anbindung
- **Optionales Turbo-Modell** – Hochgenaues Large-v3-Turbo-Modell (~1,6 GB) für beste Erkennungsqualität
- **Mehrsprachig** – Multilingual-Modell (50+ Sprachen) optional herunterladbar
- **Notizen mit Sprachaufnahme** – Aufnahmen direkt in Notizen einbetten und transkribieren
- **Export** – Notizen als PDF oder TXT exportieren, Audioaufnahmen teilen
- **Dark/Light Mode** – Systemthema oder manuelle Auswahl
- **Markdown-ähnliche Formatierung** – Fett, Kursiv, Überschriften, Listen

---

## Modelle

| Modus | Modell | Größe | Herkunft |
|-------|--------|-------|----------|
| Deutsch – Schnell | ggml-tiny-german | ~75 MB | Eingebettet im APK |
| Deutsch – Genau | ggml-large-v3-turbo-german | ~1,6 GB | Download erforderlich |
| Mehrsprachig Standard | ggml-tiny (multilingual) | ~142 MB | Download erforderlich |
| Mehrsprachig Erweitert | ggml-small (multilingual) | ~465 MB | Download erforderlich |

---

## Screenshots

| Notizliste | Notiz bearbeiten | Sprachmodell-Auswahl |
|-----------|-----------------|----------------------|
| ![Notizliste](https://placehold.co/300x600?text=Notizliste) | ![Editor](https://placehold.co/300x600?text=Editor) | ![Modelle](https://placehold.co/300x600?text=Modelle) |

---

## Build

### Voraussetzungen

- Android Studio Hedgehog oder neuer
- JDK 17+
- Android SDK 34

### Schritte

```bash
git clone https://github.com/<dein-nutzername>/MolyEcho.git
cd MolyEcho
./gradlew :androidApp:assembleDebug
```

Das APK liegt anschließend unter `androidApp/build/outputs/apk/debug/`.

> **Hinweis:** Das eingebettete German-Tiny-Modell (`ggml-tiny-german.bin`) muss sich unter  
> `shared/src/androidMain/assets/ggml-tiny-german.bin` befinden, bevor der Build startet.

---

## Unterschiede zum Original

| Feature | NotelyVoice (Original) | MolyEcho (Dieser Fork) |
|---------|------------------------|------------------------|
| Standardsprache | Englisch | Deutsch |
| Bundled-Modell | Keins | Deutsches Tiny-Modell |
| App-Name | Notely Voice | MolyEcho |
| Paketname | `com.module.notelycompose.android` | `de.molyecho.notlyvoic.android` |
| Modell-Auswahl UI | Sprachauswahl + Modell getrennt | Integrierte Modi-Auswahl (Schnell / Genau / Mehrsprachig) |

---

## Credits

- **NotelyVoice** – Originale App von [Tosin Onikute](https://github.com/tosinonikute/NotelyVoice)
- **Deutsches Tiny-Modell (Standard)** – [primeline/whisper-tiny-german-1224](https://huggingface.co/primeline/whisper-tiny-german-1224) auf HuggingFace (eingebettetes Standard-Modell, ~75 MB)
- **Deutsches Turbo-Modell** – [primeline/whisper-large-v3-turbo-german](https://huggingface.co/primeline/whisper-large-v3-turbo-german) auf HuggingFace
- **whisper.cpp** – [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp) für GGML-Inferenz

---

## Changelog

- Standardmodell auf primeline/whisper-tiny-german-1224 aktualisiert (bessere Genauigkeit, gleiche Größe)

---

## Lizenz

Dieses Projekt steht unter der **GNU General Public License v3.0**.  
Siehe [LICENSE](LICENSE) für Details.

```
MolyEcho – Deutsche Spracherkennung
Copyright (C) 2024  Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
