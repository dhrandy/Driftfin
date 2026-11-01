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
 *    in DATA_DIR (default /data). In Dockhand that directory is the
 *    bind volume /DATA/AppData/driftfin-browser:/data, so sessions
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
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { URL } = require('url');

const PORT = parseInt(process.env.PORT || '8740', 10);
const DATA_DIR = process.env.DATA_DIR || '/data';
const PUBLIC_DIR = process.env.PUBLIC_DIR || path.join(__dirname, '..', 'public');
const COOKIE_SECURE = String(process.env.COOKIE_SECURE || '').toLowerCase() === 'true';
const VERSION = '0.1.0';
const CLIENT = 'MediaBrowser Client="Driftfin Browser", Device="Browser", DeviceId="driftfin-browser", Version="0.1.0"';

const DEFAULT_SETTINGS = {
  titleLogos: true,
  showFormatBadges: true,
  skipIntro: true,
  autoplay: true,
  excludeAnimatedFromBanner: false,
};

function ensureDir(dir) { fs.mkdirSync(dir, { recursive: true }); }
function readJsonFile(file, fallback) {
  try { const v = JSON.parse(fs.readFileSync(file, 'utf8')); return v && typeof v === 'object' ? v : fallback; }
  catch { return fallback; }
}
function writeJsonFile(file, data) {
  ensureDir(path.dirname(file));
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2));
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
  if (!id || !sig || sig !== signSessionId(id, sessionSecret())) return null;
  const sessions = readJsonFile(path.join(DATA_DIR, 'sessions.json'), {});
  return sessions[id] || null;
}
function saveSession(session) {
  const file = path.join(DATA_DIR, 'sessions.json');
  const all = readJsonFile(file, {});
  all[session.id] = session;
  writeJsonFile(file, all);
}
function dropSession(req) {
  const raw = parseCookies(req).driftfin_session;
  if (!raw) return;
  const id = raw.split('.')[0];
  const file = path.join(DATA_DIR, 'sessions.json');
  const all = readJsonFile(file, {});
  delete all[id];
  writeJsonFile(file, all);
}
function cookieHeader(sessionId, maxAge) {
  let c = `driftfin_session=${sessionId}.${signSessionId(sessionId, sessionSecret())}; HttpOnly; SameSite=Lax; Path=/`;
  if (COOKIE_SECURE) c += '; Secure';
  if (maxAge !== undefined) c += `; Max-Age=${maxAge}`;
  return c;
}
function sendJson(res, status, obj, extraHeaders) {
  const body = Buffer.from(JSON.stringify(obj), 'utf8');
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': body.length, ...(extraHeaders || {}) });
  res.end(body);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}
function readJsonBody(req) {
  return readBody(req).then((buf) => {
    if (!buf.length) return {};
    try { return JSON.parse(buf.toString('utf8')); } catch { return {}; }
  });
}

/** One upstream call to the user's Jellyfin server. Resolves to {status, headers, body}. */
function jellyfinRequest(session, targetPath, method, body, contentType) {
  return new Promise((resolve, reject) => {
    const base = session.server.replace(/\/$/, '');
    const url = new URL(base + '/' + targetPath.replace(/^\//, ''));
    const lib = url.protocol === 'https:' ? https : http;
    const headers = { 'X-Emby-Authorization': `${CLIENT}, Token="${session.token}"` };
    if (body && body.length) headers['Content-Type'] = contentType || 'application/json';
    const req = lib.request(url, { method, headers, timeout: 60000 }, (resp) => {
      const chunks = [];
      resp.on('data', (c) => chunks.push(c));
      resp.on('end', () => resolve({ status: resp.statusCode, headers: resp.headers, body: Buffer.concat(chunks) }));
    });
    req.on('timeout', () => req.destroy(new Error('Jellyfin request timed out')));
    req.on('error', reject);
    if (body && body.length) req.write(body);
    req.end();
  });
}
function jellyfinUnauthenticated(server, targetPath, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(server.replace(/\/$/, '') + '/' + targetPath);
    const lib = url.protocol === 'https:' ? https : http;
    const payload = Buffer.from(JSON.stringify(body), 'utf8');
    const req = lib.request(url, {
      method: 'POST',
      headers: { 'X-Emby-Authorization': CLIENT, 'Content-Type': 'application/json', 'Content-Length': payload.length },
      timeout: 30000,
    }, (resp) => {
      const chunks = [];
      resp.on('data', (c) => chunks.push(c));
      resp.on('end', () => resolve({ status: resp.statusCode, body: Buffer.concat(chunks) }));
    });
    req.on('timeout', () => req.destroy(new Error('Jellyfin request timed out')));
    req.on('error', reject);
    req.write(payload);
    req.end();
  });
}

const MIME = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.json': 'application/json',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.ico': 'image/x-icon',
};
function serveStatic(req, res, pathname) {
  let filePath = pathname === '/' ? path.join(PUBLIC_DIR, 'index.html') : path.join(PUBLIC_DIR, pathname);
  const root = path.resolve(PUBLIC_DIR);
  if (!path.resolve(filePath).startsWith(root)) { res.writeHead(403); return res.end('forbidden'); }
  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('not found'); }
    const ext = path.extname(filePath).toLowerCase();
    res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream', 'Content-Length': data.length });
    res.end(data);
  });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://localhost');
    const pathname = url.pathname;

    if (pathname === '/api/health' || pathname === '/health') {
      return sendJson(res, 200, { ok: true, app: 'driftfin-browser', version: VERSION });
    }
    if (pathname === '/api/config') {
      return sendJson(res, 200, {
        jellyfinUrl: process.env.JELLYFIN_URL || '',
        cookieSecure: COOKIE_SECURE,
        version: VERSION,
      });
    }
    if (pathname === '/api/auth/login' && req.method === 'POST') {
      const payload = await readJsonBody(req);
      const serverUrl = String(payload.server || '').trim().replace(/\/$/, '');
      const username = String(payload.username || '').trim();
      const password = String(payload.password || '');
      if (!serverUrl || !/^https?:\/\//.test(serverUrl) || !username) {
        return sendJson(res, 400, { error: 'server must start with http:// or https://, and username is required' });
      }
      let result;
      try { result = await jellyfinUnauthenticated(serverUrl, 'Users/AuthenticateByName', { Username: username, Pw: password }); }
      catch (e) { return sendJson(res, 502, { error: 'jellyfin_unreachable', detail: String(e.message || e).slice(0, 500) }); }
      let parsed = {};
      try { parsed = JSON.parse(result.body.toString('utf8')); } catch {}
      if (result.status >= 400 || !parsed.AccessToken || !parsed.User || !parsed.User.Id) {
        return sendJson(res, result.status >= 400 ? result.status : 502, { error: 'jellyfin_rejected_sign_in' });
      }
      const session = {
        id: crypto.randomBytes(32).toString('hex'),
        server: serverUrl, token: parsed.AccessToken,
        userId: parsed.User.Id, username: parsed.User.Name || username,
        createdAt: new Date().toISOString(),
      };
      saveSession(session);
      return sendJson(res, 200, { ok: true, username: session.username, userId: session.userId },
        { 'Set-Cookie': cookieHeader(session.id) });
    }
    if (pathname === '/api/auth/logout') {
      dropSession(req);
      return sendJson(res, 200, { ok: true }, { 'Set-Cookie': cookieHeader('', 0).replace(/driftfin_session=\./, 'driftfin_session=') });
    }

    const session = getSession(req);
    if (pathname === '/api/auth/me') {
      if (!session) return sendJson(res, 401, { error: 'not_signed_in' });
      // TV SessionManager.validateToken uses Users/Me, not Users/{id}.
      const r = await jellyfinRequest(session, 'Users/Me', 'GET');
      res.writeHead(r.status, { 'Content-Type': r.headers['content-type'] || 'application/json', 'Content-Length': r.body.length });
      return res.end(r.body);
    }
    if (pathname === '/api/settings') {
      if (!session) return sendJson(res, 401, { error: 'not_signed_in' });
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
          if (key in payload) merged[key] = Boolean(payload[key]);
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
    if (pathname.startsWith('/api/jellyfin/')) {
      if (!session) return sendJson(res, 401, { error: 'not_signed_in' });
      const sub = pathname.slice('/api/jellyfin/'.length);
      const target = sub + (url.search || '');
      const body = (req.method === 'POST' || req.method === 'DELETE') ? await readBody(req) : null;
      const r = await jellyfinRequest(session, target, req.method, body && body.length ? body : null, req.headers['content-type']);
      res.writeHead(r.status, { 'Content-Type': r.headers['content-type'] || 'application/json', 'Content-Length': r.body.length });
      return res.end(r.body);
    }
    if (pathname.startsWith('/api/direct/')) {
      if (!session) return sendJson(res, 401, { error: 'not_signed_in' });
      const itemId = pathname.slice('/api/direct/'.length).replace(/\//g, '');
      if (!itemId) return sendJson(res, 400, { error: 'bad_item_id' });
      // TV JellyfinRepository.streamUrl: Videos/{id}/stream?static=true&mediaSourceId={id}.
      const directParams = new URLSearchParams(url.search);
      if (!directParams.has('static')) directParams.set('static', 'true');
      const target = `Videos/${itemId}/stream?${directParams.toString()}`;
      const base = session.server.replace(/\/$/, '');
      const upstream = new URL(base + '/' + target);
      const lib = upstream.protocol === 'https:' ? https : http;
      const headers = { 'X-Emby-Authorization': `${CLIENT}, Token="${session.token}"` };
      if (req.headers.range) headers.Range = req.headers.range;
      const up = lib.request(upstream, { method: 'GET', headers }, (resp) => {
        const out = {};
        for (const k of ['content-type', 'content-length', 'accept-ranges', 'content-range']) {
          if (resp.headers[k]) out[k] = resp.headers[k];
        }
        res.writeHead(resp.statusCode, out);
        resp.pipe(res);
      });
      up.on('error', () => sendJson(res, 502, { error: 'jellyfin_unreachable' }));
      up.end();
      return;
    }
    if (pathname.startsWith('/api/hls/')) {
      if (!session) return sendJson(res, 401, { error: 'not_signed_in' });
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
      res.writeHead(r.status, { 'Content-Type': contentType, 'Content-Length': body.length });
      return res.end(body);
    }
    if (req.method === 'GET') return serveStatic(req, res, pathname);
    return sendJson(res, 405, { error: 'method_not_allowed' });
  } catch (e) {
    if (!res.headersSent) sendJson(res, 502, { error: 'upstream_error', detail: String(e.message || e).slice(0, 500) });
    else res.end();
  }
});

ensureDir(DATA_DIR);
sessionSecret(); // create on first start if missing
server.listen(PORT, '0.0.0.0', () => {
  console.log(`Driftfin Browser v1 on http://0.0.0.0:${PORT} (public=${PUBLIC_DIR}, data=${DATA_DIR})`);
});
