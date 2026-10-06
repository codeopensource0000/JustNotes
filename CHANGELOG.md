# Changelog

All notable changes to this project are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Import notes from `.md` or `.txt` files, from Settings → Data. Several files
  can be picked at once; each becomes a note in the default folder. A leading
  Markdown heading (any level, including the underlined form) becomes the
  title, YAML front matter is stripped, and a file without a heading is
  imported whole and titled after its file name. Files that are not text are
  skipped and counted rather than failing the import.
- Imported notes arrive unlocked, which is a limit and not a choice: a plain
  text file carries neither a code nor a Keystore key.

### Changed
- Build tooling: AGP 9.4.1, Kotlin 2.4.20, KSP 2.3.12, AndroidX refresh,
  kotlinx-serialization 1.11.0.

## [1.0.0] — not yet released

First public release.

### Notes and organisation
- Plain-text notes with a light Markdown subset (bold, italic, headings,
  bullet lists, checklists), previewed live in the editor while the stored
  text stays plain.
- Folders, with a catch-all default folder that cannot be deleted. Notes can
  be moved or deleted from a long-press menu.
- Export a note as `.txt`, `.md` or `.html` through the Android share sheet.

### Security
- Optional app-open lock: a 6-digit code, with fingerprint unlock.
- Per-note secondary lock. Each locked note has its own code, and its content
  is encrypted with AES-256-GCM using a key **derived from that code**
  (PBKDF2-HMAC-SHA256, 210,000 iterations, split by HMAC into a verification
  hash and the encryption key). The key is never stored anywhere.
- Optional fingerprint shortcut per locked note, on by default and switchable
  in Settings. It keeps a copy of the note's key wrapped by a biometric-gated
  Android Keystore key; if that key is invalidated — the phone's screen lock
  being removed, or a fingerprint added — the shortcut is discarded and the
  note's code still opens it. The shortcut is never the only way in.
- Failed codes are throttled: five free attempts, then a wait that doubles
  from 30 seconds. The counter only resets on a correct code, never by waiting.
- The app re-locks after 60 seconds in the background, returning to exactly
  where you were once unlocked, unsaved changes included.
- Screenshots, screen recordings and the task-switcher thumbnail are blocked
  by default. Allowing them is a setting, and turning it on asks for the code.
- Turning the app lock off also asks for the code first.
- Android's own backup is disabled, cloud backup and phone-to-phone transfer
  alike. Nothing leaves the device — with the consequence that changing
  phones carries nothing across.

### Voice dictation
- Fully offline speech-to-text (French and English) via Vosk. Both models
  ship inside the app; installing one unpacks it from the APK's assets, with
  no download at any point.

### Language and appearance
- French and English UI, switchable independently of the dictation language
  and of the phone's own language.
- Light and dark themes, following the system setting or overridden in
  Settings.

[Unreleased]: https://github.com/codeopensource0000/JustNotes/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/codeopensource0000/JustNotes/releases/tag/v1.0.0
