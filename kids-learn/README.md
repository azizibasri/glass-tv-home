# Belajar Ceria

A colourful learning app for children aged 3 to 5, for Android phones and tablets (Android 8 or newer).
No ads, no internet permission, no accounts: it works offline and the APK is tiny, because every picture,
animation and sound is drawn or synthesised in code.

*Aplikasi pembelajaran untuk kanak-kanak 3–5 tahun: ABC, 123, Alif Ba Ta, kalimah Islam dan perkataan Arab,
melalui melukis, seret & lepas, dan padankan perkataan.*

## What's inside

Five subjects, each with four ways to play:

| Subject | Items |
|---|---|
| **ABC** | A to Z, each with a picture: A is for Apple 🍎 |
| **123** | 1 to 10, with things to count, and the Malay word (Satu, Dua…) |
| **Alif Ba Ta** | The 29 Hijaiyah letters, named as in Malaysian Iqra' classes |
| **Islamic Words** | Bismillah, Alhamdulillah, Assalamualaikum, Masjid, Al-Quran… with their meaning |
| **Arabic Words** | كتاب Kitab (book), قطة Qittah (cat), شمس Syams (sun)… 20 everyday words |

| Activity | How it plays |
|---|---|
| 👀 **Learn** | Big flash cards, read aloud. Swipe them like photos on an iPhone; tap to hear again. |
| ✏️ **Draw** | Trace the dashed letter with a finger in a crayon colour. When it's traced well enough, it fills with colour and confetti flies. Messy scribbles get a hint to wipe with the sponge. |
| 🧩 **Drag** | Drag each tile into the slot of its picture: 🍎 takes A, three ducklings take 3, the shadow of ب takes ب. |
| 🔗 **Match** | Draw a line from each picture to its word (or tap one, then the other). |

The feel is meant to be like Apple's: spring physics on every movement (buttons press in and bounce back,
cards follow the finger and tilt, tiles lift with a deeper shadow), soft drifting colour blobs behind
everything, glossy cards with tinted shadows, zoom transitions between screens, gentle bell sounds and haptics.

Words are spoken with the phone's own text-to-speech. Arabic uses an Arabic voice if the phone has one
(Settings → System → Languages → Text-to-speech → install Arabic); otherwise the transliteration is read
in English.

## Get the APK

Every push that touches `kids-learn/` builds the APK on GitHub Actions (workflow **Belajar Ceria (kids app)**).
Open the latest run and download **belajar-ceria-apk**; the **belajar-ceria-screenshots** artifact shows every
screen running on an emulator.

On the phone or tablet: open the APK, allow "install unknown apps" for the browser or file manager when asked,
and install.

## Build it yourself

Needs JDK 17 and the Android SDK (platform 34, build-tools 34.0.0):

```sh
cd kids-learn
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

`tools/screenshots.sh <apk> <folder>` installs it on a connected device or emulator, opens every kind of
screen and saves screenshots. `tools/playtest.py <folder>` then plays the games like a child (flicks cards,
drags tiles, draws match lines, traces Alif), records a video and checks each game reacted; CI runs both. Any screen can be opened directly:
`adb shell am start -n com.belajarceria.kids/.MainActivity --es open 2,3` (subject 0–4, activity 0–3).

## Code map

| File | What it does |
|---|---|
| `Content.java` | All the letters, numbers and words |
| `MainActivity.java` | The screen stack, zoom transitions, celebrations |
| `HomeScreen`, `SubjectScreen` | The menus |
| `LearnScreen`, `TraceScreen` + `TraceView`, `DragScreen`, `MatchScreen` | The four activities |
| `Spring.java` | Damped-spring animation used everywhere |
| `BlobBackground`, `Confetti` | The moving backdrop and the confetti |
| `Sfx`, `Voice` | Synthesised sound effects and text-to-speech |
