// Driftfin app-shell service worker (same pattern as the other Driftfin-family PWAs):
// caches STATIC files only. Jellyfin API calls, session traffic, and video are
// always network-only - nothing private or per-user is ever stored here.
const CACHE = 'driftfin-shell-v1';
const CORE = ['/', '/index.html', '/styles.css', '/app.js', '/vendor/hls.min.js', '/favicon.png', '/manifest.webmanifest'];
const STATIC_EXT = /\.(html|css|js|png|jpg|svg|ico|webmanifest)$/;
self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(CORE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});
async function networkFirst(req) {
  try {
    const res = await fetch(req);
    if (res.ok) { const c = await caches.open(CACHE); c.put(req, res.clone()); }
    return res;
  } catch (err) {
    const hit = await caches.match(req);
    if (hit) return hit;
    if (req.mode === 'navigate') return caches.match('/index.html');
    throw err;
  }
}
self.addEventListener('fetch', (e) => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  const path = url.pathname;
  if (path.startsWith('/api/')) return; // session + Jellyfin traffic: never cached
  if (req.mode === 'navigate' || path === '/app.js' || path === '/styles.css' || path === '/') {
    e.respondWith(networkFirst(req)); // code stays fresh across deploys; cache is the offline fallback
    return;
  }
  if (STATIC_EXT.test(path)) { // vendor lib, icons, avatars: cache-first
    e.respondWith(caches.match(req).then((hit) => hit || networkFirst(req)));
  }
});
