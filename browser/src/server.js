#!/usr/bin/env node
/**
 * Driftfin Browser - v1 server. Node stdlib only, no build step.
 *
 * This file is the whole backend. It starts from the Dockhand compose
 * stack (see docker-compose.yml and README.md): on container start the
 * Pull-on-startup downloads the Driftfin repo tarball,
 * only browser/ is copied to /app, npm ci runs, and this file starts.
 * There is no build, no bundler, and no GitHub Actions workflow.
 *
 * What it does:
 *  - Serves the static frontend from ../public (or PUBLIC_DIR).
 *  - Proxies Jellyfin API calls server-side at /api/jellyfin/<path>.
 *    The Jellyfin access token is held server-side only, keyed by a
 *    signed session cookie. Browser JS never sees the Jellyfin token.
 *  - Stores sessions, the session secret, and per-user app settings
 *    in DATA_DIR (default /data). In the compose stack that directory
 *    is a persistent bind volume mounted at /data, so sessions
 *    and settings survive container restarts and recreates. The
 *    session secret file is created on first start if it is missing.
 *  - Streams video through /api/direct/... and /api/hls/... so a
 *    <video> element never needs a Jellyfin token or api_key in a URL.
 *  - Health endpoint for the container healthcheck: GET /api/health
 *    (and GET /health), no auth required.
 *
 * What it does NOT do in v1:
 *  - Quick Connect sign-in (username/password only in v1).
 *  - HLS playlist rewriting is best-effort: relative playlist and
 *    segment lines are rewritten to the same proxy path. It has NOT
 *    been verified against a real Jellyfin server (see README.md).
 *
 * Environment (set in the Dockhand Environment tab, never a .env file):
 *  PORT           internal port, default 8740
 *  DATA_DIR       settings/sessions directory, default /data
 *  PUBLIC_DIR     frontend directory, default <repo>/browser/public
 *  JELLYFIN_URL   optional server URL prefill shown on sign-in, no secret
 *  TZ             timezone, e.g. America/New_York
 *  COOKIE_SECURE  "true" only behind an HTTPS reverse proxy, else "false"
 */
'use strict';

const http = require('http');
const https = require('https');
const dns = require('dns');
const net = require('net');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { URL } = require('url');

const PORT = parseInt(process.env.PORT || '8740', 10);
const DATA_DIR = process.env.DATA_DIR || '/data';
const PUBLIC_DIR = process.env.PUBLIC_DIR || path.join(__dirname, '..', 'public');
const COOKIE_SECURE = String(process.env.COOKIE_SECURE || '').toLowerCase() === 'true';
const VERSION = '0.3.6';
// Jellyfin revokes every existing token for a (user, DeviceId) pair on each new
// login (SessionManager.GetAuthorizationToken: "Logout any existing sessions for
// the user on this device"), so one fixed DeviceId lets any sign-in kick the
// user's other devices off. Every sign-in below mints its own random DeviceId,
// stored in the session record; sessions saved before the field existed fall
// back to the legacy fixed ID so their tokens are not silently broken.
const LEGACY_DEVICE_ID = 'driftfin-browser';
function newDeviceId() { return 'driftfin-' + crypto.randomBytes(12).toString('hex'); }
function clientHeader(deviceId) {
  return 'MediaBrowser Client="Driftfin Browser", Device="Browser", DeviceId="' + (deviceId || LEGACY_DEVICE_ID) + '", Version="' + VERSION + '"';
}
function authHeader(session) {
  return clientHeader(session.deviceId || LEGACY_DEVICE_ID) + ', Token="' + session.token + '"';
}

const DEFAULT_SETTINGS = {
  titleLogos: true,
  showFormatBadges: true,
  skipIntro: true,
  autoplay: true,
  excludeAnimatedFromBanner: false,
  // TV AppSettings parity (browser-applicable): home rows, banner, search, artwork choices.
  homeBanner: true,
  heroRatings: true,
  showMyListOnHome: true,
  showNextUpOnHome: true,
  homeAutoRefresh: true,
  searchRecommendations: true,
  showWatchlist: true,
  continueWatchingPosters: false,
  // TV "Episode cast from TVMaze" parity (TvmazeCast.kt): verified season guest credits, no key.
  tvmazeEpisodeCast: true,
  posterSize: 'Medium',
  // Player track preferences (TV AppSettings.kt: English audio, subtitles off).
  audioLanguage: 'eng',
  subtitleLanguage: null,
  // Library grid sort, saved between sessions like the TV apps.
  librarySort: 'SortName',
  librarySortOrder: 'Ascending',
  // TV LibraryScreen My Stuff parity: sort/filter choices saved per user.
  mystuffSort: 'Name',
  mystuffSortOrder: 'Ascending',
  mystuffWatch: '',
  mystuffGenre: null,
  mystuffYear: null,
  // TV pause screen parity: summary + Up next + cast while paused.
  pauseInfo: true,
  // TV profile parity: avatar pick + display mode + shown name. Driftfin display
  // settings only; the Jellyfin account name and image are never touched.
  avatarId: 0,
  avatarDisplay: 'username',
  shownName: null,
};

function ensureDir(dir) { fs.mkdirSync(dir, { recursive: true }); }
function readJsonFile(file, fallback) {
  try { const v = JSON.parse(fs.readFileSync(file, 'utf8')); return v && typeof v === 'object' ? v : fallback; }
  catch { return fallback; }
}
function writeJsonFile(file, data, mode) {
  ensureDir(path.dirname(file));
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
  if (mode) fs.chmodSync(tmp, mode);
  fs.renameSync(tmp, file);
}
function sessionSecret() {
  const file = path.join(DATA_DIR, 'session-secret');
  try { const s = fs.readFileSync(file, 'utf8').trim(); if (s) return s; } catch {}
  const secret = crypto.randomBytes(32).toString('hex');
  ensureDir(DATA_DIR);
  fs.writeFileSync(file, secret + '\n', { mode: 0o600 });
  return secret;
}
function signSessionId(id, secret) {
  return crypto.createHmac('sha256', secret).update(id).digest('hex').slice(0, 32);
}
function parseCookies(req) {
  const out = {};
  for (const part of (req.headers.cookie || '').split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}
function getSession(req) {
  const raw = parseCookies(req).driftfin_session;
  if (!raw) return null;
  const [id, sig] = raw.split('.');
  const expected = id ? signSessionId(id, sessionSecret()) : '';
  if (!id || !sig || sig.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null;
  const sessions = readJsonFile(path.join(DATA_DIR, 'sessions.json'), {});
  const sess = sessions[id] || null;
  // Sessions expire after 30 days; the Jellyfin token inside dies with them.
  if (sess && Date.parse(sess.createdAt || '') + 30 * 86400000 < Date.now()) { delete sessions[id]; writeJsonFile(path.join(DATA_DIR, 'sessions.json'), sessions, 0o600); return null; }
  return sess;
}
// When the operator locks the deployment to one Jellyfin target, sessions
// created against any other server (e.g. before the lock was enabled) are no
// longer honored - the fixed target cannot be bypassed through old sessions.
function sessionAllowedForTarget(session) {
  if (process.env.JELLYFIN_LOCKED !== '1') return true;
  return session.server === String(process.env.JELLYFIN_URL || '').trim();
}
function saveSession(session) {
  const file = path.join(DATA_DIR, 'sessions.json');
  const all = readJsonFile(file, {});
  all[session.id] = session;
  writeJsonFile(file, all, 0o600);
}
function dropSession(req) {
  const raw = parseCookies(req).driftfin_session;
  if (!raw) return;
  const id = raw.split('.')[0];
  const file = path.join(DATA_DIR, 'sessions.json');
  const all = readJsonFile(file, {});
  delete all[id];
  writeJsonFile(file, all, 0o600);
}
// Auth cookie lifetime matches the 30-day server-side session TTL, so a browser
// restart (or a phone killing the browser) no longer forces a fresh sign-in.
const SESSION_COOKIE_MAX_AGE_SECONDS = 30 * 86400;
function cookieHeader(sessionId, maxAge) {
  let c = `driftfin_session=${sessionId}.${signSessionId(sessionId, sessionSecret())}; HttpOnly; SameSite=Lax; Path=/`;
  if (COOKIE_SECURE) c += '; Secure';
  if (maxAge !== undefined) c += `; Max-Age=${maxAge}`;
  return c;
}
// Device identity: a long-lived, HMAC-signed cookie gives each browser ONE stable
// Jellyfin DeviceId across sign-ins. A repeat login from the same browser then
// replaces only that browser's own previous token server-side (Jellyfin revokes
// same user+DeviceId), instead of minting a new DeviceId per login and piling up
// dead session rows (dashboard "Active Devices" cards) and registered devices.
// The cookie is random, carries no user data, and survives sign-out and server
// restarts; the HMAC stops a client from forging another browser's DeviceId.
const DEVICE_COOKIE_MAX_AGE_SECONDS = 400 * 86400; // ~the browser cookie lifetime cap
function deviceCookieHeader(deviceId, maxAge) {
  let c = `driftfin_device=${deviceId}.${signSessionId(deviceId, sessionSecret())}; HttpOnly; SameSite=Lax; Path=/`;
  if (COOKIE_SECURE) c += '; Secure';
  if (maxAge !== undefined) c += `; Max-Age=${maxAge}`;
  return c;
}
function browserDeviceId(req) {
  const raw = parseCookies(req).driftfin_device;
  if (!raw) return null;
  const dot = raw.lastIndexOf('.');
  if (dot <= 0) return null;
  const id = raw.slice(0, dot);
  const sig = raw.slice(dot + 1);
  if (!/^driftfin-[a-f0-9]{24}$/.test(id)) return null;
  const expected = signSessionId(id, sessionSecret());
  if (sig.length !== expected.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expected))) return null;
  return id;
}
// Framing/referrer hardening applied to every response.
const SEC_HEADERS = { 'X-Frame-Options': 'DENY', 'Referrer-Policy': 'no-referrer' };
function sendJson(res, status, obj, extraHeaders) {
  const body = Buffer.from(JSON.stringify(obj), 'utf8');
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': body.length, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS, ...(extraHeaders || {}) });
  res.end(body);
}
const MAX_BODY = 1024 * 1024; // 1 MB - API payloads are small; no unbounded buffering.
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    let over = false;
    req.on('data', (c) => {
      if (over) return; // keep draining so the 413 response can be written.
      size += c.length;
      if (size > MAX_BODY) { over = true; reject(new Error('payload_too_large')); return; }
      chunks.push(c);
    });
    req.on('end', () => { if (!over) resolve(Buffer.concat(chunks)); });
    req.on('error', (e) => { if (!over) reject(e); });
  });
}
function readJsonBody(req) {
  return readBody(req).then((buf) => {
    if (!buf.length) return {};
    try { return JSON.parse(buf.toString('utf8')); } catch { return {}; }
  });
}

// Login abuse guard: sliding window of failed sign-ins. Two layers:
//  - 10 failures per client IP + username pair in 10 minutes -> 429 with
//    Retry-After for that pair only. Behind a reverse proxy where every
//    client shares the proxy's socket IP, one person's failures (or an
//    attacker guessing one account) can never lock out other usernames.
//  - 50 failures per client IP in 10 minutes -> 429 for the IP, so an
//    attacker rotating usernames is still capped.
// A successful sign-in clears the pair's record and the windows expire on
// their own, so a real user who mistypes a few times is never locked out
// permanently. For per-client accuracy behind a proxy the operator must set
// TRUST_PROXY=1 (only when the proxy strips client-supplied forwarding
// headers); without it the pair key pools by proxy IP, which stays safe.
const LOGIN_WINDOW_MS = parseInt(process.env.LOGIN_WINDOW_MS || '', 10) || 10 * 60 * 1000; // env override is for tests
const LOGIN_MAX_FAILS = 10;
const LOGIN_IP_MAX_FAILS = 50;
const loginFails = new Map(); // "ip|username" -> failure timestamps (ms)
const loginIpFails = new Map(); // ip -> failure timestamps (ms)
const loginInflight = new Map(); // "ip|username" -> attempts being verified upstream
const loginIpInflight = new Map(); // ip -> attempts being verified upstream
function trimLoginWindow(map, key, now) {
  const rec = (map.get(key) || []).filter((t) => now - t < LOGIN_WINDOW_MS);
  if (rec.length) map.set(key, rec); else map.delete(key);
  return rec;
}
function loginBlockedSeconds(ip, pair) {
  const now = Date.now();
  const pairRec = trimLoginWindow(loginFails, pair, now);
  const ipRec = trimLoginWindow(loginIpFails, ip, now);
  // In-flight attempts count against both budgets so a concurrent burst
  // cannot all pass the check before the first failure is recorded (check
  // and reservation happen in one synchronous block).
  const pairUsed = pairRec.length + (loginInflight.get(pair) || 0);
  const ipUsed = ipRec.length + (loginIpInflight.get(ip) || 0);
  if (pairUsed < LOGIN_MAX_FAILS && ipUsed < LOGIN_IP_MAX_FAILS) return 0;
  const oldest = (pairUsed >= LOGIN_MAX_FAILS ? pairRec[0] : 0) || ipRec[0] || now;
  return Math.max(1, Math.ceil((oldest + LOGIN_WINDOW_MS - now) / 1000));
}
function noteLoginFail(ip, pair) {
  const now = Date.now();
  const a = trimLoginWindow(loginFails, pair, now); a.push(now); loginFails.set(pair, a);
  const b = trimLoginWindow(loginIpFails, ip, now); b.push(now); loginIpFails.set(ip, b);
}

// SSRF guard for the user-supplied Jellyfin address. The product requires
// reaching self-hosted servers on private LAN and loopback addresses, so
// those stay allowed; what is blocked is link-local (169.254.0.0/16, which
// includes cloud instance metadata endpoints), the unspecified address,
// multicast, and their IPv6 forms. The hostname is resolved and every
// answer must be safe. Node's http client never follows redirects, so a
// hostile server cannot bounce the request to a second target; upstream
// responses are size-capped and time out.
function expandV6(addr) {
  let a = addr.toLowerCase();
  if (a.includes('.')) { // embedded dotted-quad tail -> two hex quads
    const v4 = a.split(':').pop().split('.').map((x) => parseInt(x, 10));
    if (v4.length !== 4 || v4.some((n) => isNaN(n) || n > 255)) return null;
    const hex = ((v4[0] << 8) | v4[1]).toString(16) + ':' + ((v4[2] << 8) | v4[3]).toString(16);
    a = a.slice(0, a.lastIndexOf(':') + 1) + hex;
  }
  const sides = a.split('::');
  if (sides.length > 2) return null;
  const head = sides[0] ? sides[0].split(':') : [];
  const tail = sides.length === 2 && sides[1] ? sides[1].split(':') : [];
  if (sides.length === 1) return head.length === 8 ? head : null;
  const missing = 8 - head.length - tail.length;
  if (missing < 1) return null;
  return [...head, ...Array(missing).fill('0'), ...tail];
}
function isBlockedV4(p) {
  return p[0] === 0 || (p[0] === 169 && p[1] === 254) || p[0] >= 224;
}
function isBlockedUpstreamAddr(addr) {
  if (!addr.includes(':')) {
    const p = addr.split('.').map((x) => parseInt(x, 10));
    if (p.length !== 4 || p.some((n) => isNaN(n))) return true;
    return isBlockedV4(p);
  }
  const q = expandV6(addr);
  if (!q) return true;
  const n = q.map((x) => parseInt(x, 16));
  if (n.some((x) => isNaN(x))) return true;
  if (n.every((x) => x === 0)) return true; // :: unspecified
  if (n.slice(0, 7).every((x) => x === 0) && n[7] === 1) return false; // ::1 loopback: allowed, same as 127.0.0.1
  if (n[0] >= 0xff00) return true; // multicast ff00::/8
  if ((n[0] & 0xffc0) === 0xfe80) return true; // link-local fe80::/10
  if (n.slice(0, 5).every((x) => x === 0) && n[5] === 0xffff) { // v4-mapped ::ffff:a.b.c.d, any notation
    return isBlockedV4([n[6] >> 8, n[6] & 255, n[7] >> 8, n[7] & 255]);
  }
  return false; // global or ULA (LAN) v6: allowed
}
// DRIFTFIN_TEST_DNS_FILE (test harness only, never set in deployment): path to a
// JSON file {hostname: [addresses]} consulted instead of live DNS, re-read on
// every lookup so tests can simulate mixed answers and rebinding.
async function lookupAllAddresses(hostname) {
  const hook = process.env.DRIFTFIN_TEST_DNS_FILE;
  if (hook) {
    try {
      const map = JSON.parse(fs.readFileSync(hook, 'utf8'));
      if (Array.isArray(map[hostname])) return map[hostname].map(String);
    } catch {}
  }
  if (net.isIP(hostname)) return [hostname];
  const answers = await dns.promises.lookup(hostname, { all: true }).catch(() => []);
  return answers.map((a) => a.address);
}

// Resolve and validate a user-supplied Jellyfin address. Returns
// { tls, hostname, port, addrs } on success, null when the target is not
// allowed. ANY unsafe answer poisons the whole target (mixed-answer DNS is a
// known rebinding shape), and the returned addrs are what every later
// connection is pinned to - DNS is never re-asked for a live session.
async function resolveJellyfinTarget(serverUrl) {
  let u;
  try { u = new URL(serverUrl); } catch { return null; }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
  const hostname = u.hostname.replace(/^\[|\]$/g, '');
  const port = parseInt(u.port || '', 10) || (u.protocol === 'https:' ? 443 : 80);
  const all = await lookupAllAddresses(hostname);
  if (!all.length) return null;
  if (all.some((a) => isBlockedUpstreamAddr(a))) return null;
  return { tls: u.protocol === 'https:', hostname, port, addrs: all };
}

// Addresses this session may actually connect to. Sessions created at login
// carry pinnedAddrs (validated then); older sessions resolve on demand and
// use only the safe answers. Either way every entry is re-checked against the
// blocklist at connection time.
async function pinnedAddrsFor(session, hostname) {
  const pinned = (session.pinnedAddrs || []).filter((a) => !isBlockedUpstreamAddr(a));
  if (pinned.length) return pinned;
  const all = await lookupAllAddresses(hostname);
  return all.filter((a) => !isBlockedUpstreamAddr(a));
}

// One upstream connection to a pinned address. TLS certificate verification
// stays anchored to the ORIGINAL hostname (servername + Host header), only
// the dial target is the validated IP.
function rawUpstreamRequest({ tls, addr, hostname, port, path: p, method, headers, body, timeoutMs, stream }) {
  return new Promise((resolve, reject) => {
    const lib = tls ? https : http;
    const defaultPort = tls ? 443 : 80;
    const hostHeader = hostname + (port && port !== defaultPort ? ':' + port : '');
    const opts = { host: addr, port: port || defaultPort, path: p, method, headers: { ...headers, Host: hostHeader }, timeout: timeoutMs };
    if (tls) opts.servername = hostname;
    const req = lib.request(opts, (resp) => {
      if (stream) return resolve({ streamResp: resp });
      const chunks = [];
      let size = 0;
      resp.on('data', (c) => { size += c.length; if (size > MAX_UPSTREAM_BYTES) { req.destroy(new Error('upstream response too large')); return; } chunks.push(c); });
      resp.on('end', () => resolve({ status: resp.statusCode, headers: resp.headers, body: Buffer.concat(chunks) }));
    });
    req.on('timeout', () => req.destroy(new Error('Jellyfin request timed out')));
    req.on('error', reject);
    if (body && body.length) req.write(body);
    req.end();
  });
}

const MAX_UPSTREAM_BYTES = 32 * 1024 * 1024; // 32 MB - API payloads are small; video streams bypass this path.
/** One upstream call to the user's Jellyfin server, pinned to the addresses
 *  validated at sign-in. Resolves to {status, headers, body}. */
async function jellyfinRequest(session, targetPath, method, body, contentType) {
  const base = session.server.replace(/\/$/, '');
  const url = new URL(base + '/' + targetPath.replace(/^\//, ''));
  const tls = url.protocol === 'https:';
  const hostname = url.hostname.replace(/^\[|\]$/g, '');
  const port = parseInt(url.port || '', 10) || (tls ? 443 : 80);
  const addrs = await pinnedAddrsFor(session, hostname);
  if (!addrs.length) throw new Error('no allowed upstream address for Jellyfin server');
  // TV Session.kt uses the standard Authorization header (Jellyfin 12 default).
  const headers = { 'Authorization': authHeader(session) };
  if (body && body.length) headers['Content-Type'] = contentType || 'application/json';
  let lastErr = null;
  for (const addr of addrs) {
    try {
      return await rawUpstreamRequest({ tls, addr, hostname, port, path: url.pathname + url.search, method, headers, body, timeoutMs: 60000 });
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}
async function jellyfinUnauthenticatedGet(target, targetPath, deviceId) {
  const headers = { 'Authorization': clientHeader(deviceId) };
  let lastErr = null;
  for (const addr of target.addrs) {
    try {
      return await rawUpstreamRequest({ tls: target.tls, addr, hostname: target.hostname, port: target.port, path: '/' + targetPath, method: 'GET', headers, timeoutMs: 30000 });
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}

// Quick Connect pending codes live only in memory, expire after 5 minutes, and
// the secret never leaves this server (the browser only sees the short code).
const quickConnectPending = new Map();
const QUICKCONNECT_TTL_MS = 5 * 60 * 1000;
const QUICKCONNECT_MAX_PER_IP = 3;
const QUICKCONNECT_START_WINDOW_MS = 10 * 60 * 1000;
const QUICKCONNECT_START_MAX = 10;
const quickConnectStarts = new Map(); // ip -> [timestamps]
function clientIpFor(req) {
  return process.env.TRUST_PROXY === '1'
    ? String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || 'unknown'
    : req.socket.remoteAddress || 'unknown';
}
function quickConnectStartAllowed(ip) {
  const now = Date.now();
  const list = (quickConnectStarts.get(ip) || []).filter((t) => now - t < QUICKCONNECT_START_WINDOW_MS);
  if (list.length >= QUICKCONNECT_START_MAX) { quickConnectStarts.set(ip, list); return false; }
  list.push(now);
  quickConnectStarts.set(ip, list);
  return true;
}
function qcBrowserKey(req) {
  const m = /(?:^|;\s*)driftfin_qc=([a-f0-9]{48})/.exec(String(req.headers.cookie || ''));
  return m ? m[1] : null;
}
function qcCookieHeader(key, maxAge) {
  const base = `driftfin_qc=${key}; Path=/api/quickconnect; HttpOnly; SameSite=Strict; Max-Age=${maxAge == null ? 600 : maxAge}`;
  return COOKIE_SECURE ? base + '; Secure' : base;
}
function pruneQuickConnect() {
  const now = Date.now();
  for (const [id, p] of quickConnectPending) { if (p.expiresAt <= now) quickConnectPending.delete(id); }
}

async function jellyfinUnauthenticated(target, targetPath, body, deviceId) {
  const payload = Buffer.from(JSON.stringify(body), 'utf8');
  const headers = { 'Authorization': clientHeader(deviceId), 'Content-Type': 'application/json', 'Content-Length': payload.length };
  let lastErr = null;
  for (const addr of target.addrs) {
    try {
      return await rawUpstreamRequest({ tls: target.tls, addr, hostname: target.hostname, port: target.port, path: '/' + targetPath, method: 'POST', headers, body: payload, timeoutMs: 30000 });
    } catch (e) { lastErr = e; }
  }
  throw lastErr;
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.json': 'application/json',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
  '.webmanifest': 'application/manifest+json',
};
// ---- TVMaze verified season cast (TV TvmazeCast.kt parity) ----
// Some seasons carry no Actor People on the Jellyfin server because the provider splits
// the season into its own entry (Jury Duty season 2 = TVMaze's separate
// "Jury Duty Presents: Company Retreat"). This resolves THAT season on TVMaze - never by
// provider id and never by name alone: a candidate show's season must match every Jellyfin
// episode on number + title + air date. Only then are that season's episode guest credits
// served. No verified match, no enrichment. TVMaze's show-level regulars are never fetched:
// they are not the season's episode credits.
//
// TVMaze is a free public API: no account, no key (https://www.tvmaze.com/api; data
// CC BY-SA 4.0, credit + link shown in Settings). Rate limit is at least 20 calls per
// 10 seconds per IP; a season costs 1 search + up to 5 episode lists + one guest-cast
// call per verified episode, all cached for hours.
const TVMAZE_BASE = (process.env.TVMAZE_BASE_URL || 'https://api.tvmaze.com').replace(/\/+$/, '');
const TVMAZE_UA = 'Driftfin (github.com/dhrandy/Driftfin)';
const TVMAZE_CACHE_TTL_MS = 6 * 60 * 60 * 1000;
const TVMAZE_MAX_EPISODES = 50; // a sane bound on guest-cast calls per verified season
const TVMAZE_RESOLUTION_DEADLINE_MS = 60000; // whole-resolution cap: 1 search + <=5 lists + <=50 guest calls
// Authenticated-abuse bounds: a season lookup costs real outbound calls, so resolutions
// are capped globally and per session, and the cache is size-bounded (oldest evicted).
const TVMAZE_CACHE_MAX = 500;
const TVMAZE_MAX_CONCURRENT = 3;
const TVMAZE_SESSION_WINDOW_MS = 60000;
const TVMAZE_SESSION_MAX_RESOLUTIONS = 12;
const tvmazeSeasonCache = new Map();    // key -> { ts, people } (insertion order = eviction order)
const tvmazeSeasonInflight = new Map(); // key -> Promise (one resolution per season at a time)
const tvmazeMatchCache = new Map();     // seasonKey -> { ts, match } verified TVMaze episode list
const tvmazeEpisodeCache = new Map();   // episodeKey -> { ts, people } per-episode guest credits
const tvmazeSessionLog = new Map();     // sessionKey -> number[] recent resolution start times

// ---------- update check helpers ----------
// The identity of "the browser app" is the content of every file the runtime needs:
// client bundle, markup, styles, vendored player lib, this server, and the package
// manifest (dependency set). A CSS-only or server-only release must not read as current.
const GITHUB_REPO = 'dhrandy/Driftfin';
const GITHUB_API_BASE = (process.env.GITHUB_API_BASE || 'https://api.github.com').replace(/\/+$/, '');
const UPDATE_CHECK_TTL_MS = 10 * 60 * 1000;
// The full tracked runtime tree under browser/: client code, markup, styles, icons,
// the vendored player lib, every profile avatar asset, this server, the manifests the
// container installs from, and the compose/docs files that ship in the same directory.
// A change to ANY of them must read as an update; anything unreadable reads unknown.
const UPDATE_CHECK_FILES = [
  'public/app.js',
  'public/index.html',
  'public/styles.css',
  'public/favicon.png',
  'public/manifest.webmanifest',
  'public/sw.js',
  'public/icons/icon-192.png',
  'public/icons/icon-512.png',
  'public/icons/icon-maskable-512.png',
  'public/icons/apple-touch-icon.png',
  'public/vendor/hls.min.js',
  ...Array.from({ length: 20 }, (_, i) => `public/avatars/avatar_${String(i + 1).padStart(2, '0')}.png`),
  'src/server.js',
  'package.json',
  'package-lock.json',
  'docker-compose.yml',
  'README.md',
  'THIRD_PARTY_NOTICES.md',
];
const updateCheckCache = { at: 0, result: null };
function gitBlobSha(buf) {
  return crypto.createHash('sha1').update(`blob ${buf.length}\0`).update(buf).digest('hex');
}
const UPDATE_CHECK_SET = new Set(UPDATE_CHECK_FILES);
// Fixed bounded dirs that make up the runtime tree. Subdirs of public/ are served
// and subdirs of src/ are server code, so an unknown one there means unknown coverage;
// anything else at the repo root (e.g. the untracked runtime data/ dir) is ignored.
const UPDATE_CHECK_DIRS = ['', 'public', 'public/vendor', 'public/avatars', 'public/icons', 'src'];
const UPDATE_CHECK_SUBDIRS = new Set(['public', 'public/vendor', 'public/avatars', 'public/icons', 'src']);
function updateCheckLocalFingerprints() {
  const hashes = {};
  let uncovered = false;
  for (const dir of UPDATE_CHECK_DIRS) {
    let entries;
    try { entries = fs.readdirSync(path.join(__dirname, '..', dir), { withFileTypes: true }); }
    catch { return null; }
    for (const e of entries) {
      const rel = dir ? dir + '/' + e.name : e.name;
      if (e.isDirectory()) {
        if (!UPDATE_CHECK_SUBDIRS.has(rel) && (dir === 'public' || dir === 'src')) uncovered = true;
        continue;
      }
      if (!e.isFile()) continue;
      if (!UPDATE_CHECK_SET.has(rel)) {
        // Only the served (public/) and server (src/) trees prove uncovered runtime code.
        // The app root also carries the container's own install artifacts (the
        // .driftfin-installed marker, node_modules/) which are not runtime code.
        if (dir !== '') uncovered = true;
        continue;
      }
      try { hashes[rel] = gitBlobSha(fs.readFileSync(path.join(__dirname, '..', rel))); }
      catch { return null; }
    }
  }
  return { hashes, uncovered };
}
async function githubApiFetch(pathname) {
  const res = await fetch(GITHUB_API_BASE + pathname, {
    headers: { 'User-Agent': 'Driftfin-Update-Check', Accept: 'application/vnd.github+json' },
    signal: AbortSignal.timeout(10000),
  }).catch(() => null);
  if (!res || !res.ok) return null;
  return res.json().catch(() => null);
}

function tvmazeSessionAllowed(sessionKey) {
  const now = Date.now();
  const log = (tvmazeSessionLog.get(sessionKey) || []).filter((t) => now - t < TVMAZE_SESSION_WINDOW_MS);
  if (log.length >= TVMAZE_SESSION_MAX_RESOLUTIONS) { tvmazeSessionLog.set(sessionKey, log); return false; }
  log.push(now); tvmazeSessionLog.set(sessionKey, log);
  if (tvmazeSessionLog.size > 1000) tvmazeSessionLog.delete(tvmazeSessionLog.keys().next().value);
  return true;
}
function tvmazeCacheSet(key, people) {
  if (tvmazeSeasonCache.size >= TVMAZE_CACHE_MAX) tvmazeSeasonCache.delete(tvmazeSeasonCache.keys().next().value);
  tvmazeSeasonCache.set(key, { ts: Date.now(), people });
}
function tvmazeBoundedSet(map, key, value) {
  if (map.size >= TVMAZE_CACHE_MAX) map.delete(map.keys().next().value);
  map.set(key, { ts: Date.now(), ...value });
}
function tvmazeMapPeople(credits) {
  const people = []; const seenPeople = new Set();
  for (const c of credits) {
    const person = c && c.person;
    if (!person || !(person.id > 0) || !String(person.name || '').trim() || seenPeople.has(person.id)) continue;
    seenPeople.add(person.id);
    people.push({
      Name: person.name,
      Role: (c.character && c.character.name) || '',
      RemoteImageUrl: (person.image && person.image.medium) || '',
      ProviderIds: { Tvmaze: String(person.id) },
    });
  }
  return people;
}

async function tvmazeFetch(pathname) {
  const opts = { headers: { 'User-Agent': TVMAZE_UA, Accept: 'application/json' }, signal: AbortSignal.timeout(15000) };
  let res = await fetch(TVMAZE_BASE + pathname, opts).catch(() => null);
  if (res && res.status === 429) { // one bounded retry on rate-limit; everything else fails closed
    await new Promise((r) => setTimeout(r, 1500));
    res = await fetch(TVMAZE_BASE + pathname, opts).catch(() => null);
  }
  if (!res || !res.ok) return null;
  return res.json().catch(() => null);
}

function tvmazeNormalizeTitle(str) {
  // "&" and "and" are the same word in episode titles ("Mergers & Acquisitions" ==
  // "Mergers and Acquisitions"): normalize before stripping punctuation.
  return String(str || '').toLowerCase().replace(/&/g, 'and').replace(/[^\p{L}\p{N}]/gu, '');
}
function tvmazeSameAirDate(jellyfinPremiereDate, tvmazeAirDate) {
  const j = String(jellyfinPremiereDate || '').slice(0, 10);
  const t = String(tvmazeAirDate || '').slice(0, 10);
  return j !== '' && t !== '' && j === t;
}
// Strict, extras-aware fingerprint: EVERY TVMaze episode must map onto EXACTLY ONE server
// episode with the same episode number, the same normalized title, and the same air date
// (non-blank on both sides). Server items beyond that - extras like behind-the-scenes
// rows - are tolerated: they match nothing. Any TVMaze episode left unmatched (a middle
// disagreement, a missing date, a duplicate server row) fails the whole season. A
// title-only coincidence can never pass.
function tvmazeFingerprintMatches(jellyfinEpisodes, tvmazeEpisodes) {
  if (!jellyfinEpisodes.length || !tvmazeEpisodes.length) return false;
  if (tvmazeEpisodes.length > jellyfinEpisodes.length) return false;
  for (const t of tvmazeEpisodes) {
    const matches = jellyfinEpisodes.filter((j) =>
      j.IndexNumber != null && j.IndexNumber === t.number &&
      tvmazeNormalizeTitle(j.Name) === tvmazeNormalizeTitle(t.name) &&
      tvmazeSameAirDate(j.PremiereDate, t.airdate));
    if (matches.length !== 1) return false;
  }
  return true;
}

// Verified-season resolution, cached so the season row and the pause-time single-episode
// lookup share one upstream pass. Returns { items, match } - the server's own season
// episodes and the one verified TVMaze season's episode list - or null when nothing
// verifies (unknown, ambiguous, over budget: all fail closed the same way).
async function tvmazeResolveSeasonMatch(session, seriesId, seasonId) {
  const matchKey = `${session.server}#${session.userId}#${seriesId}|${seasonId}`;
  const cached = tvmazeMatchCache.get(matchKey);
  if (cached && Date.now() - cached.ts < TVMAZE_CACHE_TTL_MS) return cached.result;
  const result = await (async () => {
    const deadline = Date.now() + TVMAZE_RESOLUTION_DEADLINE_MS;
    const expired = () => Date.now() > deadline;
    const epsRes = await jellyfinRequest(session, `Shows/${seriesId}/Episodes?userId=${encodeURIComponent(session.userId)}&seasonId=${encodeURIComponent(seasonId)}&fields=PremiereDate&limit=2000`, 'GET');
    if (epsRes.status !== 200) return null;
    let items = [];
    try { items = JSON.parse(epsRes.body.toString('utf8')).Items || []; } catch { return null; }
    if (!items.length) return null;
    const seriesName = items[0] && items[0].SeriesName;
    if (!seriesName) return null;
    const hits = await tvmazeFetch('/search/shows?q=' + encodeURIComponent(seriesName));
    if (!Array.isArray(hits)) return null;
    const candidates = []; const seenShows = new Set();
    for (const h of hits) {
      const show = h && h.show;
      if (show && show.id > 0 && !seenShows.has(show.id)) { seenShows.add(show.id); candidates.push(show); }
      if (candidates.length >= 5) break;
    }
    let match = null; // the one verified candidate season's episodes
    for (const candidate of candidates) {
      if (expired()) return null;
      const all = await tvmazeFetch(`/shows/${candidate.id}/episodes`);
      if (!Array.isArray(all)) continue;
      const bySeason = new Map();
      for (const e of all) {
        if (!e || typeof e.number !== 'number' || e.number < 1) continue;
        if (!bySeason.has(e.season)) bySeason.set(e.season, []);
        bySeason.get(e.season).push(e);
      }
      for (const seasonEpisodes of bySeason.values()) {
        // Server may carry MORE items than the real season (extras); never fewer.
        if (!seasonEpisodes.length || seasonEpisodes.length > items.length) continue;
        if (!tvmazeFingerprintMatches(items, seasonEpisodes)) continue;
        if (match) return null; // ambiguous: two candidate seasons both match - stay hidden
        match = seasonEpisodes;
      }
    }
    return match ? { items, match } : null;
  })();
  tvmazeBoundedSet(tvmazeMatchCache, matchKey, { result });
  return result;
}

// The verified season's episode guest credits, deduped by TVMaze person id, in episode
// order - or [] when no season can be verified. A credit with a missing or non-positive
// person id is rejected: it must never collapse two id-less credits into one.
async function tvmazeSeasonCast(session, seriesId, seasonId) {
  const deadline = Date.now() + TVMAZE_RESOLUTION_DEADLINE_MS;
  const resolved = await tvmazeResolveSeasonMatch(session, seriesId, seasonId);
  if (!resolved) return [];
  const people = []; const seen = new Set();
  for (const ep of resolved.match.slice(0, TVMAZE_MAX_EPISODES)) {
    if (Date.now() > deadline) return [];
    const credits = await tvmazeFetch(`/episodes/${ep.id}/guestcast`);
    if (!Array.isArray(credits)) continue;
    for (const p of tvmazeMapPeople(credits)) {
      if (seen.has(p.ProviderIds.Tvmaze)) continue;
      seen.add(p.ProviderIds.Tvmaze);
      people.push(p);
    }
  }
  return people;
}

// TV PauseCast parity: the guest credits of ONE verified episode (pause-time cast strip).
// The episode must match the verified season's list exactly once by number + normalized
// title + air date - the same fingerprint fields as the season verification - or it
// stays hidden. Never a wrong-episode or show-level list.
async function tvmazeEpisodeGuestCast(session, episodeId) {
  const epRes = await jellyfinRequest(session, `Items/${episodeId}?userId=${encodeURIComponent(session.userId)}`, 'GET');
  if (epRes.status !== 200) return [];
  let ep = null;
  try { ep = JSON.parse(epRes.body.toString('utf8')); } catch { return []; }
  if (!ep || ep.Type !== 'Episode' || !ep.SeriesId || !ep.SeasonId) return [];
  const resolved = await tvmazeResolveSeasonMatch(session, ep.SeriesId, ep.SeasonId);
  if (!resolved) return [];
  const matches = resolved.match.filter((t) =>
    ep.IndexNumber != null && ep.IndexNumber === t.number &&
    tvmazeNormalizeTitle(ep.Name) === tvmazeNormalizeTitle(t.name) &&
    tvmazeSameAirDate(ep.PremiereDate, t.airdate));
  if (matches.length !== 1) return [];
  const credits = await tvmazeFetch(`/episodes/${matches[0].id}/guestcast`);
  if (!Array.isArray(credits)) return [];
  return tvmazeMapPeople(credits);
}

function serveStatic(req, res, pathname) {
  let filePath = pathname === '/' ? path.join(PUBLIC_DIR, 'index.html') : path.join(PUBLIC_DIR, pathname);
  const root = path.resolve(PUBLIC_DIR);
  const resolved = path.resolve(filePath);
  if (resolved !== root && !resolved.startsWith(root + path.sep)) { res.writeHead(403); return res.end('forbidden'); }
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('not found'); }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Content-Length': data.length, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    // Protocol-relative or malformed request paths (e.g. "//") must not 500.
    const url = new URL(req.url.startsWith('/') && !req.url.startsWith('//') ? req.url : '/', 'http://localhost');
    const pathname = url.pathname;

    if (pathname === '/api/health' || pathname === '/health') {
      return sendJson(res, 200, { ok: true, app: 'driftfin-browser', version: VERSION });
    }
    if (pathname === '/api/config') {
      return sendJson(res, 200, {
        jellyfinUrl: process.env.JELLYFIN_URL || '',
        serverLocked: process.env.JELLYFIN_LOCKED === '1',
        cookieSecure: COOKIE_SECURE,
        version: VERSION,
      });
    }
    // CSRF boundary: every mutating API call with an Origin header must be
    // same-origin. The session cookie is SameSite=Lax (no cross-site POST
    // carries it) and API clients that send no Origin are unaffected.
    if (req.method !== 'GET' && req.method !== 'HEAD' && pathname.startsWith('/api/') && req.headers.origin) {
      let originHost = null;
      try { originHost = new URL(String(req.headers.origin)).host; } catch {}
      if (originHost !== req.headers.host) return sendJson(res, 403, { error: 'origin_not_allowed' });
    }
    if (pathname === '/api/auth/login' && req.method === 'POST') {
      // Client key for the rate limit: the socket peer by default. X-Forwarded-For
      // is honored only when the operator sets TRUST_PROXY=1, i.e. the app sits
      // behind a reverse proxy that strips client-supplied forwarding headers.
      // Otherwise every user would share the proxy's IP (one block bans all) and
      // a spoofed header could reset or target counters.
      const clientIp = process.env.TRUST_PROXY === '1'
        ? String(req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || 'unknown'
        : req.socket.remoteAddress || 'unknown';
      const payload = await readJsonBody(req);
      const serverUrl = String(payload.server || '').trim().replace(/\/$/, '');
      const username = String(payload.username || '').trim();
      const password = String(payload.password || '');
      if (!serverUrl || !/^https?:\/\//.test(serverUrl) || !username) {
        return sendJson(res, 400, { error: 'server must start with http:// or https://, and username is required' });
      }
      // JELLYFIN_LOCKED=1 (opt-in): the operator fixes the Jellyfin target via
      // JELLYFIN_URL and the sign-in form's server field is ignored, so
      // sign-in can never be aimed at another host.
      const effectiveServer = process.env.JELLYFIN_LOCKED === '1' ? String(process.env.JELLYFIN_URL || '').trim() : serverUrl;
      if (!effectiveServer) return sendJson(res, 500, { error: 'JELLYFIN_LOCKED is set but JELLYFIN_URL is empty' });
      const target = await resolveJellyfinTarget(effectiveServer);
      if (!target) {
        return sendJson(res, 400, { error: 'server address is not allowed (link-local, metadata, multicast, or unresolvable addresses are rejected)' });
      }
      const loginPair = clientIp + '|' + username.toLowerCase();
      const blockedFor = loginBlockedSeconds(clientIp, loginPair);
      if (blockedFor) return sendJson(res, 429, { error: 'too_many_attempts', retryAfterSeconds: blockedFor }, { 'Retry-After': String(blockedFor) });
      loginInflight.set(loginPair, (loginInflight.get(loginPair) || 0) + 1);
      loginIpInflight.set(clientIp, (loginIpInflight.get(clientIp) || 0) + 1);
      try {
      let result;
      const sessionDeviceId = browserDeviceId(req) || newDeviceId();
      try { result = await jellyfinUnauthenticated(target, 'Users/AuthenticateByName', { Username: username, Pw: password }, sessionDeviceId); }
      catch (e) { console.error('Driftfin login unreachable', effectiveServer, String(e.message || e)); noteLoginFail(clientIp, loginPair); return sendJson(res, 502, { error: 'jellyfin_unreachable', detail: String(e.message || e).slice(0, 500) }); }
      let parsed = {};
      try { parsed = JSON.parse(result.body.toString('utf8')); } catch {}
      if (result.status >= 400 || !parsed.AccessToken || !parsed.User || !parsed.User.Id) {
        console.error('Driftfin login rejected', effectiveServer, 'status', result.status, result.body.toString('utf8').slice(0, 300));
        noteLoginFail(clientIp, loginPair);
        return sendJson(res, result.status >= 400 ? result.status : 502, { error: 'jellyfin_rejected_sign_in', jellyfinStatus: result.status });
      }
      loginFails.delete(loginPair);
      const session = {
        id: crypto.randomBytes(32).toString('hex'),
        server: effectiveServer, token: parsed.AccessToken, pinnedAddrs: target.addrs,
        deviceId: sessionDeviceId,
        userId: parsed.User.Id, username: parsed.User.Name || username,
        createdAt: new Date().toISOString(),
      };
      saveSession(session);
      return sendJson(res, 200, { ok: true, username: session.username, userId: session.userId },
        { 'Set-Cookie': [cookieHeader(session.id, SESSION_COOKIE_MAX_AGE_SECONDS), deviceCookieHeader(sessionDeviceId, DEVICE_COOKIE_MAX_AGE_SECONDS)] });
      } finally {
        loginInflight.set(loginPair, Math.max(0, (loginInflight.get(loginPair) || 1) - 1));
        loginIpInflight.set(clientIp, Math.max(0, (loginIpInflight.get(clientIp) || 1) - 1));
      }
    }
    if (pathname === '/api/quickconnect/start' && req.method === 'POST') {
      pruneQuickConnect();
      const qcIp = clientIpFor(req);
      if (quickConnectPending.size >= 100 || !quickConnectStartAllowed(qcIp)
        || [...quickConnectPending.values()].filter((x) => x.clientIp === qcIp).length >= QUICKCONNECT_MAX_PER_IP) {
        return sendJson(res, 429, { error: 'too_many_pending' });
      }
      const payload = await readJsonBody(req);
      const serverUrl = String(payload.server || '').trim().replace(/\/$/, '');
      const effectiveServer = process.env.JELLYFIN_LOCKED === '1' ? String(process.env.JELLYFIN_URL || '').trim() : serverUrl;
      if (!effectiveServer) return sendJson(res, 400, { error: process.env.JELLYFIN_LOCKED === '1' ? 'locked_target_missing' : 'server_required' });
      if (!/^https?:\/\//.test(effectiveServer)) return sendJson(res, 400, { error: 'server must start with http:// or https://' });
      const target = await resolveJellyfinTarget(effectiveServer);
      if (!target) return sendJson(res, 400, { error: 'server address is not allowed (link-local, metadata, multicast, or unresolvable addresses are rejected)' });
      let result;
      try { result = await jellyfinUnauthenticated(target, 'QuickConnect/Initiate', {}); }
      catch (e) { return sendJson(res, 502, { error: 'jellyfin_unreachable', detail: String(e.message || e).slice(0, 500) }); }
      let parsed = {};
      try { parsed = JSON.parse(result.body.toString('utf8')); } catch {}
      if (result.status >= 400 || !parsed.Secret || !parsed.Code) {
        return sendJson(res, 400, { error: 'quickconnect_unavailable' });
      }
      const id = crypto.randomBytes(16).toString('hex');
      const browserKey = crypto.randomBytes(24).toString('hex');
      quickConnectPending.set(id, { secret: parsed.Secret, server: effectiveServer, target, clientIp: qcIp, browserKey, deviceId: browserDeviceId(req) || newDeviceId(), expiresAt: Date.now() + QUICKCONNECT_TTL_MS });
      return sendJson(res, 200, { id, code: parsed.Code, expiresInSeconds: QUICKCONNECT_TTL_MS / 1000 }, { 'Set-Cookie': qcCookieHeader(browserKey) });
    }
    if (pathname === '/api/quickconnect/status' && req.method === 'GET') {
      pruneQuickConnect();
      const pending = quickConnectPending.get(url.searchParams.get('id') || '');
      // A shared reverse proxy makes IPs non-unique: the initiating browser proves
      // itself with the unguessable cookie issued at /start, not just its address.
      if (!pending || pending.clientIp !== clientIpFor(req) || pending.browserKey !== qcBrowserKey(req)) {
        return sendJson(res, 200, { status: 'expired' });
      }
      let chk;
      try { chk = await jellyfinUnauthenticatedGet(pending.target, 'QuickConnect/Connect?secret=' + encodeURIComponent(pending.secret), pending.deviceId); }
      catch { return sendJson(res, 200, { status: 'pending' }); }
      let state = {};
      try { state = JSON.parse(chk.body.toString('utf8')); } catch {}
      if (!state.Authenticated) return sendJson(res, 200, { status: 'pending' });
      let auth;
      try { auth = await jellyfinUnauthenticated(pending.target, 'Users/AuthenticateWithQuickConnect', { Secret: pending.secret }, pending.deviceId); }
      catch (e) { return sendJson(res, 502, { error: 'jellyfin_unreachable', detail: String(e.message || e).slice(0, 500) }); }
      let body = {};
      try { body = JSON.parse(auth.body.toString('utf8')); } catch {}
      quickConnectPending.delete(url.searchParams.get('id') || '');
      if (auth.status >= 400 || !body.AccessToken || !body.User || !body.User.Id) {
        return sendJson(res, 200, { status: 'failed' });
      }
      const session = {
        id: crypto.randomBytes(32).toString('hex'),
        server: pending.server, token: body.AccessToken, pinnedAddrs: pending.target.addrs,
        deviceId: pending.deviceId,
        userId: body.User.Id, username: body.User.Name || 'Jellyfin user',
        createdAt: new Date().toISOString(),
      };
      saveSession(session);
      return sendJson(res, 200, { status: 'authorized', username: session.username, userId: session.userId },
        { 'Set-Cookie': [cookieHeader(session.id, SESSION_COOKIE_MAX_AGE_SECONDS), qcCookieHeader('', 0), deviceCookieHeader(session.deviceId, DEVICE_COOKIE_MAX_AGE_SECONDS)] });
    }
    if (pathname === '/api/quickconnect/cancel' && req.method === 'POST') {
      const payload = await readJsonBody(req);
      const pending = quickConnectPending.get(String(payload.id || ''));
      if (pending && pending.clientIp === clientIpFor(req) && pending.browserKey === qcBrowserKey(req)) {
        quickConnectPending.delete(String(payload.id || ''));
      }
      return sendJson(res, 200, { ok: true });
    }
    if (pathname === '/api/auth/logout') {
      dropSession(req);
      return sendJson(res, 200, { ok: true }, { 'Set-Cookie': cookieHeader('', 0).replace(/driftfin_session=\./, 'driftfin_session=') });
    }

    const session = getSession(req);
    if (pathname === '/api/auth/me') {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      // TV SessionManager.validateToken uses Users/Me, not Users/{id}.
      const r = await jellyfinRequest(session, 'Users/Me', 'GET');
      res.writeHead(r.status, { 'Content-Type': r.headers['content-type'] || 'application/json', 'Content-Length': r.body.length, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS });
      return res.end(r.body);
    }
    if (pathname === '/api/settings') {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const file = path.join(DATA_DIR, 'settings.json');
      if (req.method === 'GET') {
        const all = readJsonFile(file, {});
        return sendJson(res, 200, { ...DEFAULT_SETTINGS, ...(all[session.userId] || {}) });
      }
      if (req.method === 'POST') {
        const payload = await readJsonBody(req);
        const all = readJsonFile(file, {});
        const merged = { ...DEFAULT_SETTINGS, ...(all[session.userId] || {}) };
        for (const key of Object.keys(DEFAULT_SETTINGS)) {
          if (key in payload) {
            const def = DEFAULT_SETTINGS[key];
            if (typeof def === 'boolean') merged[key] = Boolean(payload[key]);
            else if (def === null) merged[key] = payload[key] == null ? null : String(payload[key]);
            else merged[key] = String(payload[key]);
          }
        }
        // Drop any setting key from an older build that is no longer in v1.
        for (const key of Object.keys(merged)) {
          if (!(key in DEFAULT_SETTINGS)) delete merged[key];
        }
        all[session.userId] = merged;
        writeJsonFile(file, all);
        return sendJson(res, 200, merged);
      }
      return sendJson(res, 405, { error: 'method_not_allowed' });
    }
    // Driftfin's own Watchlist (TV Watchlist.kt parity): starred movie/series ids,
    // newest first, 500-title cap, keyed by server+user so profiles and servers
    // never see each other's list. Separate from Jellyfin favorites (My List).
    const WATCHLIST_MAX = 500;
    const watchlistFile = () => path.join(DATA_DIR, 'watchlist.json');
    const watchlistKey = (sess) => `${sess.server}#${sess.userId}`;
    if (pathname === '/api/watchlist' || pathname.startsWith('/api/watchlist/')) {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const all = readJsonFile(watchlistFile(), {});
      const key = watchlistKey(session);
      const ids = Array.isArray(all[key]) ? all[key] : [];
      if (pathname === '/api/watchlist' && req.method === 'GET') return sendJson(res, 200, { ids });
      if (pathname === '/api/watchlist' && req.method === 'POST') {
        const payload = await readJsonBody(req);
        const id = String(payload.id || '').trim();
        if (!id) return sendJson(res, 400, { error: 'id_required' });
        let next, starred;
        if (ids.includes(id)) { next = ids.filter((x) => x !== id); starred = false; }
        else {
          if (ids.length >= WATCHLIST_MAX) return sendJson(res, 200, { starred: false, capped: true });
          next = [id, ...ids]; starred = true;
        }
        all[key] = next;
        writeJsonFile(watchlistFile(), all, 0o600);
        return sendJson(res, 200, { starred });
      }
      const delMatch = pathname.match(/^\/api\/watchlist\/([^/]+)$/);
      if (delMatch && req.method === 'DELETE') {
        all[key] = ids.filter((x) => x !== delMatch[1]);
        writeJsonFile(watchlistFile(), all, 0o600);
        return sendJson(res, 200, { ok: true });
      }
      return sendJson(res, 405, { error: 'method_not_allowed' });
    }
    // TVMaze verified season guest credits (TV TvmazeCast.kt parity), display-only
    // enrichment for the season Cast row. Never a substitute for server credits, never
    // another season's cast. Fails closed to an empty list on any error.
    if (pathname === '/api/tvmaze/season-cast' && req.method === 'GET') {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const seriesId = String(url.searchParams.get('seriesId') || '');
      const seasonId = String(url.searchParams.get('seasonId') || '');
      // Jellyfin ids are 32-hex; the charset check (not the exact length) is what keeps
      // these out of the upstream path.
      if (!/^[A-Za-z0-9-]{1,64}$/.test(seriesId) || !/^[A-Za-z0-9-]{1,64}$/.test(seasonId)) return sendJson(res, 400, { error: 'bad_id' });
      const allSettings = readJsonFile(path.join(DATA_DIR, 'settings.json'), {});
      if ({ ...DEFAULT_SETTINGS, ...(allSettings[session.userId] || {}) }.tvmazeEpisodeCast === false) return sendJson(res, 200, { people: [] });
      const cacheKey = `${session.server}#${session.userId}#${seriesId}|${seasonId}`;
      const hit = tvmazeSeasonCache.get(cacheKey);
      if (hit && Date.now() - hit.ts < TVMAZE_CACHE_TTL_MS) return sendJson(res, 200, { people: hit.people });
      if (!tvmazeSeasonInflight.has(cacheKey)) {
        // Over the caps: fail closed WITHOUT caching, so a later calm request can still resolve.
        if (tvmazeSeasonInflight.size >= TVMAZE_MAX_CONCURRENT || !tvmazeSessionAllowed(`${session.server}#${session.userId}`)) {
          return sendJson(res, 200, { people: [] });
        }
        tvmazeSeasonInflight.set(cacheKey, tvmazeSeasonCast(session, seriesId, seasonId).catch(() => []));
      }
      const people = await tvmazeSeasonInflight.get(cacheKey);
      tvmazeSeasonInflight.delete(cacheKey);
      tvmazeCacheSet(cacheKey, people);
      return sendJson(res, 200, { people });
    }
    // One episode's verified guest credits (TV PauseCast parity) for the pause-time cast
    // strip. Same verification, same fail-closed behavior, same bounds as the season route.
    if (pathname === '/api/tvmaze/episode-cast' && req.method === 'GET') {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const episodeId = String(url.searchParams.get('episodeId') || '');
      if (!/^[A-Za-z0-9-]{1,64}$/.test(episodeId)) return sendJson(res, 400, { error: 'bad_id' });
      const allSettings = readJsonFile(path.join(DATA_DIR, 'settings.json'), {});
      if ({ ...DEFAULT_SETTINGS, ...(allSettings[session.userId] || {}) }.tvmazeEpisodeCast === false) return sendJson(res, 200, { people: [] });
      const cacheKey = `${session.server}#${session.userId}#ep:${episodeId}`;
      const hit = tvmazeEpisodeCache.get(cacheKey);
      if (hit && Date.now() - hit.ts < TVMAZE_CACHE_TTL_MS) return sendJson(res, 200, { people: hit.people });
      if (!tvmazeSeasonInflight.has(cacheKey)) {
        if (tvmazeSeasonInflight.size >= TVMAZE_MAX_CONCURRENT || !tvmazeSessionAllowed(`${session.server}#${session.userId}`)) {
          return sendJson(res, 200, { people: [] });
        }
        tvmazeSeasonInflight.set(cacheKey, tvmazeEpisodeGuestCast(session, episodeId).catch(() => []));
      }
      const people = await tvmazeSeasonInflight.get(cacheKey);
      tvmazeSeasonInflight.delete(cacheKey);
      tvmazeBoundedSet(tvmazeEpisodeCache, cacheKey, { people });
      return sendJson(res, 200, { people });
    }
    // Read-only update check. Compares git-blob SHAs of every file that makes up the
    // running browser app (client JS, markup, CSS, server, vendored player lib,
    // package manifest) against the same files at the latest browser-touching commit
    // on GitHub. Anything we cannot verify reads as "unknown", never "up to date".
    if (pathname === '/api/update-check' && req.method === 'GET') {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const now = Date.now();
      if (updateCheckCache.result && now - updateCheckCache.at < UPDATE_CHECK_TTL_MS) {
        return sendJson(res, 200, updateCheckCache.result);
      }
      let result = { status: 'unknown' };
      const local = updateCheckLocalFingerprints();
      if (local) {
        const commits = await githubApiFetch('/repos/' + GITHUB_REPO + '/commits?path=browser&per_page=1');
        const commit = Array.isArray(commits) && commits[0];
        if (commit && commit.sha && commit.html_url) {
          const dirs = await Promise.all(['', 'public', 'public/vendor', 'public/avatars', 'public/icons', 'src'].map((dir) =>
            githubApiFetch(`/repos/${GITHUB_REPO}/contents/browser${dir ? '/' + dir : ''}?ref=${commit.sha}`)));
          if (dirs.every(Array.isArray)) {
            const remote = { files: {}, uncovered: false };
            for (const list of dirs) {
              for (const item of list) {
                if (!item || !item.path) continue;
                const rel = item.path.replace(/^browser\//, '');
                if (item.type === 'file') {
                  if (UPDATE_CHECK_SET.has(rel)) remote.files[rel] = item.sha;
                  else remote.uncovered = true; // upstream file this build knows nothing about
                } else if (item.type === 'dir' && !UPDATE_CHECK_SUBDIRS.has(rel)) {
                  const parent = rel.includes('/') ? rel.slice(0, rel.lastIndexOf('/')) : '';
                  if (parent === 'public' || parent === 'src') remote.uncovered = true; // new served/server subdir
                }
              }
            }
            const names = Object.keys(local.hashes);
            if (!local.uncovered && !remote.uncovered && names.every((rel) => remote.files[rel])) {
              result = names.some((rel) => remote.files[rel] !== local.hashes[rel])
                ? { status: 'update-available', url: commit.html_url }
                : { status: 'current' };
            }
          }
        }
      }
      updateCheckCache.at = now;
      updateCheckCache.result = result;
      return sendJson(res, 200, result);
    }
    if (pathname.startsWith('/api/jellyfin/')) {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const sub = pathname.slice('/api/jellyfin/'.length);
      const target = sub + (url.search || '');
      const body = (req.method === 'POST' || req.method === 'DELETE') ? await readBody(req) : null;
      const r = await jellyfinRequest(session, target, req.method, body && body.length ? body : null, req.headers['content-type']);
      res.writeHead(r.status, { 'Content-Type': r.headers['content-type'] || 'application/json', 'Content-Length': r.body.length, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS });
      return res.end(r.body);
    }
    if (pathname.startsWith('/api/direct/')) {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const itemId = pathname.slice('/api/direct/'.length).replace(/\//g, '');
      if (!itemId) return sendJson(res, 400, { error: 'bad_item_id' });
      // TV JellyfinRepository.streamUrl: Videos/{id}/stream?static=true&mediaSourceId={id}.
      const directParams = new URLSearchParams(url.search);
      if (!directParams.has('static')) directParams.set('static', 'true');
      const target = `Videos/${itemId}/stream?${directParams.toString()}`;
      const base = new URL(session.server.replace(/\/$/, '') + '/' + target);
      const tls = base.protocol === 'https:';
      const hostname = base.hostname.replace(/^\[|\]$/g, '');
      const port = parseInt(base.port || '', 10) || (tls ? 443 : 80);
      const addrs = await pinnedAddrsFor(session, hostname);
      if (!addrs.length) return sendJson(res, 502, { error: 'jellyfin_unreachable' });
      const headers = { 'Authorization': authHeader(session) };
      if (req.headers.range) headers.Range = req.headers.range;
      let streamed = false, lastErr = null;
      for (const addr of addrs) {
        try {
          const r = await rawUpstreamRequest({ tls, addr, hostname, port, path: base.pathname + base.search, method: 'GET', headers, timeoutMs: 60000, stream: true });
          const resp = r.streamResp;
          const out = {};
          for (const k of ['content-type', 'content-length', 'accept-ranges', 'content-range']) {
            if (resp.headers[k]) out[k] = resp.headers[k];
          }
          res.writeHead(resp.statusCode, { ...out, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS });
          resp.pipe(res);
          streamed = true;
          break;
        } catch (e) { lastErr = e; }
      }
      if (!streamed) { console.error('Driftfin stream unreachable', String(lastErr && lastErr.message || lastErr)); sendJson(res, 502, { error: 'jellyfin_unreachable' }); }
      return;
    }
    if (pathname.startsWith('/api/hls/')) {
      if (!session || !sessionAllowedForTarget(session)) return sendJson(res, 401, { error: 'not_signed_in' });
      const parts = pathname.slice('/api/hls/'.length).split('/').filter(Boolean);
      if (parts.length < 3) return sendJson(res, 400, { error: 'expected /api/hls/<itemId>/<mediaSourceId>/<playlist-or-segment>' });
      const [itemId, mediaSourceId, ...rest] = parts;
      const params = new URLSearchParams(url.search);
      if (!params.has('MediaSourceId')) params.set('MediaSourceId', mediaSourceId);
      const target = `Videos/${itemId}/${rest.join('/')}?${params.toString()}`;
      const r = await jellyfinRequest(session, target, 'GET');
      let body = r.body;
      let contentType = r.headers['content-type'] || '';
      if (contentType.includes('mpegurl') || rest[rest.length - 1].endsWith('.m3u8')) {
        const prefix = `/api/hls/${itemId}/${mediaSourceId}/`;
        const lines = body.toString('utf8').split('\n').map((line) => {
          const t = line.trim();
          if (!t || t.startsWith('#') || /^https?:\/\//.test(t)) return t;
          return prefix + t;
        });
        body = Buffer.from(lines.join('\n'), 'utf8');
        contentType = 'application/vnd.apple.mpegurl';
      }
      res.writeHead(r.status, { 'Content-Type': contentType, 'Content-Length': body.length, 'X-Content-Type-Options': 'nosniff', ...SEC_HEADERS });
      return res.end(body);
    }
    if (req.method === 'GET') return serveStatic(req, res, pathname);
    return sendJson(res, 405, { error: 'method_not_allowed' });
  } catch (e) {
    if (String(e && e.message) === 'payload_too_large' && !res.headersSent) return sendJson(res, 413, { error: 'payload_too_large' });
    if (!res.headersSent) sendJson(res, 502, { error: 'upstream_error', detail: String(e.message || e).slice(0, 500) });
    else res.end();
  }
});

ensureDir(DATA_DIR);
sessionSecret(); // create on first start if missing
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Driftfin Browser v1 on http://0.0.0.0:${PORT} (public=${PUBLIC_DIR}, data=${DATA_DIR})`);
});
