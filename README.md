# Driftfin

An Android TV client for your own Jellyfin server, with a TV-first layout (home rows, hero banner, My List, details, search, player). Not affiliated with Jellyfin.

**Status: alpha, TV-only.** Expect rough edges and bugs. It is unit-tested and snapshot-tested, and it has had hands-on use on one Android TV, but that is not broad device coverage. Playback, HLS transcodes, subtitle loading, remote-control focus and scrolling smoothness are still being checked on real hardware, and known issues are open. Do not rely on it as your only way to watch.

## What it does

- Sign in with server address, username and password (profile chips list the server's public users when the server shows them).
- Home rows: My List (your Jellyfin favorites), Continue Watching, Next Up, Recently Added, More like (last watched), libraries.
- Details with seasons and episodes, Play/Resume, mark watched, My List.
- Search, Shows, Movies, My List, Settings tabs.
- Player: seek, audio and text subtitle selection, playback speed, autoplay next episode (switchable).
- Skip Intro: shows a bottom-right button during an intro that your Jellyfin server has marked (Media Segments, server 10.10 or newer), and only skips when you press OK. It appears only where the server has segment data. Switchable in Settings.
- Title logos on Home and Details where Jellyfin has a logo image, with plain text otherwise. Switchable in Settings.
- Saved profiles: a profile you signed in with stays on the TV (encrypted) so you can switch back without the password. "Sign out and forget this profile" removes it. It does not revoke the token on the server.
- Check for updates (bottom of Settings): only when you ask. It reads the latest GitHub release, downloads the APK, checks its checksum, package name, version and signing certificate, and then opens Android's installer, which asks you to confirm. Android may first ask you to allow installs from Driftfin. The installer step has not been tested on a TV.

## Limits

- Text subtitles only (SRT, WebVTT, ASS/SSA). Picture subtitles are not supported.
- No downloads or kids/PIN profiles. No recommendation engine; "More like" is Jellyfin's similar-items list.
- Trailers play from the details page when the server has them.
- Device coverage is thin (see Status). Performance on older or low-power TVs is not measured.
- Server addresses may use plain `http://`, which is common on a home network. Sign-in details and the access token then travel unencrypted on that network, so use `https://` for anything outside your own LAN.

## Build

Android SDK 35, JDK 17, Gradle 8.10.2. `gradle testDebugUnitTest assembleDebug`. Snapshot tests render with Paparazzi; real artwork fixtures are not included in this repository, so renders show placeholders.

## Releases and signing

The workflow in `.github/workflows/android.yml` builds a signed release APK from repository secrets (`JETFLIX_KEYSTORE_B64`, `JETFLIX_KEYSTORE_PASSWORD`, `JETFLIX_KEY_ALIAS`, `JETFLIX_KEY_PASSWORD`). It refuses to build without them. No key is stored in the repo.

## License

MIT. See LICENSE. Driftfin is an independent project, not affiliated with Jellyfin.
