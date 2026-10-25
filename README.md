# Driftfin (formerly Jetflix)

An Android TV client for your own Jellyfin server, with a Netflix-style layout (home rows, hero banner, My List, details, search, player). Original branding only: no Netflix assets. Not affiliated with Netflix or Jellyfin.

**Status: early, TV-only.** Built and unit-tested, but not yet verified on a real TV. Playback, HLS transcodes, subtitle loading and remote-control focus behavior have not been exercised on a device.

## What it does

- Sign in with server address, username and password (profile chips list the server's public users when the server shows them).
- Home rows: My List (your Jellyfin favorites), Continue Watching, Next Up, Recently Added, More like (last watched), libraries.
- Details with seasons and episodes, Play/Resume, mark watched, My List.
- Search, Shows, Movies, My List, Settings tabs.
- Player: seek, audio and text subtitle selection, playback speed, autoplay next episode (switchable).

## Limits

- Text subtitles only (SRT, WebVTT, ASS/SSA). Picture subtitles are not supported.
- No trailers, downloads, skip-intro, kids/PIN profiles. No Netflix catalog or recommendations; "More like" is Jellyfin's similar-items list.
- Unverified on devices.

## Build

Android SDK 35, JDK 17, Gradle 8.10.2. `gradle testDebugUnitTest assembleDebug`. Snapshot tests render with Paparazzi; real artwork fixtures are not included in this repository, so renders show placeholders.

## Releases and signing

The workflow in `.github/workflows/android.yml` builds a signed release APK from repository secrets (`JETFLIX_KEYSTORE_B64`, `JETFLIX_KEYSTORE_PASSWORD`, `JETFLIX_KEY_ALIAS`, `JETFLIX_KEY_PASSWORD`). It refuses to build without them. No key is stored in the repo.

## License

MIT. See LICENSE. Driftfin is an independent project, not affiliated with Netflix or Jellyfin.
