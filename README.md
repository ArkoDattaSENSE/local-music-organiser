# AudOneOut

AudOneOut is a native Android app for local music libraries. It scans on-device audio with MediaStore, stores the library in Room, helps review messy metadata, and creates portable playlists without requiring an account or uploading audio.

The app keeps the original PowerFLAC local-player roadmap, under the AudOneOut identity, and adds the AudOneOut intelligence layer:

> AudOneOut turns messy music metadata into better discovery and portable playlists.

## Architecture

- Kotlin, Gradle Kotlin DSL, Jetpack Compose, and Material 3.
- MVVM with Hilt-injected ViewModels, repositories, workers, and services.
- Room for tracks, roots, blacklist rules, scan jobs, library facets, analysis results, metadata layers, playlists, exports, and external matches.
- DataStore for privacy and background-check settings.
- MediaStore scanning without `MANAGE_EXTERNAL_STORAGE`.
- WorkManager and a MediaStore `ContentObserver` foundation for incremental checks.
- Media3 service foundation for local playback features from the original brief.

## Current State

Implemented production slices:

- Dark-first AudOneOut shell with real vector-style Compose icons.
- Home, Library, Player, New Music Inbox, and Settings screens.
- Android media permission request flow.
- MediaStore audio scanner with progress, blacklist filtering, and Room sync.
- Persistent library facets for songs, albums, artists, and folders.
- Search, sort, list/grid controls for the Library screen.
- Music Roots picker using Android Storage Access Framework, with include/exclude, rescan, and remove controls.
- Configurable folder blacklist with defaults, preview counts, enable/disable, delete, and restore defaults.
- Library Doctor metadata health checks stored in Room and shown in the Inbox.
- M3U/M3U8 and CSV playlist import/export helpers, plus Soundiiz handoff guidance.
- GitHub Actions build workflow and release-APK workflow.

## Build Locally

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

On this workstation, the most reliable local verification command has been:

```bash
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  GRADLE_USER_HOME=/home/arko/Devstuff/local-music-manager/.gradle-user-home \
  ./gradlew --no-daemon --max-workers=1 \
  -Dkotlin.compiler.execution.strategy=in-process \
  -Pandroid.aapt2FromMavenOverride=/home/arko/Android/Sdk/build-tools/34.0.0/aapt2 \
  assembleDebug testDebugUnitTest
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Android Permissions

AudOneOut requests audio-library access only:

- Android 13 and newer: `READ_MEDIA_AUDIO`
- Android 10-12: `READ_EXTERNAL_STORAGE`

It also declares foreground-service permissions for the original local playback roadmap. AudOneOut does not request `MANAGE_EXTERNAL_STORAGE`.

## Music Roots

Users can add one or more music roots through Android's folder picker. Roots are stored as persistable read grants and used to narrow future MediaStore scans where a relative path can be derived.

Each root shows:

- display name and location
- indexed track count
- storage used
- last scan time
- scan status
- include/exclude toggle
- rescan and remove actions

If no roots are selected, AudOneOut can still scan MediaStore audio safely.

## Folder Blacklist

Default blacklist suggestions include messaging audio, recordings, ringtones, notifications, alarms, podcasts, audiobooks, app cache-style folders, Audio_Lab, Dolby records, and thumbnails.

Blacklist rules are local-only filters. They never delete files. They only remove matching tracks from AudOneOut's working library, analysis, playlist generation, and exports.

## Library Doctor

Library Doctor detects:

- weak or missing titles
- unknown artists
- unknown albums
- missing artwork
- broken or inaccessible files
- possible duplicates

Findings are stored as analysis results and shown in New Music Inbox. AudOneOut does not edit or delete music files automatically.

## Playlist Import And Export

Implemented helper support:

- M3U/M3U8 import parsing
- CSV import parsing with quoted values
- M3U8 export using local content references
- streaming-migration CSV export
- Soundiiz handoff guidance

Direct streaming-service account modification is intentionally out of scope.

## GitHub Actions APK

Pushes to `main` run the `Android Build` workflow. It runs unit tests, assembles the debug APK, detects the generated APK path, and uploads it as the `audoneout-debug-apk` artifact.

The `Android Release APK` workflow can publish a downloadable debug APK to a GitHub Release.

## Gemini Setup

Gemini support is planned as an optional prompt-interpretation layer. AudOneOut is local-only by default and does not include API keys or secrets.

When online enrichment is eventually enabled, only metadata summaries should be sent, never audio files.

## Privacy

- No account required.
- No analytics.
- No broad storage access.
- No audio upload.
- No listening-history upload.
- Library data remains on device by default.
- Online enrichment is opt-in and controlled by DataStore-backed settings.

## Current Limitations

- Manual playlist persistence and reorder UI are not complete yet.
- Media3 playback service exists, but full queue controls and notification UX still need completion.
- Metadata suggestions are stored, but batch accept/reject flows are still pending.
- Optional Gemini interpretation is not implemented yet.
- Playlist import/export helpers are implemented, but Storage Access Framework import/export UI still needs wiring.

## Roadmap

1. Complete manual playlists, queue saving, and reorder flows.
2. Finish Media3 playback controls, notifications, lock-screen, and Bluetooth behavior.
3. Add Storage Access Framework playlist import/export UI.
4. Expand New Music Inbox batch review and metadata suggestion actions.
5. Add deterministic prompt-based playlist generation UI with interpreted-rule preview.
6. Add optional Gemini prompt interpreter with strict JSON validation and local fallback.
7. Add release signing configuration for non-debug distribution.
