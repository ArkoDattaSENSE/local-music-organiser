# AudOneOut

AudOneOut is a native Android companion for local music libraries.

> AudOneOut turns messy music metadata into better discovery and portable playlists.

It indexes music with Android MediaStore, helps repair and enrich the catalog, creates local recommendations and mixtapes, and hands tracks, playlists, and radio streams to music apps already installed on the device. AudOneOut is not a music player and does not contain a playback engine.

## What It Does

- Select one or more music roots through Android's folder picker.
- Scan local audio without broad filesystem access.
- Exclude messaging audio, recordings, ringtones, podcasts, audiobooks, cache folders, and custom blacklist rules.
- Browse songs, albums, artists, folders, and playlists with search, sorting, and list or grid views.
- Pin favorite songs to Speed Dial and open them in an installed music player.
- Run Library Doctor checks for missing metadata, inaccessible files, suspicious titles, and possible duplicates.
- Add conservative local discovery signals such as era, lossless quality, mood, character, and energy with stored source and confidence.
- Look up Last.fm metadata, review suggestions, and explicitly write accepted fields back to supported audio files.
- Build offline recommendations from corrected metadata, Speed Dial, local discovery tags, and controlled exploration.
- Read an opt-in public Last.fm taste profile, including recent scrobbles, and find related tracks already in or outside the local library.
- Open online recommendations on Last.fm or as YouTube Music searches.
- Generate local radio mixes from a song or playlist and hand the generated M3U8 to an external player.
- Save direct stream, M3U, or PLS radio addresses and open the resolved stream externally.
- Generate deterministic local mixtapes from natural-language rules.
- Import M3U, M3U8, and CSV playlists.
- Export local-player M3U8 and streaming-migration CSV files.

## Architecture

- Kotlin and Gradle Kotlin DSL
- Jetpack Compose and Material 3
- MVVM with StateFlow
- Hilt dependency injection
- Room with explicit migrations and exported schemas
- MediaStore and Storage Access Framework
- DataStore settings
- WorkManager periodic checks and a debounced MediaStore ContentObserver
- Coil artwork loading
- Android TagLib for user-confirmed, file-descriptor-based metadata writeback

The database separates original file metadata, AudOneOut enrichment, and user-confirmed metadata. Accepting a suggestion updates the AudOneOut catalog. Writing those accepted fields into the audio file is a separate per-track action with a field preview, Android write consent where required, and read-back verification.

## Companion Handoffs

AudOneOut delegates listening to other apps:

- A track is opened through an Android `ACTION_VIEW` content-URI intent.
- An album, artist, folder, playlist, recommendation mix, or local radio is exported to a temporary M3U8 and opened through Android's app chooser.
- An online radio URL is resolved from direct, M3U/M3U8, or PLS input and opened through the chooser.
- Last.fm pages and YouTube Music searches open in the user's browser or associated app.

External-player support varies. The target player must accept content URIs or M3U8 intents and have permission to read the user's audio library.

## Music Roots And Blacklist

Music roots use persistable Storage Access Framework grants. Each root shows its location, indexed tracks, storage use, last scan time, status, include toggle, rescan action, and remove action.

Blacklist rules can match a typed folder name or a selected folder and its descendants. They are local filters only and never delete audio. Disabled or removed rules can be restored from the default suggestions.

## Library Checks

- Manual scans show the current root and folder plus found, new, updated, missing, excluded, and failed counts.
- A manual scan can be cancelled. Running it again resumes from the existing catalog and rechecks changed files.
- MediaStore changes are debounced before an incremental WorkManager job is enqueued.
- Periodic fallback checks can be disabled, scheduled Daily or Weekly, and constrained to charging or device-idle time.
- Missing files are retained as unavailable records rather than immediately deleted.
- New, modified, moved, restored, missing, and excluded changes are retained as scan records.

## Last.fm Setup

1. Create a Last.fm API account at `https://www.last.fm/api/account/create`.
2. In AudOneOut, open Settings and enter the Last.fm username and API key.
3. Never enter the Last.fm shared secret. AudOneOut does not need it.
4. Open Discover, then select the Last.fm tab.

AudOneOut reads public profile information, recent scrobbles, top tracks, loved tracks, similar tracks, and explicit track metadata lookups. It sends the configured username and artist/title metadata. It does not upload audio, authenticate as the user, submit scrobbles, or control Last.fm playback.

Scrobbling remains the responsibility of the external music player or a dedicated scrobbler.

## Playlists And Migration

M3U8 exports use local content references for compatible Android players. CSV exports include position, title, artist, album, album artist, year, duration, identifier placeholders, streaming candidate columns, confidence, and notes.

After a CSV export, AudOneOut shows the Soundiiz handoff steps. AudOneOut never signs in to or modifies a streaming-service account.

## Build Locally

Requirements:

- Android SDK 34
- JDK 11 or 17

```bash
./gradlew testDebugUnitTest assembleDebug
```

The APK is generated under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

On the current development workstation, the verified command is:

```bash
env JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64 \
  GRADLE_USER_HOME="$PWD/.gradle-user-home" \
  TMPDIR="$PWD/.gradle-user-home/tmp" \
  ./gradlew --max-workers=1 -Dkotlin.incremental=false \
  -Pandroid.aapt2FromMavenOverride="$HOME/Android/Sdk/build-tools/34.0.0/aapt2" \
  testDebugUnitTest assembleDebug
```

## Permissions

- Android 13 and newer: `READ_MEDIA_AUDIO`
- Android 10 through 12: `READ_EXTERNAL_STORAGE`
- `INTERNET` and network state for opt-in Last.fm and radio URL resolution
- Notifications for configurable background-check results

AudOneOut does not request `MANAGE_EXTERNAL_STORAGE`.

## CI And APK Releases

`.github/workflows/android-build.yml` runs unit tests, Android lint, and `assembleDebug` on pull requests and pushes to `main`, then uploads the detected APK as an Actions artifact.

After a successful `main` build, `.github/workflows/android-release-apk.yml` publishes `AudOneOut-debug.apk` to a versioned GitHub Release. It also supports `v*` tag pushes and manual dispatches.

## Privacy

- No account is required for local features.
- No analytics are included.
- No audio is uploaded.
- No broad storage permission is requested.
- No listening history is uploaded by default.
- Online features are opt-in and identify the metadata they send.
- No file tags are changed automatically.
- File writeback happens only after a per-track preview and explicit confirmation.
- No streaming-service account is accessed.

## Current Limitations

- Release downloads are debug-signed testing APKs, not Play Store production artifacts.
- Last.fm uses a user-supplied API key; authenticated write actions and scrobble submission are intentionally absent.
- File writeback supports common TagLib formats including FLAC, MP3, Ogg/Opus, MP4/M4A, WAV, AIFF, WMA, APE, WavPack, Musepack, and DSF; unsupported or rejected fields remain catalog-only.
- Sample rate and bit depth depend on metadata exposed by Android and the file format.
- Some external music players do not accept M3U8 content-URI handoffs.
- Online radio depends on the target player supporting the resolved stream format.
- Gemini prompt interpretation is not bundled; mixtape interpretation is deterministic and local.
