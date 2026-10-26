# Driftfin

An Android TV client for your own Jellyfin server, with a TV-first layout (home rows, hero banner, My List, details, search, player). Not affiliated with Jellyfin.

**Status: alpha, TV-only.** Expect rough edges and bugs. It is unit-tested and snapshot-tested, and it has had hands-on use on one Android TV, but that is not broad device coverage. Playback, HLS transcodes, subtitle loading, remote-control focus and scrolling smoothness are still being checked on real hardware, and known issues are open. Do not rely on it as your only way to watch.

## What it does

- Sign in with server address, username and password (profile chips list the server's public users when the server shows them).
- Home rows: My List (your Jellyfin favorites), Continue Watching, Next Up, Recently Added, More like (last watched), libraries.
- Details with seasons and episodes, Play/Resume, mark watched, My List.
- Search, Shows, Movies, My List, Settings tabs.
- Player: seek, audio and text subtitle selection, playback speed, autoplay next episode (switchable).

## Limits

- Text subtitles only (SRT, WebVTT, ASS/SSA). Picture subtitles are not supported.
- No downloads, skip-intro, kids/PIN profiles. No recommendation engine; "More like" is Jellyfin's similar-items list.
- Trailers play from the details page when the server has them.
- Device coverage is thin (see Status). Performance on older or low-power TVs is not measured.
- Server addresses may use plain `http://`, which is common on a home network. Sign-in details and the access token then travel unencrypted on that network, so use `https://` for anything outside your own LAN.

## Build

Android SDK 35, JDK 17, Gradle 8.10.2. `gradle testDebugUnitTest assembleDebug`. Snapshot tests render with Paparazzi; real artwork fixtures are not included in this repository, so renders show placeholders.

## Releases and signing

The workflow in `.github/workflows/android.yml` builds a signed release APK from repository secrets (`JETFLIX_KEYSTORE_B64`, `JETFLIX_KEYSTORE_PASSWORD`, `JETFLIX_KEY_ALIAS`, `JETFLIX_KEY_PASSWORD`). It refuses to build without them. No key is stored in the repo.

## License

MIT. See LICENSE. Driftfin is an independent project, not affiliated with Jellyfin.
