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

Randy runs this with Docker Compose via Dockhand on his NAS. There
is no other run path documented here.

This is a pull-on-startup compose: the container downloads the code on startup, there is no published image and no build step.
There is NO published image, NO Dockerfile, and NO GitHub Actions
build for this app.

1. In Dockhand, create a new app named `driftfin-browser`.
2. Paste the complete stack below as the stack file.
3. In the Dockhand Environment tab (not a `.env` file), set the
   variables in the table below.
4. Deploy. On start the container downloads the current Driftfin
   main tarball from GitHub, unpacks it, copies ONLY `browser/`
   to the tmpfs `/app`, runs `npm ci --omit=dev`, and starts the
   server.
5. Point your reverse proxy at the host port (8740 by default).
   The container itself binds `0.0.0.0:8740` inside, and the compose
   port mapping has no `127.0.0.1` prefix, so the reverse proxy
   can reach it.

To update: recreate / restart the stack. A running app does not
update itself. Persistent state lives only in the bind volume:

```
/DATA/AppData/driftfin-browser:/data
```

That volume holds `settings.json`, `sessions.json`, and
`session-secret`. Everything in `/app` is tmpfs and is rebuilt on
every start. The container is `read_only: true` with
`cap_drop: ALL` and tmpfs `/app` and `/tmp`, and a healthcheck on
`GET /api/health`.

### Environment (Dockhand Environment tab)

| Variable | Value / default | Notes |
| --- | --- | --- |
| `DRIFTFIN_PORT` | `8740` | Host port, default 8740. |
| `JELLYFIN_URL` | (blank) | Optional server URL prefill on the sign-in page only. No username, password, or token goes here. |
| `TZ` | `America/New_York` | Timezone. |
| `COOKIE_SECURE` | `false` | `true` ONLY behind an HTTPS reverse proxy. Use `false` for plain LAN http, or the session cookie will not be sent and sign-in will loop. |

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
      - /app
      - /tmp
    environment:
      - PORT=8740
      - DATA_DIR=/data
      - JELLYFIN_URL=${JELLYFIN_URL:-}
      - TZ=${TZ:-America/New_York}
      - COOKIE_SECURE=${COOKIE_SECURE:-false}
    volumes:
      # Settings, sessions, and the session secret persist here.
      # Everything else in /app is tmpfs and is rebuilt on every start.
      - /DATA/AppData/driftfin-browser:/data
    ports:
      # Host interface, NOT 127.0.0.1, so the reverse proxy can reach it.
      - "${DRIFTFIN_PORT:-8740}:8740"
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8740/api/health || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 120s
    command: >-
      sh -c 'set -e;
      export NPM_CONFIG_CACHE=/tmp/npm-cache;
      mkdir -p /tmp/npm-cache;
      rm -rf /tmp/driftfin-src /tmp/driftfin.tar.gz;
      wget -qO /tmp/driftfin.tar.gz https://codeload.github.com/dhrandy/Driftfin/tar.gz/refs/heads/main;
      mkdir -p /tmp/driftfin-src;
      tar -xzf /tmp/driftfin.tar.gz -C /tmp/driftfin-src --strip-components=1;
      cp -a /tmp/driftfin-src/browser/. /app/;
      rm -rf /tmp/driftfin-src /tmp/driftfin.tar.gz;
      cd /app;
      npm ci --omit=dev;
      exec node src/server.js'
```

### Important: this stack fails until browser/ is merged upstream

Until `browser/` exists on Driftfin main, this compose will fail
at startup at the copy step (`cp -a /tmp/driftfin-src/browser/.`).
That is expected, and it is the signal that the merge has not
happened yet. Do not "fix" it by mounting a local source volume
as the primary path; that was superseded by this pull-on-startup
pattern. Do not push to GitHub from here; that merge is a separate
step for Randy/Wade.

## What is in this folder

This folder maps 1:1 to the future `browser/` folder in the Driftfin
repo (https://github.com/dhrandy/Driftfin).

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
`/api/hls/.../master.m3u8`. Audio and subtitle lists come from
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
- A bundled HLS player library: a transcode that needs HLS will
  not play in a browser with no native HLS support. Direct streams
  are unaffected.

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
  Dockhand deploy to prove it, after browser/ is merged upstream.
- The responsive layout has not been checked on a real phone or
  tablet, only by breakpoint rules in `styles.css`.
- Actual video rendering in a real browser was not tested: the
  mock tests verified the stream, HLS playlist, and subtitle
  endpoints the `<video>` element loads, but no browser played
  a file here.

## Open questions

1. Is the `browser/` folder name and the `8740` default port right,
   or do you want a different port?
2. Is LAN + Twingate the intended access route for v1, or should
   this also be public behind the reverse proxy from day one?
   That decides `COOKIE_SECURE` and whether server-side sessions
   need any hardening beyond this build.
