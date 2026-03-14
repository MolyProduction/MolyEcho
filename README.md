# MolyEcho

**Euer persönlicher Schreiberling.**
Erstellt Notizen, nimmt Sprache auf und wandelt sie in Text um – vollständig auf eurem Gerät, ohne dass auch nur ein einziges Wort eure Hand verlässt.

Eine Internetverbindung wird nur einmalig benötigt, wenn ihr beim ersten Start ein Sprachmodell herunterladet. Danach arbeitet MolyEcho vollständig offline.

---

## Was ist MolyEcho?

MolyEcho ist eine Android-App für Notizen mit integrierter Offline-Spracherkennung, optimiert für die deutsche Sprache. Aufnahmen werden direkt auf dem Gerät transkribiert – kein Server, keine Cloud, keine Datenweitergabe.

Hinter der Transkription steckt [whisper.cpp](https://github.com/ggerganov/whisper.cpp) mit speziell auf Deutsch feingetunten Modellen.

---

## Features

- **100 % Offline** – Sprache bleibt auf eurem Gerät, keine Cloud-Anbindung
- **Optimiert für Deutsch** – speziell auf Deutsch feingetunte Whisper-Modelle
- **Wählbare Modellgröße** – von schnell & kompakt bis hochgenau
- **Notizen mit Sprachaufnahme** – Aufnahmen direkt in Notizen einbetten
- **Export** – Notizen als PDF oder TXT exportieren, Audioaufnahmen teilen
- **Formatierung** – Fett, Kursiv, Überschriften, Listen
- **Dark / Light Mode** – Systemthema oder manuelle Auswahl
- **Datenschutz by Design** – keine Accounts, kein Tracking, keine Telemetrie

---

## Sprachmodelle

Beim ersten Start wählt ihr ein Modell aus – es wird einmalig heruntergeladen und danach offline genutzt.

| Modus | Modell | Größe | Empfohlen für |
|-------|--------|-------|---------------|
| **Deutsch – Schnell** *(Standard)* | ggml-large-v3-turbo-german-q5_0 | ~574 MB | Alltag, schnelle Notizen |
| **Deutsch – Genau** | ggml-large-v3-turbo-german | ~1,62 GB | Lange Texte, hohe Genauigkeit |
| **Mehrsprachig** | ggml-small (multilingual) | ~465 MB | 50+ Sprachen |

> **Hinweis zum Standardmodell:** Das Modell *Deutsch – Schnell* wurde von uns eigens für MolyEcho quantisiert und auf HuggingFace veröffentlicht. Mit ~574 MB ist es aktuell die genaueste deutsche Whisper-Variante unter 1,4 GB – präzise genug für den Alltag, kompakt genug für jedes Smartphone.

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
Copyright (C) 2024  Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```
