# JustNotes

A 100% local, offline Android notes app. No network access, no cloud sync, no accounts — everything lives on the device: notes, folders, an optional per-note secondary lock with real encryption at rest, and even speech-to-text runs entirely on-device.

## Why it's built this way

The starting principle was **offline-first, no exceptions**: not "offline with an optional cloud backup," not "offline except for this one convenience feature" — the app never opens a network connection, period. That single constraint shaped several concrete decisions:

- **Speech-to-text ships its models inside the app** instead of downloading them on demand. A more typical implementation would fetch the recognition model on first use; here the ~40MB French and English [Vosk](https://alphacephei.com/vosk/) models are bundled directly as APK assets and unpacked to local storage the first time you install them from Settings. The trade-off is a much larger APK; the payoff is that "offline" is actually true, not "offline once you've downloaded something."
- **No account, no sync, no analytics.** There's nothing to log into and nothing phoning home — the SQLite database and all preferences live only in the app's private storage.
- **Android's own backup is switched off too.** This one is easy to miss: an app that never opens a socket can still have its entire private directory uploaded to the user's Google Drive, because the *system* does the transfer, not the app. `allowBackup` is `false` and both rules files exclude everything, cloud backup and phone-to-phone transfer alike. The trade-off is deliberate and worth knowing before you rely on it: **changing phones carries nothing across.** Export the notes you want to keep.
- **Encryption keys are never stored, anywhere.** A locked note's key is derived from that note's own code every time you type it, and exists in memory only for as long as the note is open. There is no copy on disk to steal — but equally, no way to recover a note whose code is forgotten. See [Encryption](#encryption) for why it works this way rather than through the Android Keystore.

The second driving principle was **genuine security, not just a PIN screen for show**. That's why the app has two independent authentication layers rather than one:

- The **primary lock** gates opening the app at all (optional — it can be turned off in Settings while keeping the code saved for later). It is a gate and nothing more: it protects no data at rest, so a fingerprint is enough to pass it.
- The **secondary lock** protects individual notes, and it's per-note: each locked note has its **own** code, and that code is what its **own** encryption key is derived from. Cracking one note's code exposes only that note and gives no advantage against any other, because no secret is shared between them.

One honest qualification, since the point of writing this down is to be accurate rather than flattering: if you leave the optional fingerprint shortcut enabled (it is on by default), then anyone who can pass your phone's biometric can open any locked note without knowing its code. That is a convenience you can switch off in Settings → Security, and switching it off is what makes the two layers strictly independent.

## Features

### Notes and organization
- Notes are plain, portable text with a light Markdown subset (bold, italic, headings, bullet lists, checklists) — never a proprietary rich-text format, so a note is always just a `.txt`/`.md` file underneath.
- Folders: create them from the home screen, open one to browse its notes, move a note between folders or delete it via a long-press menu (with haptic feedback), delete a folder (its notes go with it — the catch-all default folder is exempt from deletion).
- Recents and Folders tabs on the home screen; a folder-picker chip inside the editor lets you reassign a note's folder (or create a new one) without leaving it.
- Export any note as plain text, Markdown, or HTML through the normal Android share sheet — export is the one deliberate "escape hatch" from the app; nothing is protected once it leaves via export, by design.

### Security
- Primary app-open code (6 digits) with fingerprint/biometric unlock (auto-prompted the moment the lock screen appears), disableable without losing the saved code.
- Secondary per-note lock: locking a note for the first time saves it silently in the background (no forced trip back to the home screen) and then walks through a one-time code setup specific to that note.
- Optional fingerprint shortcut for locked notes, on by default and switchable in Settings. Turning it off erases every stored shortcut on the spot.
- Turning a note's lock off wipes that note's code, its fingerprint shortcut and its in-memory key — but only *after* the plain text has been written back, never before.

#### Encryption

A locked note's content is encrypted with AES-256-GCM. The key comes from the note's own code:

```
code ──PBKDF2-HMAC-SHA256(210,000 iterations, 16-byte salt)──► master
                                                                ├─HMAC──► verification hash   (stored)
                                                                └─HMAC──► AES-256 key         (never stored)
```

One expensive derivation, split into two independent values by label. The hash that goes to disk reveals nothing about the key that doesn't, and the key is rebuilt from scratch each time you type the code.

**Why not the Android Keystore?** An earlier version of this app did exactly that — one hardware-isolated Keystore key per note, unextractable, which is genuinely stronger against anyone holding a copy of the phone's data. It had one fatal property: a Keystore key created with `setUserAuthenticationRequired` is **permanently invalidated** the moment the device's screen lock is removed. Every locked note became unreadable, with no warning, no fallback and no recovery. Trading hardware isolation for content that survives whatever the device does to itself was the right way round for a notes app.

The cost of that choice, stated plainly: someone who obtains a copy of the app's private data can try codes offline against the ciphertext. Six digits is a million possibilities — the 210,000 PBKDF2 iterations are there to make each attempt expensive, not to make the search impossible. Getting at that data in the first place requires a rooted or already-unlocked phone; the device's own screen lock remains the real perimeter, and in-app guessing is separately rate-limited.

**Where the fingerprint fits.** The shortcut keeps a *copy* of the note's key, encrypted under a biometric-gated Keystore key, and hands it back after a successful scan. The Keystore is a convenience here, not the foundation — when it is invalidated (screen lock removed, fingerprint added or removed), the copy is thrown away and the code still opens the note exactly as before. A shortcut that breaks may cost convenience; it must never cost data.

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

## Installing the app

If you only want to *use* JustNotes, you do not need Android Studio or any of
the build steps below.

1. Download an APK from the [Releases](https://github.com/codeopensource0000/JustNotes/releases)
   page. If you are not sure which, take `app-universal-release.apk` — it
   works on every phone. The other two hold a single processor architecture
   instead of all of them, which makes them about 30 MB smaller:

   | File | Size | For |
   | --- | --- | --- |
   | `app-universal-release.apk` | 120 MB | Any phone — the safe choice |
   | `app-arm64-v8a-release.apk` | 91 MB | Almost every phone made since ~2017 |
   | `app-armeabi-v7a-release.apk` | 90 MB | Older 32-bit phones |

2. Check that you got what was published — see [Verifying the
   download](#verifying-the-download) just below. Worth doing for any app you
   install outside a store, and doubly so for one holding your notes.
3. Open the file on your phone. Android will ask permission to install from
   this source; that prompt is normal for any app installed outside the Play
   Store, and the permission can be revoked afterwards.

**Requirements:** Android 8.0 (API 26) or newer, and about 220 MB free — the
app itself, plus another ~40 MB once a voice model is unpacked next to it.

**Before you rely on it, two things are worth knowing:**

- **There is no backup, by design.** Android's own backup is switched off, for
  the cloud and for phone-to-phone transfer alike, because an app that never
  opens a network connection should not have its database uploaded by the
  system either. The consequence is real: **changing phones carries nothing
  across.** Export anything you want to keep.
- **A forgotten code cannot be recovered.** A locked note's encryption key is
  derived from its code and stored nowhere. Nobody can reset it — not you, not
  this app, not anyone holding the phone.

### Verifying the download

Every released APK is signed with the same key. You can check that the file
you downloaded really came from this project:

```sh
apksigner verify --print-certs app-universal-release.apk
```

The certificate's SHA-256 digest must be:

```
3492f0f49e36e9ced9ee4dde660e33b4fb001efbc8166eb54907486c595ed6f9
```

If it differs, the file was not signed with this project's key — do not
install it.

## What is stored, and where

Everything lives in the app's own private storage, which other apps cannot
read. Nothing is sent anywhere: the app holds no `INTERNET` permission, so it
could not reach the network even if it tried.

| What | Where | Notes |
| --- | --- | --- |
| Notes and folders | SQLite database (`justnotes.db`) | A locked note's content is stored encrypted |
| Codes | One preferences file per code | Only a salted hash — never the code itself |
| Fingerprint shortcuts | One preferences file | A copy of a note's key, encrypted by the Android Keystore |
| Encryption keys | Nowhere | Rebuilt from the code each time, held in memory only |
| Voice models | App storage, after you install one from Settings | Unpacked from the APK, never downloaded |
| Exports | Cache, until the next app launch | Plain text, cleared at every start |

The only permission requested is `RECORD_AUDIO`, and only when you first use
voice dictation. Recognition runs entirely on the device; no audio leaves the
phone, and none is kept.

## Building it yourself

### Requirements

- [Android Studio](https://developer.android.com/studio) (bundles the JDK, Android SDK, and Kotlin support — nothing else to install separately)
- A physical Android device (recommended) or an emulator, running Android 8.0 (API 26) or newer

### Getting started

1. Clone this repository.
2. Open the project folder in Android Studio and let it finish syncing (first sync downloads all dependencies, can take a few minutes — this project's APK is large, around 100–140MB in debug builds, mostly the two bundled speech models).
3. Connect a physical device with USB debugging enabled (Settings → About phone → tap "Build number" 7 times → Developer options → USB debugging), or start an emulator.
4. Press **Run** in Android Studio, or from a terminal:

   ```sh
   ./gradlew installDebug
   ```

### Building from the command line

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

## License

[Apache License 2.0](LICENSE) — see [NOTICE](NOTICE) for the third-party
components redistributed with the app, chiefly the two bundled
[Vosk](https://alphacephei.com/vosk/) speech models, which are themselves
Apache 2.0.

## Notes on the app ID

The application ID (`code.opensource0000.justnotes`) is intentionally generic and contains no personal or organizational identifiers.
