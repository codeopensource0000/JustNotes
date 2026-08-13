# JustNotes

A 100% local, offline Android notes app. No network access, no cloud sync — everything lives on the device: notes, folders, and an optional per-note secondary lock with encryption at rest.

## Requirements

- [Android Studio](https://developer.android.com/studio) (bundles the JDK, Android SDK, and Kotlin support — nothing else to install separately)
- A physical Android device (recommended) or an emulator, running Android 8.0 (API 26) or newer
- When setting up the Android SDK inside Android Studio, also enable **NDK (Side by side)** and **CMake** in the SDK Manager (needed later for the on-device speech-to-text feature)

## Getting started

1. Clone this repository.
2. Open the project folder in Android Studio and let it finish syncing (first sync downloads all dependencies, can take a few minutes).
3. Connect a physical device with USB debugging enabled (Settings → About phone → tap "Build number" 7 times → Options pour développeurs → Débogage USB), or start an emulator.
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
- `app/src/main/java/code/opensource0000/justnotes/security/` — PIN hashing and note encryption (Android Keystore)
- `app/src/main/java/code/opensource0000/justnotes/settings/` — app-wide preferences (theme, etc.)
- `app/src/main/java/code/opensource0000/justnotes/ui/screens/` — one file per screen, plus their ViewModels
- `app/src/main/java/code/opensource0000/justnotes/MainActivity.kt` — navigation graph tying the screens together

## Notes on the app ID

The application ID (`code.opensource0000.justnotes`) is intentionally generic and contains no personal or organizational identifiers.
