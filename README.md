# <p align="center">MolyEcho</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/MolyProduction/MolyEcho/main/shared/src/commonMain/composeResources/drawable/molyecho_logo.png" width="220" alt="MolyEcho Logo">
</p>

<p align="center"><strong>Euer persönlicher Schreiberling.</strong></p>
<p align="center">Erstellt Notizen, nimmt Sprache auf und wandelt sie in Text um – vollständig auf eurem Gerät, ohne dass auch nur ein einziges Wort eure Hand verlässt.</p>

<p align="center">Eine Internetverbindung wird nur einmalig benötigt, wenn ihr beim ersten Start ein Sprachmodell herunterladet. Danach arbeitet MolyEcho vollständig offline.</p>

---

## Was ist MolyEcho?

MolyEcho ist eine Android-App für Notizen mit integrierter Offline-Spracherkennung, optimiert für die deutsche Sprache. Aufnahmen werden direkt auf dem Gerät transkribiert – kein Server, keine Cloud, keine Datenweitergabe.

Hinter der Transkription steckt [whisper.cpp](https://github.com/ggerganov/whisper.cpp) mit speziell auf Deutsch feingetunten Modellen.

---

## Features

### ✏️ Notizen

- **Rich-Text-Editor** – Formatiere Notizen mit Überschriften (Titel, Überschrift, Unterüberschrift), **Fett**, *Kursiv*, Unterstrichen und Textausrichtung (links, zentriert, rechts)
- **Aufzählungslisten** – Strukturierte Listenpunkte direkt im Editor
- **Anpassbare Schriftgröße** – Editor-Textgröße individuell einstellen
- **Volltextsuche** – Jede Notiz sofort per Suchleiste finden
- **Intelligentes Filtern** – Notizen nach Typ sortieren: Alle, Markiert ⭐, Sprachnotizen, Zuletzt bearbeitet
- **Notizen markieren** – Wichtige Notizen als Favorit (Stern) hervorheben
- **Zusammenfassung** – Lange Transkriptionen automatisch auf die Kernaussagen kürzen (lokal, kein KI-Server)

### 🎙️ Spracherkennung

- **Hochgenaue Offline-Transkription** – Whisper wandelt Sprache direkt auf dem Gerät in Text um – kein Server, keine Cloud
- **Optimiert für Deutsch** – speziell feingetunte Whisper-Modelle für deutsche Sprache
- **50+ Sprachen** – über das Mehrsprachig-Modell auch international nutzbar
- **Nahtlose Integration** – Aufnahme direkt in die Notiz transkribieren oder später manuell starten
- **Audio-/Video-Import** – externe Aufnahmen importieren und transkribieren lassen
- **Unbegrenzte Transkriptionen** – kein Limit, kein Abo
- **Wählbare Modellgröße** – von schnell & kompakt bis höchstgenau (Details siehe [Sprachmodelle](#sprachmodelle))

### 🎧 Aufnahme & Wiedergabe

- **Integrierte Aufnahme** – Sprachnotizen direkt in der App aufnehmen, pausieren und stoppen
- **Audiowiedergabe** – Aufnahmen direkt aus der Notiz abspielen
- **Quick-Settings-Kachel** – Aufnahme per Android-Schnelleinstellung starten, ohne die App zu öffnen
- **Hintergrundaufnahme** – Aufnahme läuft weiter, auch wenn der Bildschirm gesperrt ist

### 📤 Export & Teilen

- **Export als PDF oder TXT** – einzelne Notizen in gängige Formate exportieren
- **Batch-Export** – mehrere Notizen gleichzeitig exportieren (Audio + TXT)
- **Text teilen** – Notiztext direkt an WhatsApp, Nachrichten, Drive oder andere Apps senden
- **Audio teilen** – Aufnahmen direkt aus der App weitergeben

<p align="center">
  <img src="https://raw.githubusercontent.com/MolyProduction/MolyEcho/main/assets/molyecho_mascot.png" width="180" alt="MolyEcho Maskottchen">
</p>

### ⚙️ Allgemein

- **100 % Offline** – nach dem einmaligen Modell-Download vollständig ohne Internet
- **Dark / Light Mode** – Systemthema oder manuelle Auswahl
- **Datenschutz by Design** – keine Accounts, kein Tracking, keine Telemetrie
- **Kotlin Multiplatform** – entwickelt mit KMP + Compose Multiplatform

---

## Sprachmodelle

Beim ersten Start wählt ihr ein Modell aus – es wird einmalig heruntergeladen und danach offline genutzt.

| Modus | Modell | Größe | Empfohlen für |
|-------|--------|-------|---------------|
| **Deutsch – Genau** *(Standard)* | whisper-large-v3-turbo-german-sherpa-onnx |  1 GB | Alltag, schnelle Notizen |
| **Deutsch – Extrem Genau** | ggml-large-v3-turbo-german | ~1,62 GB | Lange Texte, hohe Genauigkeit |
| **Mehrsprachig** | ggml-small (multilingual) | ~465 MB | 50+ Sprachen |

> **Hinweis zum Standardmodell:** Das Modell *Deutsch – Genau* wurde von uns eigens für MolyEcho quantisiert und auf HuggingFace veröffentlicht.
---

## Build

### Voraussetzungen

- Android Studio Hedgehog oder neuer
- JDK 17+
- Android SDK 34

### Schritte

```bash
git clone https://github.com/MolyProduction/MolyEcho.git
cd MolyEcho
./gradlew :androidApp:assembleDebug
```

Das APK liegt anschließend unter `androidApp/build/outputs/apk/debug/`.

---

## Datenschutz

MolyEcho verarbeitet alle Sprach- und Textdaten ausschließlich lokal auf eurem Gerät:

- Keine Übertragung von Audiodaten oder Transkriptionen
- Keine Nutzerkonten oder Registrierung
- Keine Analyse- oder Tracking-Bibliotheken
- Internetverbindung nur beim erstmaligen Modell-Download

---

## Credits

- **NotelyVoice** – Ursprüngliche App von [Tosin Onikute](https://github.com/tosinonikute/NotelyVoice) *(Basis dieses Forks)*
- **Deutsches Turbo-Modell (Schnell)** – von uns quantisiert und veröffentlicht: [F1sk/whisper-large-v3-turbo-german-ggml-q5_0](https://huggingface.co/F1sk/whisper-large-v3-turbo-german-ggml-q5_0)
- **Deutsches Turbo-Modell (Genau)** – [cstr/whisper-large-v3-turbo-german](https://huggingface.co/cstr/whisper-large-v3-turbo-german)
- **whisper.cpp** – [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp)

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
(at your option) any later version.
```
