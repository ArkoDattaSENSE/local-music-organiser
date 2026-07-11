# AudOneOut

AudOneOut is a native Android music app for local files. It starts as a polished dark-first shell and will grow into a private library scanner, player, queue manager, playlist tool, and prompt-driven mix builder.

## Architecture

- Kotlin Android app using Gradle Kotlin DSL.
- Jetpack Compose and Material 3 for UI.
- Navigation Compose for the main app surfaces.
- MVVM with `ViewModel` and `StateFlow`.
- Hilt is wired at the application and activity layers.

## Current Milestone

Milestone 1 is implemented:

- Android application module.
- Hilt setup.
- Compose theme.
- Home, Library, Player, and Settings screens.
- Bottom navigation.
- Dark-first placeholder states.
- GitHub Actions build workflow.

## Build Locally

```bash
./gradlew assembleDebug
```

Run unit tests:

```bash
./gradlew testDebugUnitTest
```

The debug APK is generated under `app/build/outputs/apk/debug/`.

## Android Permissions

Milestone 1 does not request media permissions yet. The next milestone will add version-correct MediaStore access for Android 10 and newer without `MANAGE_EXTERNAL_STORAGE`.

## GitHub Actions APK

Push to `main` or run the `Android Build` workflow manually. The workflow runs unit tests, assembles the debug APK, detects the generated APK path, and uploads it as the `audoneout-debug-apk` artifact.

## Gemini Setup

Gemini support is planned for a later optional milestone. AudOneOut is local-only by default and does not include API keys or secrets.

## Privacy

- No account required.
- No analytics.
- No broad storage access.
- No audio upload.
- Library data is intended to remain on device.

## Current Limitations

- Local media scanning is not implemented yet.
- Playback is represented by placeholder UI only.
- Playlists, queues, Room persistence, Media3, and prompt interpretation are planned future milestones.

## Roadmap

1. Local music permission handling and MediaStore scanning.
2. Room-backed persistent library.
3. Library UI for songs, albums, artists, folders, playlists, and search.
4. Media3 playback with background controls.
5. Manual playlists and M3U import/export.
6. Local prompt parser and deterministic Power Mix generation.
7. Optional Gemini interpretation with strict JSON validation.
8. Library Doctor diagnostics.

