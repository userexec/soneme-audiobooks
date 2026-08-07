# Soneme Audiobooks — starter implementation

This directory is a first native Android implementation of the behavior described in the repository specification.

## Design goals

- Target device: Sonim XP3Plus 5G (X320), Android 14, 480×854, no touchscreen.
- Kotlin + platform Android APIs.
- XML layouts and framework widgets (`ListView`, `Button`, `SeekBar`, `AlertDialog`).
- No AndroidX runtime libraries, no Google Play services, no analytics, no network access.
- MP3 is the promised format. Other audio MIME types are shown because `MediaPlayer` may support them without format-specific Soneme code.
- Background playback uses framework `MediaPlayer`, `MediaSession`, audio focus, and a `mediaPlayback` foreground `Service`.
- Library access uses the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) and persisted read grants, so internal storage and SD-card folders are treated identically.
- App state uses framework SQLite and SharedPreferences.

## Implemented in this starter

- Books / Recent / Queue / Player tabs.
- Sources view and system folder picker.
- Multiple source roots merged at the Books top level.
- Folder navigation with Back moving up one level.
- Metadata title / artist / runtime extraction, filename fallback, and alphabetical folder-first sorting.
- Source duplicate and nested-source checks.
- Queue insertion order; duplicate queue additions are ignored.
- Recent ordering by last-played time.
- Saved position and 100% completion state.
- Resume from saved progress; a completed title starts at 0 when explicitly played again.
- One-minute progress checkpoints plus event-driven saves on play/pause/seek/track change/completion/sleep expiry/service shutdown.
- Background playback foreground service and platform media session.
- Audio focus handling.
- Previous / next queue behavior and completion behavior for Repeat Off / 1 / All.
- Sleep timer and keypad `0` extension.
- Playback speeds from 0.5× through 4×.
- Configurable rewind and fast-forward intervals.
- Player keypad shortcuts 1–9, `*`, `0`, `#`.
- Player wiper short-seek and hold-to-repeat behavior.
- Framework Options menu, including `KEYCODE_MENU` and `KEYCODE_SOFT_LEFT` handling.

## Intentional assumptions

- Selecting an audio title begins playback immediately and resumes its saved position unless it was previously completed (100%), in which case it begins at 0:00.
- Queue additions are idempotent: adding a URI already present in Queue does not add a second copy.
- Manual Previous/Next always wraps when two or more queue items exist and starts the destination title at 0:00, matching the specification.
- Sleep state is session-only. Repeat, speed, rewind interval, and fast-forward interval are persistent preferences.
- Removing a Source stops it from appearing in Books but does not delete listening history or queue data.

## First-device-test checklist

The code is intentionally structured so the likely Sonim-specific adjustments are localized. On the X320, verify:

1. Left softkey keycode. `MainActivity.dispatchKeyEvent()` currently recognizes `KEYCODE_MENU` and `KEYCODE_SOFT_LEFT`.
2. D-pad focus order in Player view.
3. Whether Android's framework options panel is presented in the expected Sonim style.
4. Folder picker usability with no touchscreen, including access to the SD card.
5. Flip-close behavior while playing and paused.
6. Bluetooth/headset play/pause/previous/next delivery to the platform `MediaSession`.
7. `MediaPlayer` playback speed behavior on the device at the extreme 0.5× and 4× settings.

If #5 or #6 exposes an OEM lifecycle issue, Media3 would be the first fallback considered. It is intentionally not a dependency in this starter.

## Building

The project is configured for:

- compileSdk 34
- targetSdk 34
- minSdk 34
- Java 17
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21

A Gradle wrapper is not included in this generated starter because the build environment used to create it did not have a Gradle distribution available. Opening the project in Android Studio and generating/using a compatible wrapper is the simplest first build step. AGP 8.7.x uses Gradle 8.9.

## Repository-spec corrections incorporated

The implementation follows the clarifications from the design discussion:

- Queue is ordered by time added, not by recency of last play.
- Fast-forward Interval has the same 10 seconds / 1 minute / 10 minutes / 1 hour choices as Rewind Interval.
- Normal completion advances through Queue with Repeat Off, loops the current title with Repeat 1, and wraps the queue with Repeat All.
- Sleep-timer expiry is treated as an event-driven progress save.
