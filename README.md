# JustNotes

A 100% local, offline Android notes app. No network access, no cloud sync, no accounts — everything lives on the device: notes, folders, an optional per-note secondary lock with real encryption at rest, and even speech-to-text runs entirely on-device.

## Why it's built this way

The starting principle was **offline-first, no exceptions**: not "offline with an optional cloud backup," not "offline except for this one convenience feature" — the app never opens a network connection, period. That single constraint shaped several concrete decisions:

- **Speech-to-text ships its models inside the app** instead of downloading them on demand. A more typical implementation would fetch the recognition model on first use; here the ~40MB French and English [Vosk](https://alphacephei.com/vosk/) models are bundled directly as APK assets and unpacked to local storage the first time you install them from Settings. The trade-off is a much larger APK; the payoff is that "offline" is actually true, not "offline once you've downloaded something."
- **No account, no sync, no analytics.** There's nothing to log into and nothing phoning home — the SQLite database and all preferences live only in the app's private storage.
- **Encryption keys never leave the device.** The secondary lock's cryptography is backed by the Android Keystore, which is hardware-isolated on supported devices — the app can ask the Keystore to encrypt or decrypt, but the raw key material is never exposed to app code, even to itself.

The second driving principle was **genuine security, not just a PIN screen for show**. That's why the app has two independent authentication layers rather than one:

- The **primary lock** gates opening the app at all (optional — it can be turned off in Settings while keeping the code saved for later).
- The **secondary lock** protects individual notes, and — importantly — it's per-note: each locked note has its **own** PIN and its **own** independent Keystore encryption key. Cracking one note's code exposes only that note; it gives no advantage against any other locked note, because there's no shared secret anywhere in the design.

## Features

### Notes and organization
- Notes are plain, portable text with a light Markdown subset (bold, italic, headings, bullet lists, checklists) — never a proprietary rich-text format, so a note is always just a `.txt`/`.md` file underneath.
- Folders: create them from the home screen, open one to browse its notes, move a note between folders or delete it via a long-press menu (with haptic feedback), delete a folder (its notes go with it — the catch-all default folder is exempt from deletion).
- Recents and Folders tabs on the home screen; a folder-picker chip inside the editor lets you reassign a note's folder (or create a new one) without leaving it.
- Export any note as plain text, Markdown, or HTML through the normal Android share sheet — export is the one deliberate "escape hatch" from the app; nothing is protected once it leaves via export, by design.

### Security
- Primary app-open PIN with fingerprint/biometric unlock (auto-prompted the moment the lock screen appears), disableable without losing the saved code.
- Secondary per-note lock: locking a note for the first time saves it silently in the background (no forced trip back to the home screen) and then walks through a one-time PIN setup specific to that note. Each note's PIN is a salted PBKDF2 hash (210,000 iterations) stored independently; each note's content is encrypted with its own AES-256-GCM key, generated and held in the Android Keystore, gated by `setUserAuthenticationRequired` so the OS itself refuses to use the key without a real biometric/device-credential check.
- Turning a note's lock off wipes that note's PIN and Keystore key immediately — nothing lingers once it's no longer protecting anything.

### Voice dictation
- Fully offline speech-to-text via [Vosk](https://alphacephei.com/vosk/), in French and English. Both language models are bundled inside the app itself; installing one just unpacks it from the APK's assets into local storage — no download, ever.
- A microphone button in the editor toolbar dictates directly into the note, with a live pulsing indicator while listening. Settings lets you install/remove each language independently and choose which one the mic uses.

### Language
- Full French/English UI, switchable independently for two different things: the app's own display language (Settings → Général, with a confirmation step since it briefly restarts the app) and the dictation language (which model the mic uses) — these don't have to match, since a bilingual person might read the UI in one language and dictate in the other.

### Design
- A warm, muted teal-and-cream brand palette (amber reserved specifically for anything related to the secondary lock, so that color always means the same thing).
- A restrained type system: a serif face for the app's few genuine "brand" moments (the home screen wordmark, note titles) paired with plain, quiet sans-serif for everything else — deliberately not decorative everywhere, just where it earns its place.
- Card-based home screen, borderless "paper" text fields in the editor (no boxed form-field look), a custom notepad launcher icon.
- Light and dark themes, following the system setting or overridable in Settings.

## Requirements

- [Android Studio](https://developer.android.com/studio) (bundles the JDK, Android SDK, and Kotlin support — nothing else to install separately)
- A physical Android device (recommended) or an emulator, running Android 8.0 (API 26) or newer

## Getting started

1. Clone this repository.
2. Open the project folder in Android Studio and let it finish syncing (first sync downloads all dependencies, can take a few minutes — this project's APK is large, around 100–140MB in debug builds, mostly the two bundled speech models).
3. Connect a physical device with USB debugging enabled (Settings → About phone → tap "Build number" 7 times → Developer options → USB debugging), or start an emulator.
4. Press **Run** in Android Studio, or from a terminal:

   ```sh
   ./gradlew installDebug
   ```

## Building from the command line

```sh
./gradlew assembleDebug   # builds app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug    # builds and installs on a connected device/emulator
```

## Project structure

- `app/src/main/java/code/opensource0000/justnotes/data/local/` — Room database, entities, DAOs
- `app/src/main/java/code/opensource0000/justnotes/security/` — PIN hashing and per-note encryption (Android Keystore)
- `app/src/main/java/code/opensource0000/justnotes/settings/` — app-wide preferences (theme, app language, dictation language)
- `app/src/main/java/code/opensource0000/justnotes/stt/` — offline speech-to-text (Vosk model management + recognition)
- `app/src/main/java/code/opensource0000/justnotes/export/` — plain text / Markdown / HTML export
- `app/src/main/java/code/opensource0000/justnotes/ui/screens/` — one file per screen, plus their ViewModels
- `app/src/main/java/code/opensource0000/justnotes/ui/components/` — small composables shared across screens (dialogs, rows, PIN pad)
- `app/src/main/java/code/opensource0000/justnotes/ui/theme/` — color palette, typography, light/dark theme
- `app/src/main/java/code/opensource0000/justnotes/MainActivity.kt` — navigation graph tying the screens together
- `app/src/main/assets/` — the bundled Vosk speech models (French and English)

## Notes on the app ID

The application ID (`code.opensource0000.justnotes`) is intentionally generic and contains no personal or organizational identifiers.
