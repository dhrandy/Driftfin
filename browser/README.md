# Driftfin Browser v1

Self-hosted web app, part of Driftfin. Browser-first, responsive at
desktop, tablet, and phone. Same feature and behavior direction as
the TV and phone apps, in a browser.

Design: concept 1 overall layout, concept 2 shorter banner. The hero
is deliberately not full viewport, so rows start sooner.

Logo: text placeholder only (`DRIFTFIN` in the header). The logo is
NOT final, a white-fin variation is being explored, and whatever is
chosen will be the same across TV, phone, and browser. Do not treat
this placeholder as locked, and do not change any existing TV or
phone logos.

This app is browser-only. It does not edit, build, or touch any
TV or phone app code.

## Deploy in Dockhand (the only run path)

Deploy with Docker Compose via Dockhand (or any compose runner).
There is no other run path documented here.

This is a pull-on-startup compose: the container downloads the code on startup, there is no published image and no build step.
There is NO published image, NO Dockerfile, and NO GitHub Actions
build for this app.

1. In Dockhand, create a new app named `driftfin-browser`.
2. Paste the complete stack below as the stack file.
3. In the Dockhand Environment tab (not a `.env` file), set the
   variables in the table below.
4. Deploy. On the first start only, the container downloads the
   current Driftfin main tarball from GitHub, unpacks it, copies
   ONLY `browser/` to `/app`, runs `npm ci --omit=dev`, and starts
   the server. Later restarts just start the server from the code
   already saved in `/app`, they do NOT download or rebuild again.
5. Point your reverse proxy at the host port (8740 by default).
   The container itself binds `0.0.0.0:8740` inside, and the compose
   port mapping has no `127.0.0.1` prefix, so the reverse proxy
   can reach it.

To update: set `FORCE_UPDATE=true` in the Dockhand Environment
tab, then recreate the stack. After it updates, set
`FORCE_UPDATE` back to `false`, so later restarts do not rebuild
again. A running app does not update itself. Persistent state
lives in these bind volumes:

```
/DATA/AppData/driftfin-browser:/data
/DATA/AppData/driftfin-browser-app:/app
```

The first volume holds `settings.json`, `sessions.json`, and
`session-secret`. The second holds the app code, so it is only
downloaded and installed on the first start. The container is
`read_only: true` with `cap_drop: ALL` and tmpfs `/tmp`, and a
healthcheck on `GET /api/health`.

### Environment (Dockhand Environment tab)

| Variable | Value / default | Notes |
| --- | --- | --- |
| `DRIFTFIN_PORT` | `8740` | Host port, default 8740. |
| `JELLYFIN_URL` | (blank) | Optional server URL prefill on the sign-in page only. No username, password, or token goes here. |
| `TZ` | `America/New_York` | Timezone. |
| `COOKIE_SECURE` | `false` | `true` ONLY behind an HTTPS reverse proxy. Use `false` for plain LAN http, or the session cookie will not be sent and sign-in will loop. |
| `FORCE_UPDATE` | `false` | Set to `true` only when you want to re-download and rebuild from GitHub, then set it back to `false`. |

### Paste-ready stack

```yaml
# Driftfin Browser - Dockhand / CasaOS compose.
#
# Pull-on-startup, adapted for the browser/ folder in
# the Driftfin repo. There is NO published image, NO Dockerfile, and
# NO GitHub Actions build for this app.
#
# First container start only, this stack:
#   1. Downloads the current Driftfin main tarball from GitHub.
#   2. Unpacks it and copies ONLY browser/ to /app.
#   3. Runs npm ci --omit=dev (no dependencies in v1, so this is fast).
#   4. Starts node src/server.js.
# After that, restarts just start the server from the code already
# saved in /app. It does NOT download or rebuild on every restart.
# To update to a newer GitHub version: set FORCE_UPDATE=true in
# the Dockhand Environment tab, then recreate the stack. After it
# updates, set FORCE_UPDATE back to false, so later restarts do
# not rebuild again. A running app does not update itself.
#
# IMPORTANT: this compose pulls browser/ from the Driftfin repo on
# main. It will fail at startup with a copy error if browser/ is
# ever removed from main upstream.
#
# Environment is set in the Dockhand Environment tab, not a .env file.
#   DRIFTFIN_PORT   host port, default 8740.
#   JELLYFIN_URL    optional server URL prefill on the sign-in page.
#                   No username, password, or token goes here.
#   TZ              timezone, default America/New_York.
#   COOKIE_SECURE   "true" ONLY behind an HTTPS reverse proxy.
#                   Use "false" for plain LAN http, or the session
#                   cookie will not be sent and sign-in will loop.
services:
  driftfin-browser:
    image: node:22-alpine
    container_name: driftfin-browser
    working_dir: /app
    restart: unless-stopped
    read_only: true
    cap_drop:
      - ALL
    tmpfs:
      - /tmp
    environment:
      - PORT=8740
      - DATA_DIR=/data
      - JELLYFIN_URL=${JELLYFIN_URL:-}
      - TZ=${TZ:-America/New_York}
      - COOKIE_SECURE=${COOKIE_SECURE:-false}
      - FORCE_UPDATE=${FORCE_UPDATE:-false}
    volumes:
      # Settings, sessions, and the session secret persist here.
      - /DATA/AppData/driftfin-browser:/data
      # App code persists here, so it is only downloaded and
      # installed on the first start, not on every restart.
      - /DATA/AppData/driftfin-browser-app:/app
    ports:
      # Host interface, NOT 127.0.0.1, so the reverse proxy can reach it.
      - "${DRIFTFIN_PORT:-8740}:8740"
    healthcheck:
      test: ["CMD", "node", "-e", "fetch('http://127.0.0.1:8740/api/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 180s
    command: >-
      sh -c 'set -e;
      export NPM_CONFIG_CACHE=/tmp/npm-cache;
      mkdir -p /tmp/npm-cache;
      if [ "$$FORCE_UPDATE" = "true" ] || [ ! -f /app/.driftfin-installed ] || [ ! -f /app/src/server.js ]; then
      rm -rf /tmp/driftfin-src /tmp/driftfin.tar.gz;
      wget -qO /tmp/driftfin.tar.gz https://codeload.github.com/dhrandy/Driftfin/tar.gz/refs/heads/main;
      mkdir -p /tmp/driftfin-src;
      tar -xzf /tmp/driftfin.tar.gz -C /tmp/driftfin-src --strip-components=1;
      rm -rf /app/src /app/public /app/package.json /app/package-lock.json /app/.driftfin-installed;
      cp -a /tmp/driftfin-src/browser/. /app/;
      rm -rf /tmp/driftfin-src /tmp/driftfin.tar.gz;
      cd /app;
      npm ci --omit=dev;
      touch /app/.driftfin-installed;
      fi;
      cd /app;
      exec node src/server.js'
```

### Important: this stack pulls browser/ from Driftfin main

This compose downloads the Driftfin main tarball on startup and
copies browser/ from it. If browser/ is ever removed from main
upstream, it will fail at startup at the copy step
(`cp -a /tmp/driftfin-src/browser/.`). Do not "fix" it by mounting
a local source volume as the primary path.

## What is in this folder

This folder is the `browser/` folder in the Driftfin repo
(https://github.com/dhrandy/Driftfin).

- `src/server.js` — Node stdlib only, no build step. Serves the
  frontend, proxies Jellyfin server-side, holds the Jellyfin token
  server-side keyed by a signed session cookie, and stores per-user
  settings server-side.
- `public/index.html`, `public/styles.css`, `public/app.js` — plain
  HTML/CSS/JS, no build step, no bundler.
- `package.json`, `package-lock.json` — no runtime dependencies in
  v1, so `npm ci --omit=dev` is fast. The lockfile exists so `npm ci`
  works in the container.
- `docker-compose.yml` — the Dockhand stack above.
- `public/vendor/hls.min.js` — hls.js v1.7.4 for HLS playback in
  browsers without native HLS. License: `THIRD_PARTY_NOTICES.md`.

## How it works

Sign-in takes a Jellyfin server URL, username, and password. The
server calls Jellyfin `Users/AuthenticateByName`, stores the access
token in `DATA_DIR/sessions.json` (server-side only), and sets a
signed, HttpOnly session cookie. Browser JS never sees the token.

Settings are stored per Jellyfin user in `DATA_DIR/settings.json`,
so they follow the user between devices. Jellyfin itself already
stores favorites, Continue Watching, and watched status, so those
also follow the user between devices without this app inventing its
own watchlist.

Playback: the frontend asks Jellyfin for `PlaybackInfo` with a
browser DeviceProfile, then plays through this server. Direct
stream through `/api/direct/...` when the server says the source
supports direct play/stream, otherwise HLS through
`/api/hls/.../master.m3u8`. Browsers without native HLS (desktop
Chrome, Firefox, Edge) use the bundled hls.js player
(`public/vendor/hls.min.js`, Apache-2.0, see THIRD_PARTY_NOTICES.md).
Leaving the player stops the video and reports playback stopped to
Jellyfin. Audio defaults to the remembered preference (English by
default, like the TV apps) and audio/subtitle choices persist. Audio and subtitle lists come from
`PlaybackInfo` media streams. Subtitle tracks load through the
same-origin proxy, so no token is in the page. Skip Intro uses
Jellyfin `MediaSegments/{itemId}?includeSegmentTypes=Intro` and
only shows a button while the playhead is inside a reported intro
segment, and only seeks when pressed. Autoplay next episode shows
the TV-style 5-second Up Next card.

Pages, all calling the real Jellyfin API, not demo data:
Home (hero, Continue Watching, Next Up, Recently Added Movies,
Recently Added Episodes, Favorites, More like the last played
title, library rows), Shows and Movies grids with a Kids filter
(All / Kids only / No kids, exact library names "Kids Movies" and
"Kids Shows"), Details (seasons with full season switching,
episodes with Play/Resume and watched status per episode,
Play/Resume naming the episode, Play from start, mark watched on
the play-target episode, My List), Search, Settings, and Player
(seek, audio/subtitle selection, playback speed, autoplay,
Skip Intro).

If the Jellyfin server is unreachable, pages show an error state,
not hard-coded demo titles.

## What is NOT in v1

- Quick Connect sign-in (username/password only in v1).
- Cast while paused, trailers, downloads, kids PIN profiles.
  None of these appear in the app UI.

## Not verified (no real Jellyfin server was available for testing)

- No sign-in, library load, playback, subtitle, Skip Intro, or
  autoplay flow has been tested against a real Jellyfin server.
  Those paths were tested end to end against a local mock Jellyfin
  server returning the TV app's API shapes, through the real
  browser server (see the test report delivered with this build),
  but they need a live server check before anyone calls this
  working on a real library.
- The Dockhand compose has not been run in Dockhand, and the
  pull-on-startup script has not been run in a `node:22-alpine`
  container. It parses as
  valid YAML with the expected service keys, but it needs a real
  Dockhand deploy to prove it.
- The responsive layout has not been checked on a real phone or
  tablet, only by breakpoint rules in `styles.css`.
- Playback was exercised in headless Chrome against a mock
  Jellyfin: a direct stream plays, an HLS transcode plays through
  the bundled hls.js and the playlist-rewriting proxy, and leaving
  the player stops the video and reports Stopped. A real Jellyfin
  server transcode still needs a live check.

## Open questions

1. Is the `browser/` folder name and the `8740` default port right,
   or do you want a different port?
2. Is LAN + Twingate the intended access route for v1, or should
   this also be public behind the reverse proxy from day one?
   That decides `COOKIE_SECURE` and whether server-side sessions
   need any hardening beyond this build.
