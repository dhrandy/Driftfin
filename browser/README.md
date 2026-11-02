# Driftfin Browser

[![Status: alpha](https://img.shields.io/badge/status-alpha-orange)](https://github.com/dhrandy/Driftfin/releases)
[![License: proprietary](https://img.shields.io/badge/license-proprietary-blue)](../LICENSE)

**Status: alpha.** Expect rough edges and bugs. The app is exercised
end to end in automated browser tests against a mock Jellyfin server,
but real-server and real-device coverage is still limited, and known
issues are open. Please file issues on the repo.

Self-hosted web app, part of Driftfin. Browser-first, responsive at
desktop, tablet, and phone. Same feature and behavior direction as
the TV and phone apps, in a browser.

This app is browser-only. It does not edit, build, or touch any
TV or phone app code.

## Install

The app runs as one Docker container that downloads the current code
from GitHub on first start (pull-on-startup). There is NO published
image, NO Dockerfile, and NO GitHub Actions build for this app. Any
Docker host that can run a compose file works. The two documented
routes below are plain Docker Compose and Dockhand.

On the first start only, the container:

1. Downloads the current Driftfin main tarball from GitHub.
2. Unpacks it and copies ONLY `browser/` to `/app`.
3. Runs `npm ci --omit=dev` (no dependencies yet, so this is fast).
4. Starts `node src/server.js`.

Later restarts just start the server from the code already saved in
`/app`. They do NOT download or rebuild again.

### Option 1: Docker Compose (plain Docker)

1. Save the paste-ready stack below as `docker-compose.yml`.
2. Create a `.env` file next to it with your values:

```ini
DRIFTFIN_PORT=8740
JELLYFIN_URL=
JELLYFIN_LOCKED=
TZ=America/New_York
COOKIE_SECURE=false
FORCE_UPDATE=false
```

3. `docker compose up -d`. The first start downloads and installs
   the code, which takes a minute; later starts are instant.
4. Open `http://your-host:8740`, or point your reverse proxy at that
   port (see below).

### Option 2: Dockhand

1. Create a new app named `driftfin-browser`.
2. Paste the stack below as the stack file.
3. Set the same variables in the Dockhand Environment tab (Dockhand
   does not read `.env`). Enter plain values, no `${VAR}` and no
   `:-` default suffixes.
4. Deploy.

### Environment

| Variable | Example / default | Notes |
| --- | --- | --- |
| `DRIFTFIN_PORT` | `8740` | Host port. |
| `JELLYFIN_URL` | (blank) | Optional server URL prefill on the sign-in page only. No username, password, or token goes here. |
| `JELLYFIN_LOCKED` | (blank) | `1` locks sign-in to `JELLYFIN_URL`: the form's server field is ignored and sessions for any other server are rejected. Fails closed if `JELLYFIN_URL` is empty. Leave unset to keep the open form. |
| `TZ` | `America/New_York` | Timezone. |
| `COOKIE_SECURE` | `false` | `true` ONLY behind an HTTPS reverse proxy. Use `false` for plain LAN http, or the session cookie will not be sent and sign-in will loop. |
| `FORCE_UPDATE` | `false` | Set to `true` only when you want to re-download and rebuild from GitHub (see Updating). |

### Paste-ready stack

```yaml
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
      - JELLYFIN_URL=${JELLYFIN_URL}
      - JELLYFIN_LOCKED=${JELLYFIN_LOCKED}
      - TZ=${TZ}
      - COOKIE_SECURE=${COOKIE_SECURE}
      - FORCE_UPDATE=${FORCE_UPDATE}
    volumes:
      # Settings, sessions, and the session secret persist here.
      - driftfin-browser-data:/data
      # App code persists here, so it is only downloaded and
      # installed on the first start, not on every restart.
      - driftfin-browser-app:/app
    ports:
      # Host interface, NOT 127.0.0.1, so the reverse proxy can reach it.
      - "${DRIFTFIN_PORT}:8740"
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

volumes:
  driftfin-browser-data:
  driftfin-browser-app:
```

The container is `read_only: true` with `cap_drop: ALL` and tmpfs
`/tmp`, plus a healthcheck on `GET /api/health`.

### Updating

A running app does not update itself. To update to the newest
Driftfin main:

- Compose CLI: set `FORCE_UPDATE=true` in `.env`, run
  `docker compose up -d --force-recreate`, and wait for the
  healthcheck to go green. Then set it back to `false` and recreate
  once more, so later restarts do not rebuild again.
- Dockhand: set `FORCE_UPDATE=true` in the Environment tab, recreate
  the stack, then set it back to `false` and recreate again.

Persistent state survives updates in the two named volumes:
`driftfin-browser-data` (settings, sessions, session secret) and
`driftfin-browser-app` (the downloaded app code).

### Reverse proxy and HTTPS

If you front the app with a reverse proxy, forward it to
`http://your-host:8740` (the compose port mapping has no
`127.0.0.1` prefix, so the proxy can reach it). Set
`COOKIE_SECURE=true` only when the proxy serves HTTPS.

To install the app on a phone home screen (Add to Home Screen as a
standalone app), the app must be served over HTTPS. On plain LAN
http, browsers only offer a normal bookmark shortcut.

### Important: this stack pulls browser/ from Driftfin main

The stack downloads the Driftfin main tarball on first start and
copies `browser/` from it. If `browser/` is ever removed from main
upstream, it will fail at startup at the copy step
(`cp -a /tmp/driftfin-src/browser/.`). Do not "fix" it by mounting
a local source volume as the primary path.

## What is in this folder

This folder is the `browser/` folder in the Driftfin repo
(https://github.com/dhrandy/Driftfin).

- `src/server.js` - Node stdlib only, no build step. Serves the
  frontend, proxies Jellyfin server-side, holds the Jellyfin token
  server-side keyed by a signed session cookie, and stores per-user
  settings server-side.
- `public/index.html`, `public/styles.css`, `public/app.js` - plain
  HTML/CSS/JS, no build step, no bundler.
- `package.json`, `package-lock.json` - no runtime dependencies in
  v1, so `npm ci --omit=dev` is fast. The lockfile exists so `npm ci`
  works in the container.
- `docker-compose.yml` - the compose file the maintainer deploys
  with. The paste-ready stack in the Install section is the
  recommended generic version (named volumes, no host paths).
- `public/vendor/hls.min.js` - hls.js v1.7.4 for HLS playback in
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

- Downloads and kids PIN profiles. Neither appears in the app UI.

## Status and testing

Alpha. The flows that touch Jellyfin (sign-in, library, playback,
subtitles, Skip Intro, autoplay) are covered by automated
end-to-end browser tests that run against a mock Jellyfin server
through the real app server, and the compose deployment path is
exercised in real use. That is still not broad device or browser
coverage: expect rough edges, and please file issues on the repo
for anything that misbehaves on your setup.

## Getting help

If you use an AI assistant to troubleshoot your install, share only
the relevant log excerpt, not a raw log dump, and remove anything
private first: server URLs and IP addresses, usernames, passwords,
tokens, cookies, and auth headers. A prompt that works well:

```
I'm installing Driftfin Browser, a self-hosted Jellyfin web client
that runs as one Docker container (pull-on-startup compose, no
published image). Here is my compose file with private values
removed: [paste]. Here is the relevant excerpt from the container
logs, with URLs, tokens, cookies, and auth headers removed: [paste].
I expected: [describe]. What is wrong and how do I fix it?
```

## License

Driftfin is free for personal use, not free to fork or republish.
See `LICENSE` at the repo root. The bundled hls.js player is
Apache-2.0; see `THIRD_PARTY_NOTICES.md`.
