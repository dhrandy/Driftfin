/* Driftfin Browser v1 frontend. No build step, plain JS.
   Every data view calls the Jellyfin API through this server's
   /api/jellyfin proxy. The Jellyfin token is never read here: it is
   held server-side and keyed by the HttpOnly session cookie.
   If the server is unreachable, views show an error state, not demo data.
   API shapes follow the TV app (app/ module: JellyfinApi.kt,
   Models.kt, DetailsScreen.kt, BannerPicks.kt, PlaybackNegotiator.kt,
   MediaSegments.kt). No Jellyfin field is invented here.
*/
'use strict';

const state = {
  userId: null, username: null, views: [], settings: null, config: null,
  currentItem: null, allEpisodes: [], seasons: [], selectedSeasonId: null,
  playTarget: null, introSegments: [], nextEpisode: null,
  mediaSourceId: null, playSessionId: null, progressTimer: null, upNextTimer: null,
  heroItem: null, subtitleTracks: [],
  heroList: [], heroIndex: 0, heroTimer: null,
  hls: null, playMethod: 'DirectPlay', playingItemId: null,
};

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s == null ? '' : s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

async function api(path, options) {
  const res = await fetch(path, { credentials: 'same-origin', ...(options || {}) });
  if (res.status === 401) { showView('signin'); throw new Error('not_signed_in'); }
  if (!res.ok) {
    let detail = '';
    try { const j = await res.json(); detail = j.error || j.detail || ''; } catch {}
    throw new Error(detail || ('request failed: ' + res.status));
  }
  const ct = res.headers.get('content-type') || '';
  return ct.includes('application/json') ? res.json() : res.text();
}
const jf = (path, options) => api('/api/jellyfin/' + path.replace(/^\//, ''), options);
// 1x1 transparent GIF used when an image is missing, so cards keep their shape.
// Artwork fallback: Jellyfin items/people without artwork 404 their image; swap in a
// titled placeholder tile instead of a blank box. Capture phase, before the per-img handlers.
document.addEventListener('error', (e) => {
  const img = e.target;
  if (!(img instanceof HTMLImageElement)) return;
  const inCard = img.closest('.card');
  const inPerson = img.closest('.cast-person');
  if (!inCard && !inPerson) return;
  if (img.dataset.fbDone) return;
  img.dataset.fbDone = '1';
  const holder = document.createElement('div');
  holder.className = 'img-fallback' + (inPerson ? ' person' : '');
  holder.setAttribute('aria-hidden', 'true');
  img.replaceWith(holder);
}, true);
const PIXEL = 'data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==';

/* ---------- Jellyfin helpers, mirroring TV Models.kt ---------- */
function formatAirDate(raw) {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(String(raw || '').trim());
  if (!m) return null;
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const month = parseInt(m[2], 10), day = parseInt(m[3], 10);
  if (parseInt(m[1], 10) < 1850 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  return months[month - 1] + ' ' + day + ', ' + m[1];
}
function runtimeLabel(item) {
  if (!item || !item.RunTimeTicks) return '';
  const total = Math.floor(item.RunTimeTicks / 600000000);
  if (total <= 0) return '';
  const h = Math.floor(total / 60), min = total % 60;
  return h > 0 ? h + 'h ' + min + 'm' : min + 'm';
}
const ICON_HEART = '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true" focusable="false"><path d="M12 21s-7.5-4.9-10-9.3C.4 8.6 2.2 5 5.7 5c2 0 3.5 1.1 4.3 2.6h4c.8-1.5 2.3-2.6 4.3-2.6 3.5 0 5.3 3.6 3.7 6.7C19.5 16.1 12 21 12 21z"/></svg>';
const ICON_STAR = '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true" focusable="false"><path d="M12 2l2.9 6.3 6.9.8-5.1 4.7 1.4 6.8L12 17.3 5.9 20.6l1.4-6.8L2.2 9.1l6.9-.8z"/></svg>';
const ICON_CHECK = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><polyline points="4 12.5 9.5 18 20 6.5"/></svg>';
const ICON_TRAILER = '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="9" x2="22" y2="9"/><line x1="7" y1="5" x2="5" y2="9"/><line x1="12" y1="5" x2="10" y2="9"/><line x1="17" y1="5" x2="15" y2="9"/><line x1="22" y1="5" x2="20" y2="9"/></svg>';
function episodeLabel(ep) {
  if (!ep || ep.ParentIndexNumber == null || ep.IndexNumber == null) return null;
  return 'S' + ep.ParentIndexNumber + ':E' + ep.IndexNumber;
}
function isResumeable(item) { return item && item.UserData && (item.UserData.PlaybackPositionTicks || 0) > 0; }
function playLabel(target) {
  if (!target) return 'Play';
  const verb = isResumeable(target) ? 'Resume' : 'Play';
  const ep = episodeLabel(target);
  return ep ? verb + ' ' + ep : verb;
}
/* TV EpisodeTarget.pick: in-progress episode (most recently played, then latest in order),
   else the episode after the last watched one, else the first unplayed one, else the first. */
function pickEpisodeTarget(all) {
  if (!all || !all.length) return null;
  const inProgress = all.map((e, i) => ({ e, i })).filter(({ e }) => e.UserData && e.UserData.Played !== true && (e.UserData.PlaybackPositionTicks || 0) > 0);
  if (inProgress.length) {
    inProgress.sort((a, b) => String(b.e.UserData.LastPlayedDate || '').localeCompare(String(a.e.UserData.LastPlayedDate || '')) || (b.i - a.i));
    return inProgress[0].e;
  }
  const watched = all.map((e, i) => ({ e, i })).filter(({ e }) => e.UserData && e.UserData.Played === true);
  if (watched.length) {
    watched.sort((a, b) => String(b.e.UserData.LastPlayedDate || '').localeCompare(String(a.e.UserData.LastPlayedDate || '')) || (b.i - a.i));
    const next = all[watched[0].i + 1];
    if (next) return next;
  }
  return all.find((e) => !(e.UserData && e.UserData.Played)) || all[0];
}
function pickNextEpisode(all, currentId) {
  const i = (all || []).findIndex((e) => e.Id === currentId);
  return i >= 0 ? (all[i + 1] || null) : null;
}
/* TV BannerPicks: untouched = not played, no resume position, no last-played date.
   Prefer rating 7.0 or more, else best-rated unwatched; exclude animation if set;
   require a backdrop; fall back to started-but-unfinished (Played != true) only if strict is empty. */
function pickHeroSet(pool, excludeAnimation) {
  let list = (pool || []).filter((i) => i.Type === 'Movie');
  if (excludeAnimation) list = list.filter((i) => !(i.Genres || []).some((g) => String(g).trim().toLowerCase() === 'animation'));
  const hasBackdrop = (i) => (i.BackdropImageTags && i.BackdropImageTags.length) || (i.ImageTags && i.ImageTags.Backdrop);
  const strict = list.filter((i) => i.UserData && i.UserData.Played !== true && !(i.UserData.PlaybackPositionTicks || 0) && !i.UserData.LastPlayedDate && hasBackdrop(i));
  const sorted = (arr) => arr.slice().sort((a, b) => (b.CommunityRating || 0) - (a.CommunityRating || 0));
  let eligible = sorted(strict);
  const high = eligible.filter((i) => (i.CommunityRating || 0) >= 7.0);
  if (high.length) eligible = high;
  if (!eligible.length) {
    const relaxed = sorted(list.filter((i) => i.UserData && i.UserData.Played !== true && hasBackdrop(i)));
    const relaxedHigh = relaxed.filter((i) => (i.CommunityRating || 0) >= 7.0);
    eligible = relaxedHigh.length ? relaxedHigh : relaxed;
  }
  // TV HomeScreen.kt HeroRotation: random 6 of the top 50, rotated every 10s.
  const top = eligible.slice(0, 50);
  for (let i = top.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [top[i], top[j]] = [top[j], top[i]];
  }
  return top.slice(0, 6);
}
function isKidsLibrary(view) {
  const name = String(view.Name || '').trim().toLowerCase();
  return name === 'kids shows' || name === 'kids movies';
}
function isVideoLibrary(view) {
  const t = String(view.CollectionType || '').toLowerCase();
  return !['music', 'musicvideos', 'books', 'livetv', 'playlists', 'photos', 'boxsets', 'trailers'].includes(t);
}
function logoUrlFor(item) {
  const id = item.Type === 'Episode' && item.SeriesId ? item.SeriesId : item.Id;
  return `/api/jellyfin/Items/${id}/Images/Logo?maxWidth=600`;
}
function titleHtml(item) {
  if (state.settings && state.settings.titleLogos) {
    const hasLogo = (item.ImageTags && item.ImageTags.Logo) || item.Type === 'Episode';
    // Real Jellyfin serves an episode's inherited series logo; where no logo exists
    // anywhere the image 404s and the text title must take over, not a blank space.
    if (hasLogo) return `<img src="${logoUrlFor(item)}" alt="${esc(item.Name)}" style="max-height:90px;max-width:60%" onerror="this.style.display='none';this.nextElementSibling.style.display=''"><h1 style="display:none">${esc(item.Name)}</h1>`;
  }
  return `<h1>${esc(item.Name)}</h1>`;
}
/* TV Resolution4K + HdrFormats, movie only, from the item's own MediaSources.
   4K = video width >= 3800 or height >= 2000. Dolby Vision comes from VideoRangeType,
   never from a title or artwork guess. */
function formatBadges(item) {
  if (!state.settings || !state.settings.showFormatBadges) return '';
  if (item.Type !== 'Movie') return '';
  const badges = [];
  for (const src of (item.MediaSources || [])) {
    const v = (src.MediaStreams || []).find((s) => s.Type === 'Video');
    if (!v) continue;
    if ((v.Width || 0) >= 3800 || (v.Height || 0) >= 2000) badges.push('4K');
    const t = String(v.VideoRangeType || '').trim();
    const hdr = String(v.VideoRange || '').toLowerCase() === 'hdr';
    if (/^dovi/i.test(t)) badges.push('Dolby Vision');
    else if (t.toLowerCase() === 'hdr10plus') badges.push('HDR10+');
    else if (t.toLowerCase() === 'hdr10') badges.push('HDR10');
    else if (t.toLowerCase() === 'hlg') badges.push('HLG');
    else if (hdr) badges.push('HDR');
  }
  return [...new Set(badges)].map((b) => `<span class="badge">${b}</span>`).join(' ');
}
function metaLine(item, opts) {
  const showRatings = !opts || opts.ratings !== false;
  const parts = [];
  if (showRatings && item.OfficialRating) parts.push(item.OfficialRating);
  if (item.Type === 'Series' && item.ChildCount != null) parts.push(item.ChildCount === 1 ? '1 Season' : item.ChildCount + ' Seasons');
  const air = formatAirDate(item.PremiereDate);
  if (item.Type === 'Series' && air) parts.push(air);
  else if (item.ProductionYear) parts.push(item.ProductionYear);
  if (showRatings && item.CommunityRating) parts.push(Math.round(item.CommunityRating * 10) + '%');
  return parts.map((p) => `<span>${esc(p)}</span>`).join(' ');
}
const img = (item, type, maxWidth) => {
  if (!item || !item.Id) return '';
  return `/api/jellyfin/Items/${item.Id}/Images/${type}?maxWidth=${maxWidth || 400}&quality=90`;
};
// TV JellyfinRepository.backdropUrl parity: an episode without its own backdrop
// uses its series backdrop, not a blank banner.
function detailBackdropCandidates(item) {
  const out = [];
  if (!item || !item.Id) return out;
  if (item.BackdropImageTags && item.BackdropImageTags.length) out.push(img(item, 'Backdrop', 1600));
  if (item.ImageTags && item.ImageTags.Thumb) out.push(img(item, 'Thumb', 1600));
  if (item.ParentBackdropItemId && item.ParentBackdropImageTags && item.ParentBackdropImageTags.length) {
    out.push(`/api/jellyfin/Items/${item.ParentBackdropItemId}/Images/Backdrop?maxWidth=1600&quality=90`);
  }
  if (item.SeriesPrimaryImageTag && item.SeriesId) out.push(`/api/jellyfin/Items/${item.SeriesId}/Images/Primary?maxWidth=1600&quality=90`);
  if (item.ImageTags && item.ImageTags.Primary) out.push(img(item, 'Primary', 1600));
  return out;
}
// A metadata tag is a claim, not proof: on a 404 the banner advances to the next
// candidate, and only when every candidate fails is it removed (leaving no gap).
window.__dfbNextBackdrop = function (el) {
  const list = state.detailBackdropList || [];
  const next = (state.detailBackdropIdx || 0) + 1;
  if (next < list.length) { state.detailBackdropIdx = next; el.src = list[next]; }
  else el.remove();
};
function cardImage(item) {
  // TV posterUrl: an episode falls back to its series poster.
  if (item.Type === 'Episode' && item.SeriesPrimaryImageTag && item.SeriesId) {
    return `/api/jellyfin/Items/${item.SeriesId}/Images/Primary?maxWidth=360&quality=90`;
  }
  return img(item, 'Primary', 360);
}
function cardImageWide(item) {
  if (item.BackdropImageTags && item.BackdropImageTags.length) return img(item, 'Backdrop', 640);
  if (item.Type === 'Episode' && item.SeriesId) return `/api/jellyfin/Items/${item.SeriesId}/Images/Backdrop?maxWidth=640&quality=90`;
  return img(item, 'Primary', 360);
}
function cardHtml(item, wide) {
  const watched = item.UserData && item.UserData.Played === true;
  // A full red bar under an already-watched card is noise (the check mark says it);
  // progress shows only for real partial progress, e.g. Continue Watching.
  const pct = !watched && item.UserData && item.UserData.PlayedPercentage && item.UserData.PlayedPercentage < 100
    ? Math.round(item.UserData.PlayedPercentage) : 0;
  const title = item.SeriesName ? `${item.SeriesName} - ${item.Name}` : item.Name;
  const label = episodeLabel(item) ? episodeLabel(item) + ' ' : '';
  return `<button class="card${wide ? ' wide' : ''}" data-id="${esc(item.Id)}" data-type="${esc(item.Type || '')}">
    <img src="${wide ? cardImageWide(item) : cardImage(item)}" alt="" loading="lazy">
    ${watched ? '<span class="watched">&#10003;</span>' : ''}
    <div class="card-title">${esc(label + title)}</div>
    ${pct ? `<div class="progress"><div style="width:${pct}%"></div></div>` : ''}
  </button>`;
}
function bindCards(container) {
  container.querySelectorAll('.card').forEach((el) => {
    el.addEventListener('click', () => { location.hash = '#/details?id=' + encodeURIComponent(el.dataset.id); });
  });
}
function rowHtml(title, items, wide, key) {
  if (!items || !items.length) return '';
  return `<section class="row"${key ? ` data-row="${esc(key)}"` : ''}><h2>${esc(title)}</h2><div class="row-scroll">${items.map((i) => cardHtml(i, wide)).join('')}</div></section>`;
}
function showView(name, navOverride) {
  document.body.classList.toggle('signed-out', name === 'signin');
  document.querySelectorAll('.view').forEach((v) => { v.hidden = true; });
  const el = $('view-' + name);
  if (el) el.hidden = false;
  const navName = navOverride || name;
  document.querySelectorAll('[data-nav]').forEach((a) => {
    a.classList.toggle('active', a.dataset.nav === navName);
  });
  window.scrollTo(0, 0);
}
const POSTER_WIDTHS = { Small: '140px', Medium: '180px', Large: '220px' };
function applyPosterSize() {
  const size = (state.settings && state.settings.posterSize) || 'Medium';
  document.documentElement.style.setProperty('--card-w', POSTER_WIDTHS[size] || POSTER_WIDTHS.Medium);
}
async function ensureSignedIn() {
  if (state.userId) return true;
  try {
    const me = await api('/api/auth/me');
    state.userId = me.Id; state.username = me.Name;
    state.settings = await api('/api/settings');
  applyPosterSize();
    applyPosterSize();
    $('userLabel').textContent = state.username || '';
    $('signoutBtn').hidden = false;
    return true;
  } catch { showView('signin'); return false; }
}

// ---------- Home ----------
async function loadHome() {
  if (!(await ensureSignedIn())) return;
  showView('home');
  const wrap = $('homeRows');
  wrap.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const uid = state.userId;
    const fields = 'Overview,Genres,MediaStreams,CommunityRating,ProductionYear';
    const [resumeRes, nextUp, latestMovies, latestEpisodes, favorites, views, heroRes] = await Promise.all([
      jf(`UserItems/Resume?userId=${uid}&limit=16&mediaTypes=Video&fields=${fields}`),
      jf(`Shows/NextUp?userId=${uid}&limit=16&enableResumable=false&fields=${fields}`),
      jf(`Items/Latest?userId=${uid}&limit=16&includeItemTypes=Movie&fields=${fields}`),
      jf(`Items/Latest?userId=${uid}&limit=16&includeItemTypes=Episode&fields=${fields}`),
      jf(`Items?userId=${uid}&Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending&limit=24&fields=${fields}`),
      jf(`UserViews?userId=${uid}`),
      jf(`Items?userId=${uid}&IncludeItemTypes=Movie&Recursive=true&Filters=IsUnplayed&SortBy=CommunityRating&SortOrder=Descending&limit=120&fields=Overview,Genres,CommunityRating,ProductionYear,BackdropImageTags`).catch(() => null),
    ]);
    state.views = (views.Items || []);
    const resume = (resumeRes.Items || []).filter(isResumeable);
    // Banner: keep the previous hero set if this banner request failed (TV row-82 rule).
    if (heroRes && heroRes.Items) {
      const picked = pickHeroSet(heroRes.Items, state.settings && state.settings.excludeAnimatedFromBanner);
      if (picked.length) { state.heroList = picked; state.heroIndex = 0; state.heroItem = picked[0]; }
    }
    if (state.settings && state.settings.homeBanner === false) {
      stopHeroRotation();
      $('hero').hidden = true;
    } else {
      $('hero').hidden = false;
      renderHero(state.heroItem);
      startHeroRotation();
    }
    const cwWide = !(state.settings && state.settings.continueWatchingPosters === true);
    let html = '';
    html += rowHtml('Continue Watching', resume, cwWide, 'resume');
    if (!state.settings || state.settings.showNextUpOnHome !== false) html += rowHtml('Next Up', nextUp.Items || [], true, 'nextup');
    html += rowHtml('Recently Added Movies', latestMovies.Items || latestMovies || []);
    html += rowHtml('Recently Added Episodes', latestEpisodes.Items || latestEpisodes || [], true);
    if (!state.settings || state.settings.showMyListOnHome !== false) html += rowHtml('Favorites', favorites.Items || []);
    // More like the last played title, TV RelatedItems rule: same kind, shared genre, at least 4.
    try {
      const last = await jf(`Items?userId=${uid}&IncludeItemTypes=Movie,Series&Recursive=true&SortBy=DatePlayed&SortOrder=Descending&limit=1&fields=Genres&filters=IsPlayed`);
      const lastItem = (last.Items || [])[0];
      if (lastItem && (lastItem.Genres || []).length) {
        const sim = await jf(`Items/${lastItem.Id}/Similar?userId=${uid}&limit=24&fields=Genres`);
        const wanted = new Set(lastItem.Genres.map((g) => String(g).toLowerCase()));
        const kept = (sim.Items || []).filter((c) => c.Id !== lastItem.Id && c.Type === lastItem.Type && (c.Genres || []).some((g) => wanted.has(String(g).toLowerCase())));
        if (kept.length >= 4) html += rowHtml('More like ' + lastItem.Name, kept.slice(0, 16));
      }
    } catch {}
    // No per-library Latest rows: the 10-31 trimmed Home design dropped them on TV and phone.
    wrap.innerHTML = html || '<p class="muted">No rows returned by Jellyfin for this user.</p>';
    bindCards(wrap);
    startHomeAutoRefresh();
  } catch (e) {
    wrap.innerHTML = `<p class="error">Could not load Home from Jellyfin: ${esc(e.message)}</p>`;
  }
}
let homeRefreshTimer = null;
function startHomeAutoRefresh() {
  stopHomeAutoRefresh();
  if (state.settings && state.settings.homeAutoRefresh === false) return;
  homeRefreshTimer = setInterval(refreshProgressRows, 60000);
}
function stopHomeAutoRefresh() { if (homeRefreshTimer) { clearInterval(homeRefreshTimer); homeRefreshTimer = null; } }
// TV homeAutoRefresh: only the progress rows update, in place, so scroll position is kept.
async function refreshProgressRows() {
  if ($('view-home').hidden || document.visibilityState !== 'visible') return;
  const uid = state.userId;
  if (!uid) return;
  try {
    const fields = 'Overview,Genres,MediaStreams,CommunityRating,ProductionYear';
    const [resumeRes, nextUp] = await Promise.all([
      jf(`UserItems/Resume?userId=${uid}&limit=16&mediaTypes=Video&fields=${fields}`),
      state.settings && state.settings.showNextUpOnHome === false ? Promise.resolve(null)
        : jf(`Shows/NextUp?userId=${uid}&limit=16&enableResumable=false&fields=${fields}`),
    ]);
    const wrap = $('homeRows');
    const cwWide = !(state.settings && state.settings.continueWatchingPosters === true);
    const replaceRow = (key, html) => {
      const old = wrap.querySelector(`[data-row="${key}"]`);
      if (old) { if (html) old.outerHTML = html; else old.remove(); }
      else if (html) wrap.insertAdjacentHTML('afterbegin', html);
    };
    replaceRow('resume', rowHtml('Continue Watching', (resumeRes.Items || []).filter(isResumeable), cwWide, 'resume'));
    if (nextUp) replaceRow('nextup', rowHtml('Next Up', nextUp.Items || [], true, 'nextup'));
    bindCards(wrap);
  } catch {}
}
function startHeroRotation() {
  stopHeroRotation();
  if (state.heroList.length < 2) return;
  state.heroTimer = setInterval(() => {
    state.heroIndex = (state.heroIndex + 1) % state.heroList.length;
    state.heroItem = state.heroList[state.heroIndex];
    renderHero(state.heroItem);
  }, 10000);
}
function stopHeroRotation() {
  if (state.heroTimer) clearInterval(state.heroTimer);
  state.heroTimer = null;
}
function truncateOverview(text) {
  const t = String(text || '');
  if (t.length <= 300) return t;
  const cut = t.slice(0, 300);
  const lastSpace = cut.lastIndexOf(' ');
  return (lastSpace > 200 ? cut.slice(0, lastSpace) : cut).trimEnd() + ' \u2026';
}
function renderHero(item) {
  const hero = $('hero');
  if (!item) { hero.innerHTML = '<div class="hero-empty">No unwatched movie found for the home banner.</div>'; return; }
  hero.innerHTML = `
    <img class="hero-backdrop" src="${img(item, 'Backdrop', 1600)}" alt="" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
    <div class="hero-body">
      ${titleHtml(item)}
      <div class="hero-meta">${metaLine(item, { ratings: !state.settings || state.settings.heroRatings !== false })} ${formatBadges(item)}</div>
      <p>${esc(truncateOverview(item.Overview))}</p>
      <button type="button" id="heroPlay">${isResumeable(item) ? 'Resume' : 'Play'}</button>
      <button type="button" class="secondary" id="heroInfo">More Info</button>
    </div>`;
  $('heroPlay').addEventListener('click', () => { location.hash = '#/player?id=' + encodeURIComponent(item.Id); });
  $('heroInfo').addEventListener('click', () => { location.hash = '#/details?id=' + encodeURIComponent(item.Id); });
}

// ---------- Library (Shows / Movies) ----------
let libraryKind = 'shows';
async function loadLibrary(kind, fresh) {
  libraryKind = kind;
  // TV LibraryScreen: the watched filter is in-memory per screen - a fresh library
  // navigation starts at All, while sort/order stay persisted settings.
  if (fresh) $('watchedFilter').value = '';
  if (!(await ensureSignedIn())) return;
  showView('library', kind);
  $('libraryTitle').textContent = kind === 'shows' ? 'Shows' : 'Movies';
  $('librarySort').value = (state.settings && state.settings.librarySort) || 'SortName';
  $('librarySortOrder').value = (state.settings && state.settings.librarySortOrder) || 'Ascending';
  const grid = $('libraryGrid');
  grid.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const uid = state.userId;
    if (!state.views.length) {
      const views = await jf(`UserViews?userId=${uid}`);
      state.views = views.Items || [];
    }
    const kids = $('kidsFilter').value;
    let viewsToUse = state.views.filter(isVideoLibrary);
    if (kids === 'kids') viewsToUse = viewsToUse.filter(isKidsLibrary);
    if (kids === 'nokids') viewsToUse = viewsToUse.filter((v) => !isKidsLibrary(v));
    const type = kind === 'shows' ? 'Series' : 'Movie';
    state.libraryPaging = {
      views: viewsToUse, type,
      sort: $('librarySort').value, order: $('librarySortOrder').value,
      watch: $('watchedFilter').value,
      offsets: {}, totals: {}, done: {},
    };
    const items = await fetchLibraryPage();
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No items returned for this filter.</p>';
    bindCards(grid);
    renderLibraryMore();
  } catch (e) {
    grid.innerHTML = `<p class="error">Could not load library from Jellyfin: ${esc(e.message)}</p>`;
  }
}

// Libraries page through Jellyfin with StartIndex so titles past the first 200
// are reachable (TV LibraryScreen fetches up to 4000 in one go; the browser pages).
const LIBRARY_PAGE = 200;
async function fetchLibraryPage() {
  const pg = state.libraryPaging;
  if (!pg) return [];
  const uid = state.userId;
  const jobs = pg.views.filter((v) => !pg.done[v.Id]).map(async (view) => {
    const start = pg.offsets[view.Id] || 0;
    const res = await jf(`Items?userId=${uid}&ParentId=${view.Id}&IncludeItemTypes=${pg.type}&Recursive=true&SortBy=${encodeURIComponent(pg.sort)}&SortOrder=${encodeURIComponent(pg.order)}${pg.watch ? `&Filters=${pg.watch}` : ''}&limit=${LIBRARY_PAGE}&StartIndex=${start}&fields=Overview,Genres,MediaStreams`);
    const got = res.Items || [];
    const total = typeof res.TotalRecordCount === 'number' ? res.TotalRecordCount : (start + got.length);
    pg.totals[view.Id] = total;
    pg.offsets[view.Id] = start + got.length;
    if (got.length === 0 || pg.offsets[view.Id] >= total) pg.done[view.Id] = true;
    return got;
  });
  const pages = await Promise.all(jobs);
  return pages.flat();
}
function renderLibraryMore() {
  const wrap = $('libraryMoreWrap');
  if (!wrap) return;
  const pg = state.libraryPaging;
  const remaining = !!(pg && pg.views.some((v) => !pg.done[v.Id]));
  wrap.hidden = !remaining;
  if (remaining) {
    const loaded = pg.views.reduce((n, v) => n + (pg.offsets[v.Id] || 0), 0);
    const total = pg.views.reduce((n, v) => n + (pg.totals[v.Id] || 0), 0);
    $('libraryMore').textContent = total > loaded ? `Load more (${loaded} of ${total})` : 'Load more';
  }
}
$('libraryMore').addEventListener('click', async () => {
  try {
    const items = await fetchLibraryPage();
    const grid = $('libraryGrid');
    grid.insertAdjacentHTML('beforeend', items.map((i) => cardHtml(i)).join(''));
    bindCards(grid);
    renderLibraryMore();
  } catch (e) {
    $('libraryMore').textContent = 'Load more failed - try again';
  }
});

// ---------- Details (full season switching) ----------
/* TV DetailsScreen MetaRow: year (movies, or a series with no premiere date), the play-target
   episode's air date + runtime (else the series premiere date), content-rating badge,
   N Seasons / runtime, community rating, then the 4K/HDR badges. */
function detailMetaHtml(item, target) {
  const parts = [];
  const series = item.Type === 'Series';
  const air = formatAirDate(item.PremiereDate);
  if (!(series && air) && item.ProductionYear) parts.push(`<span>${esc(String(item.ProductionYear))}</span>`);
  if (series) {
    const ep = target && target.Type === 'Episode' ? target : null;
    const epAir = ep ? formatAirDate(ep.PremiereDate) : null;
    if (epAir) parts.push(`<span>Aired ${esc(epAir)}${runtimeLabel(ep) ? ' · ' + esc(runtimeLabel(ep)) : ''}</span>`);
    else if (air) parts.push(`<span>Premiered ${esc(air)}</span>`);
  }
  if (item.OfficialRating) parts.push(`<span class="badge">${esc(item.OfficialRating)}</span>`);
  const duration = series
    ? (item.ChildCount != null ? (item.ChildCount === 1 ? '1 Season' : item.ChildCount + ' Seasons') : '')
    : runtimeLabel(item);
  if (duration) parts.push(`<span>${esc(duration)}</span>`);
  if (typeof item.CommunityRating === 'number') parts.push(`<span>★ ${esc(item.CommunityRating.toFixed(1))}</span>`);
  const badges = formatBadges(item);
  return parts.join(' ') + (badges ? ' ' + badges : '');
}
/* TV genreLine: a series leads with its premiere date when the episode air date took the
   meta-row slot, then the series status, then genres. Movies and episodes show genres only. */
function genreLineHtml(item, target) {
  const segs = [];
  if (item.Type === 'Series') {
    const epAired = target && target.Type === 'Episode' ? formatAirDate(target.PremiereDate) : null;
    const air = formatAirDate(item.PremiereDate);
    if (epAired && air) segs.push('Premiered ' + air);
    const st = String(item.SeriesStatus || '').trim().toLowerCase();
    const status = st === 'continuing' ? 'Continuing' : st === 'ended' ? 'Ended' : (st === 'canceled' || st === 'cancelled') ? 'Canceled' : null;
    if (status) segs.push(status);
  }
  const genres = (item.Genres || []).join(' · ');
  if (genres) segs.push(genres);
  return segs.length ? `<p class="detail-genres muted">${segs.map(esc).join('  ·  ')}</p>` : '';
}
/* TV Trailers.youtubeVideoId: the 11-char video id from the common YouTube link shapes. */
function youtubeVideoId(url) {
  if (!url || !String(url).trim()) return null;
  let u;
  try { u = new URL(String(url).trim()); } catch { return null; }
  if (u.protocol !== 'https:' && u.protocol !== 'http:') return null;
  const host = u.hostname.toLowerCase();
  const hosts = ['youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com', 'youtube-nocookie.com', 'www.youtube-nocookie.com'];
  let id = null;
  if (host === 'youtu.be') id = u.pathname.replace(/^\//, '').split('/')[0];
  else if (hosts.includes(host)) {
    const p = u.pathname;
    if (p === '/watch') id = u.searchParams.get('v');
    else if (p.startsWith('/embed/') || p.startsWith('/shorts/') || p.startsWith('/v/')) id = p.slice(p.indexOf('/', 1) + 1).split('/')[0];
  }
  return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
}
/* TV RelatedItems.filter: same type, shares a genre with the source, at least 4 left -
   otherwise the row stays hidden rather than vouching for loose matches. */
function relatedItemsFilter(source, candidates) {
  const genres = (source.Genres || []).map((g) => String(g).toLowerCase());
  if (!genres.length) return [];
  const wanted = new Set(genres);
  const kept = (candidates || []).filter((c) => c.Id !== source.Id && c.Type === source.Type && (c.Genres || []).some((g) => wanted.has(String(g).toLowerCase())));
  return kept.length >= 4 ? kept : [];
}
/* TV DetailsScreen.load: trailer, similar items and episode 4K badges fill in after the page
   shows; a failed lookup hides that control/row instead of erroring the page. */
async function fillDetailExtras(item, seriesId) {
  const uid = state.userId;
  const still = () => state.currentItem && state.currentItem.Id === item.Id;
  const jobs = [];
  jobs.push((async () => {
    try {
      // Server trailer files first (TV Trailers.pick; some servers count remote trailers in
      // LocalTrailerCount, so an empty local list falls through), then the YouTube links.
      if ((item.LocalTrailerCount || 0) > 0) {
        const local = await jf(`Items/${item.Id}/LocalTrailers?userId=${uid}`).catch(() => null);
        const first = Array.isArray(local) && local[0] && local[0].Id ? local[0] : null;
        if (first) { state.detailTrailer = { kind: 'local', id: first.Id }; return; }
      }
      for (const r of (item.RemoteTrailers || [])) {
        const vid = youtubeVideoId(r && r.Url);
        if (vid) { state.detailTrailer = { kind: 'youtube', id: vid }; return; }
      }
    } catch { /* hidden */ }
  })());
  if (seriesId) {
    jobs.push((async () => {
      // The server's own is4K filter, ids only; failure just means no episode badges.
      try {
        const res = await jf(`Items?userId=${uid}&parentId=${seriesId}&includeItemTypes=Episode&recursive=true&is4K=true&enableImages=false&enableUserData=false&limit=2000`);
        state.fourKEpisodeIds = new Set((res.Items || []).map((i) => i.Id));
      } catch { /* no badges */ }
    })());
  }
  jobs.push((async () => {
    try {
      const sim = await jf(`Items/${item.Id}/Similar?userId=${uid}&limit=24&fields=Genres`);
      state.detailSimilar = relatedItemsFilter(item, sim.Items || []);
    } catch { /* row hidden */ }
  })());
  await Promise.all(jobs);
  if (still()) renderDetails();
}

async function goDetails(id, keepSeasonId) {
  if (!(await ensureSignedIn())) return;
  showView('details');
  const el = $('details');
  el.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const uid = state.userId;
    const item = await jf(`Items/${id}?userId=${uid}`);
    state.currentItem = item;
    state.allEpisodes = []; state.seasons = []; state.playTarget = item; state.selectedSeasonId = null;
    state.detailTrailer = null; state.detailSimilar = []; state.fourKEpisodeIds = new Set();
    if (item.Type === 'Movie' || item.Type === 'Series') {
      try { state.watchlistIds = ((await api('/api/watchlist')) || {}).ids || []; } catch { state.watchlistIds = []; }
    }
    const seriesId = item.Type === 'Series' ? item.Id : (item.Type === 'Episode' && item.SeriesId ? item.SeriesId : null);
    if (seriesId) {
      // TV DetailsScreen: seasons via Items (parentId + IncludeItemTypes=Season, SortName),
      // all episodes in one call (limit 2000, fields Overview,PremiereDate,People), filtered client-side.
      // Episode entries (Continue Watching / Next Up) get the same season+episode browsing
      // WITHOUT losing the requested episode as the Play/Resume target.
      const [seasonsRes, episodesRes] = await Promise.all([
        jf(`Items?userId=${uid}&parentId=${seriesId}&includeItemTypes=Season&sortBy=SortName`),
        jf(`Shows/${seriesId}/Episodes?userId=${uid}&fields=Overview,PremiereDate,People&limit=2000`),
      ]);
      state.seasons = seasonsRes.Items || [];
      state.allEpisodes = episodesRes.Items || [];
      state.playTarget = item.Type === 'Episode' ? item : (pickEpisodeTarget(state.allEpisodes) || item);
      state.selectedSeasonId = keepSeasonId || (state.playTarget && state.playTarget.SeasonId) || (state.seasons[0] && state.seasons[0].Id) || null;
    }
    renderDetails();
    fillDetailExtras(item, seriesId);
  } catch (e) {
    el.innerHTML = `<p class="error">Could not load details from Jellyfin: ${esc(e.message)}</p>`;
  }
}
function renderDetails() {
  const el = $('details');
  const item = state.currentItem;
  if (!item) return;
  const uid = state.userId;
  const isSeries = item.Type === 'Series';
  // Episode entries carry their series' season/episode browsing (loaded in goDetails).
  const hasSeasons = isSeries || (item.Type === 'Episode' && state.seasons.length > 0);
  const target = state.playTarget || item;
  const isFav = item.UserData && item.UserData.IsFavorite;
  // Watched button acts on the play-target episode for a series, never the series id
  // (marking the series id would mark every episode watched on the server).
  const watchedTarget = hasSeasons && target.Type === 'Episode' ? target : item;
  const watchedPlayed = watchedTarget.UserData && watchedTarget.UserData.Played;
  let seasonsHtml = '';
  if (hasSeasons) {
    const shown = state.allEpisodes.filter((ep) => ep.SeasonId === state.selectedSeasonId);
    const list = shown.length ? shown : state.allEpisodes;
    const chips = state.seasons.length > 1 ? state.seasons.map((s) => `<button type="button" class="season-chip${s.Id === state.selectedSeasonId ? ' active' : ''}" data-season="${esc(s.Id)}">${esc(s.Name)}</button>`).join(' ') : '';
    const episodesHtml = list.map((ep) => {
      const pct = ep.UserData && ep.UserData.PlayedPercentage ? Math.round(ep.UserData.PlayedPercentage) : 0;
      const air = formatAirDate(ep.PremiereDate);
      const ethumb = ep.ImageTags && ep.ImageTags.Primary
        ? `/api/jellyfin/Items/${ep.Id}/Images/Primary?maxWidth=480&quality=90`
        : (ep.ParentBackdropItemId ? `/api/jellyfin/Items/${ep.ParentBackdropItemId}/Images/Backdrop?maxWidth=480&quality=90` : '');
      const ep4k = state.fourKEpisodeIds && state.fourKEpisodeIds.has(ep.Id);
      return `<div class="episode${ep.Id === item.Id ? ' current' : ''}" data-id="${esc(ep.Id)}">
        ${ethumb ? `<img class="episode-thumb" src="${ethumb}" alt="" loading="lazy" onerror="this.remove()">` : ''}
        <div class="episode-main">
          <span><strong>${esc(episodeLabel(ep) || '')}</strong> ${esc(ep.Name)}${ep4k ? ' <span class="badge">4K</span>' : ''}</span>
          <span class="muted">${air ? esc(air) + ' · ' : ''}${runtimeLabel(ep)}${ep.UserData && ep.UserData.Played ? ' · ✓ Watched' : ''}</span>
          ${ep.Overview ? `<div class="episode-overview muted">${esc(ep.Overview)}</div>` : ''}
          ${pct ? `<div class="progress"><div style="width:${pct}%"></div></div>` : ''}
        </div>
        <div class="episode-actions">
          <button type="button" class="episode-play icon-btn" data-id="${esc(ep.Id)}" aria-label="${esc((isResumeable(ep) ? 'Resume ' : 'Play ') + (episodeLabel(ep) || ep.Name))}" title="${esc((isResumeable(ep) ? 'Resume' : 'Play'))}"><svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true" focusable="false"><path d="M8 5v14l11-7z"/></svg></button>
          <button type="button" class="episode-watched icon-btn secondary${ep.UserData && ep.UserData.Played ? ' on' : ''}" data-id="${esc(ep.Id)}" aria-pressed="${ep.UserData && ep.UserData.Played ? 'true' : 'false'}" aria-label="${esc(ep.UserData && ep.UserData.Played ? 'Mark unwatched' : 'Mark watched')}" title="${esc(ep.UserData && ep.UserData.Played ? 'Mark unwatched' : 'Mark watched')}"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><polyline points="4 12.5 9.5 18 20 6.5"/></svg></button>
        </div>
      </div>`;
    }).join('') || '<p class="muted">No episodes returned for this season.</p>';
    seasonsHtml = `<h2>Seasons and Episodes</h2>${chips ? `<div class="season-chips">${chips}</div>` : ''}${episodesHtml}`;
  }
  // TV DetailsScreen cast rules:
  // - Series with a selected season: actors of THAT season's episodes, episode order,
  //   deduped by Id (name fallback), capped at 15. Series-wide People are never
  //   substituted (a regular may not appear in this season), and an empty result
  //   hides the row instead of showing unrelated cast.
  // - Other items: the item's actors, then the play-target episode's actors (merged).
  const actorsOf = (people) => (people || []).filter((p) => p.Type === 'Actor');
  const dedupPeople = (list) => {
    const seen = new Set(); const out = [];
    for (const p of list) { const k = p.Id || 'name:' + (p.Name || ''); if (!seen.has(k)) { seen.add(k); out.push(p); } }
    return out;
  };
  const seasonEps = hasSeasons ? state.allEpisodes.filter((ep) => ep.SeasonId === state.selectedSeasonId) : [];
  const cast = hasSeasons
    ? dedupPeople((seasonEps.length ? seasonEps : state.allEpisodes).flatMap((ep) => actorsOf(ep.People))).slice(0, 15)
    : dedupPeople([...actorsOf(item.People), ...(target && target !== item ? actorsOf(target.People) : [])]).slice(0, 15);
  const castHtml = cast.length
    ? `<h2>Cast</h2><div class="cast-row">${cast.map((p) => `
        <button type="button" class="cast-person" data-person="${esc(p.Id)}">
          <img src="/api/jellyfin/Items/${esc(p.Id)}/Images/Primary?maxWidth=220&quality=90" alt="" loading="lazy" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
          <div class="cast-name">${esc(p.Name || '')}</div>
          ${p.Role ? `<div class="cast-role">${esc(p.Role)}</div>` : ''}
        </button>`).join('')}</div>`
    : '';
  const similarHtml = state.detailSimilar && state.detailSimilar.length ? rowHtml('More Like This', state.detailSimilar, false) : '';
  // Async fills re-render this page; keep the already-advanced backdrop candidate
  // for the same item instead of restarting the chain (and re-firing 404s).
  if (state.detailBackdropItemId !== item.Id) {
    state.detailBackdropItemId = item.Id;
    state.detailBackdropList = detailBackdropCandidates(item);
    state.detailBackdropIdx = 0;
  }
  el.innerHTML = `
    ${state.detailBackdropList.length && state.detailBackdropIdx < state.detailBackdropList.length ? `<img class="details-backdrop" src="${state.detailBackdropList[state.detailBackdropIdx]}" alt="" onerror="window.__dfbNextBackdrop(this)">` : ''}
    ${titleHtml(item)}
    <div class="hero-meta">${detailMetaHtml(item, target)}</div>
    <p>${esc(item.Overview || '')}</p>
    ${genreLineHtml(item, target)}
    <div class="detail-actions">
      <button type="button" id="detailPlay">${playLabel(target)}</button>
      ${isResumeable(target) ? `<button type="button" class="secondary" id="detailFromStart">Play from start</button>` : ''}
      <button type="button" class="icon-btn" id="detailTrailer" aria-label="Watch trailer" title="Watch trailer"${state.detailTrailer ? '' : ' hidden'}>${ICON_TRAILER}</button>
      <button type="button" class="icon-btn${watchedPlayed ? ' on' : ''}" id="detailWatched" aria-label="${watchedPlayed ? 'Mark unwatched' : 'Mark watched'}${target.Type === 'Episode' ? ' (' + esc(episodeLabel(target) || target.Name) + ')' : ''}" title="${watchedPlayed ? 'Mark unwatched' : 'Mark watched'}" aria-pressed="${watchedPlayed ? 'true' : 'false'}">${ICON_CHECK}</button>
      ${(() => { const wlId = (item.Type === 'Episode' && item.SeriesId) ? item.SeriesId : item.Id; const wlOn = (state.watchlistIds || []).includes(wlId); return (item.Type === 'Movie' || item.Type === 'Series' || item.Type === 'Episode') && (!state.settings || state.settings.showWatchlist !== false) ? `<button type="button" class="icon-btn${wlOn ? ' on' : ''}" id="detailStar" aria-label="${wlOn ? 'On Watchlist - remove' : 'Add to Watchlist'}${item.Type === 'Episode' ? ' (series)' : ''}" title="${wlOn ? 'On Watchlist' : 'Add to Watchlist'}" aria-pressed="${wlOn ? 'true' : 'false'}">${ICON_STAR}</button>` : ''; })()}
      <button type="button" class="icon-btn${isFav ? ' on' : ''}" id="detailFav" aria-label="${isFav ? 'Remove from My List' : 'Add to My List'}" title="${isFav ? 'Remove from My List' : 'Add to My List'}" aria-pressed="${isFav ? 'true' : 'false'}">${ICON_HEART}</button>
      <span id="detailActionNote" class="muted" role="status"></span>
    </div>
    ${castHtml}
    ${seasonsHtml}
    ${similarHtml}`;
  el.querySelectorAll('.cast-person').forEach((btn) => btn.addEventListener('click', () => { location.hash = '#/actor?id=' + encodeURIComponent(btn.dataset.person); }));
  bindCards(el);
  $('detailPlay').addEventListener('click', () => { location.hash = '#/player?id=' + encodeURIComponent(target.Id); });
  const fromStart = $('detailFromStart');
  if (fromStart) fromStart.addEventListener('click', () => { location.hash = '#/player?id=' + encodeURIComponent(target.Id) + '&start=1'; });
  const trailerBtn = $('detailTrailer');
  if (trailerBtn && state.detailTrailer) trailerBtn.addEventListener('click', () => {
    // Local trailer files play in the app player; YouTube opens in a new tab.
    if (state.detailTrailer.kind === 'local') location.hash = '#/player?id=' + encodeURIComponent(state.detailTrailer.id);
    else window.open('https://www.youtube.com/watch?v=' + state.detailTrailer.id, '_blank', 'noopener');
  });
  $('detailFav').addEventListener('click', async () => {
    const method = isFav ? 'DELETE' : 'POST';
    const updated = await api(`/api/jellyfin/UserFavoriteItems/${item.Id}?userId=${uid}`, { method });
    if (updated) state.currentItem.UserData = updated;
    renderDetails();
  });
  $('detailWatched').addEventListener('click', async () => {
    await toggleWatched(watchedTarget);
  });
  const starBtn = $('detailStar');
  if (starBtn) starBtn.addEventListener('click', async () => {
    const wlId = (item.Type === 'Episode' && item.SeriesId) ? item.SeriesId : item.Id;
    const r = await api('/api/watchlist', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: wlId }) });
    if (r && r.capped) { const note = $('detailActionNote'); if (note) note.textContent = 'Watchlist full (500 titles)'; return; }
    if (r) {
      const ids = new Set(state.watchlistIds || []);
      if (r.starred) ids.add(wlId); else ids.delete(wlId);
      state.watchlistIds = [...ids];
    }
    renderDetails();
  });
  el.querySelectorAll('.season-chip').forEach((btn) => btn.addEventListener('click', () => {
    // Season switching only changes the list; Play/Resume keeps pointing at the series target.
    state.selectedSeasonId = btn.dataset.season;
    renderDetails();
  }));
  el.querySelectorAll('.episode-play').forEach((btn) => btn.addEventListener('click', (e) => { e.stopPropagation(); location.hash = '#/player?id=' + encodeURIComponent(btn.dataset.id); }));
  el.querySelectorAll('.episode-watched').forEach((btn) => btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    const ep = state.allEpisodes.find((x) => x.Id === btn.dataset.id);
    if (ep) await toggleWatched(ep);
  }));
  el.querySelectorAll('.episode').forEach((node) => node.addEventListener('click', (e) => {
    if (e.target.closest('button')) return;
    location.hash = '#/player?id=' + encodeURIComponent(node.dataset.id);
  }));
}
async function toggleWatched(target) {
  const uid = state.userId;
  const played = target.UserData && target.UserData.Played;
  const method = played ? 'DELETE' : 'POST';
  const updated = await api(`/api/jellyfin/UserPlayedItems/${target.Id}?userId=${uid}`, { method });
  if (!updated) return;
  if (target.Type === 'Episode') {
    state.allEpisodes = state.allEpisodes.map((ep) => ep.Id === target.Id ? { ...ep, UserData: updated } : ep);
    if (state.playTarget && state.playTarget.Id === target.Id) {
      // TV advancedTargetAfterWatched: watched -> next episode in server order, else the general pick;
      // unwatched -> back onto the just-cleared episode.
      state.playTarget = updated.Played ? (pickNextEpisode(state.allEpisodes, target.Id) || pickEpisodeTarget(state.allEpisodes) || state.playTarget)
        : (state.allEpisodes.find((ep) => ep.Id === target.Id) || state.playTarget);
    }
    // Keep the currently selected season after the toggle.
    renderDetails();
  } else {
    state.currentItem.UserData = updated;
    renderDetails();
  }
}

// ---------- Search / My Stuff ----------
const hasPoster = (i) => (i.ImageTags && i.ImageTags.Primary) || (i.SeriesPrimaryImageTag && i.SeriesId);
// TV SearchScreen.recommend: server suggestions filtered to confirmed-unplayed (fail closed),
// falling back to top-rated unplayed titles when suggestions come up short.
async function recommendTitles(type) {
  const uid = state.userId;
  const suggested = (await jf(`Items/Suggestions?userId=${uid}&type=${type}&limit=24`).catch(() => null)) || {};
  const withPoster = (suggested.Items || []).filter(hasPoster);
  if (withPoster.length) {
    const unplayed = await jf(`Items?userId=${uid}&Ids=${withPoster.map((i) => i.Id).join(',')}&filters=IsUnplayed&limit=${withPoster.length}`).catch(() => null);
    const keep = new Set(((unplayed && unplayed.Items) || []).map((i) => i.Id));
    const unwatched = withPoster.filter((i) => keep.has(i.Id));
    if (unwatched.length) return unwatched.slice(0, 20);
  }
  const fallback = (await jf(`Items?userId=${uid}&includeItemTypes=${type}&recursive=true&sortBy=CommunityRating&sortOrder=Descending&limit=40&filters=IsUnplayed`).catch(() => null)) || {};
  return (fallback.Items || []).filter(hasPoster).slice(0, 20);
}
async function loadSearchRecommendations() {
  if (!(await ensureSignedIn())) return;
  showView('search');
  const recs = $('searchRecs');
  if ($('searchPeople')) $('searchPeople').innerHTML = '';
  $('searchResults').innerHTML = '';
  if (state.settings && state.settings.searchRecommendations === false) { recs.innerHTML = ''; return; }
  recs.innerHTML = '<p class="muted">Loading recommendations…</p>';
  const [movies, shows] = await Promise.all([recommendTitles('Movie'), recommendTitles('Series')]);
  const html = rowHtml('Recommended Movies', movies) + rowHtml('Recommended Shows', shows);
  recs.innerHTML = html || '';
  bindCards(recs);
}
async function loadSearch(term) {
  if (!(await ensureSignedIn())) return;
  showView('search');
  const grid = $('searchResults');
  const peopleWrap = $('searchPeople');
  if ($('searchRecs')) $('searchRecs').innerHTML = '';
  if (!term) { grid.innerHTML = ''; if (peopleWrap) peopleWrap.innerHTML = ''; loadSearchRecommendations(); return; }
  grid.innerHTML = '<p class="muted">Searching Jellyfin…</p>';
  try {
    // TV SearchScreen parity: titles plus an Actors row from the Persons endpoint.
    const [res, peopleRes] = await Promise.all([
      jf(`Items?userId=${state.userId}&SearchTerm=${encodeURIComponent(term)}&IncludeItemTypes=Movie,Series,Episode&Recursive=true&limit=30&fields=Overview`),
      jf(`Persons?userId=${state.userId}&searchTerm=${encodeURIComponent(term)}&limit=12&personTypes=Actor`).catch(() => ({ Items: [] })),
    ]);
    const items = res.Items || [];
    const people = (peopleRes.Items || []).filter((p) => p.Name);
    if (peopleWrap) {
      peopleWrap.innerHTML = people.length ? `<div class="search-people"><h2>People</h2><div class="cast-row">${people.map((p) => `
        <button type="button" class="cast-person" data-person="${esc(p.Id)}">
          <img src="/api/jellyfin/Items/${esc(p.Id)}/Images/Primary?maxWidth=220&quality=90" alt="" loading="lazy">
          <div class="cast-name">${esc(p.Name)}</div>
        </button>`).join('')}</div></div>` : '';
      peopleWrap.querySelectorAll('.cast-person').forEach((btn) => btn.addEventListener('click', () => { location.hash = '#/actor?id=' + encodeURIComponent(btn.dataset.person); }));
    }
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : (people.length ? '' : '<p class="muted">No results from Jellyfin.</p>');
    bindCards(grid);
  } catch (e) { grid.innerHTML = `<p class="error">Search failed: ${esc(e.message)}</p>`; }
}
// My Stuff sections in the TV order: Favorites (Jellyfin heart, unchanged semantics),
// Watchlist (Driftfin's own star, stored server-side per server+user), Collections (box sets).
let mystuffSection = 'favorites';
async function loadMyStuff() {
  if (!(await ensureSignedIn())) return;
  showView('mystuff');
  const grid = $('mystuffGrid');
  const wlBtn = document.querySelector('#mystuffTabs button[data-section="watchlist"]');
  if (wlBtn) wlBtn.hidden = Boolean(state.settings && state.settings.showWatchlist === false);
  if (mystuffSection === 'watchlist' && state.settings && state.settings.showWatchlist === false) mystuffSection = 'favorites';
  document.querySelectorAll('#mystuffTabs button').forEach((b) => b.classList.toggle('active', b.dataset.section === mystuffSection));
  grid.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const uid = state.userId;
    if (mystuffSection === 'watchlist') {
      const ids = ((await api('/api/watchlist')) || {}).ids || [];
      state.watchlistIds = ids;
      const emptyText = '<p class="muted">Nothing on your watchlist yet. Open a movie or show and choose the star.</p>';
      if (!ids.length) { grid.innerHTML = emptyText; return; }
      const res = await jf(`Items?userId=${uid}&Ids=${encodeURIComponent(ids.join(','))}&fields=Overview`);
      const found = new Map((res.Items || []).map((i) => [i.Id, i]));
      const ordered = ids.map((id) => found.get(id)).filter(Boolean);
      // Never prune: a missing id can mean deleted, permission-filtered, or a
      // partial/unavailable server response - the saved list is kept as is.
      const missing = ids.length - ordered.length;
      const note = missing > 0
        ? `<p class="muted">${missing} saved ${missing === 1 ? 'title is' : 'titles are'} not available from the server right now. ${missing === 1 ? 'It stays' : 'They stay'} on your watchlist.</p>`
        : '';
      grid.innerHTML = (ordered.length ? ordered.map((i) => cardHtml(i)).join('') : (missing ? '' : emptyText)) + note;
      bindCards(grid);
      return;
    }
    if (mystuffSection === 'collections') {
      const res = await jf(`Items?userId=${uid}&IncludeItemTypes=BoxSet&Recursive=true&SortBy=SortName&fields=Overview`);
      const items = res.Items || [];
      grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No collections found on this server.</p>';
      bindCards(grid);
      return;
    }
    const res = await jf(`Items?userId=${uid}&Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending&limit=100&fields=Overview`);
    const items = res.Items || [];
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No favorites in Jellyfin yet.</p>';
    bindCards(grid);
  } catch (e) { grid.innerHTML = `<p class="error">Could not load My Stuff: ${esc(e.message)}</p>`; }
}
document.querySelectorAll('#mystuffTabs button').forEach((b) => b.addEventListener('click', () => { mystuffSection = b.dataset.section; loadMyStuff(); }));

// ---------- Actor page ----------
async function goActor(personId) {
  if (!(await ensureSignedIn())) return;
  showView('actor');
  const el = $('actor');
  el.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const uid = state.userId;
    const [person, works] = await Promise.all([
      jf(`Items/${personId}?userId=${uid}`),
      jf(`Items?userId=${uid}&PersonIds=${encodeURIComponent(personId)}&Recursive=true&IncludeItemTypes=Movie,Series&limit=60&fields=Overview`),
    ]);
    const items = works.Items || [];
    el.innerHTML = `
      <div class="actor-head">
        <img src="/api/jellyfin/Items/${esc(personId)}/Images/Primary?maxWidth=320&quality=90" alt="" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
        <div>
          <h1>${esc(person.Name || '')}</h1>
          ${person.Overview ? `<p>${esc(person.Overview.slice(0, 1200))}</p>` : ''}
        </div>
      </div>
      <h2>On your server</h2>
      ${items.length ? `<div class="grid">${items.map((i) => cardHtml(i)).join('')}</div>` : '<p class="muted">No titles with this person on this server.</p>'}`;
    bindCards(el);
  } catch (e) {
    el.innerHTML = `<p class="error">Could not load this person from Jellyfin: ${esc(e.message)}</p>`;
  }
}

// ---------- Player ----------
async function goPlayer(id, opts) {
  opts = opts || {};
  if (!(await ensureSignedIn())) return;
  stopPlayback(true); // end any previous session cleanly before starting a new one
  showView('player');
  const video = $('player');
  const note = $('playerNote');
  note.textContent = 'Loading playback info from Jellyfin…';
  stopProgress();
  $('upNext').hidden = true;
  try {
    const uid = state.userId;
    const item = await jf(`Items/${id}?userId=${uid}`);
    const startTicks = opts.fromStart ? 0 : (opts.preservePosition != null ? Math.round(opts.preservePosition * 10000000) : (item.UserData && item.UserData.PlaybackPositionTicks ? item.UserData.PlaybackPositionTicks : 0));
    // PlaybackInfo body follows TV PlaybackNegotiator.playbackInfoBody, with a browser
    // DeviceProfile (h264/vp9/av1 video, aac/mp3/opus audio, HLS transcode, text subtitles only).
    const infoBody = {
      UserId: uid,
      StartTimeTicks: startTicks,
      EnableDirectPlay: true,
      EnableDirectStream: true,
      EnableTranscoding: true,
      MaxStreamingBitrate: 100000000,
      DeviceProfile: {
        Name: 'Driftfin Browser',
        MaxStreamingBitrate: 100000000,
        MaxStaticBitrate: 100000000,
        DirectPlayProfiles: [{ Type: 'Video', Container: 'mp4,m4v,webm', VideoCodec: 'h264,vp9,av1', AudioCodec: 'aac,mp3,opus' }],
        TranscodingProfiles: [{ Type: 'Video', Container: 'ts', Protocol: 'hls', VideoCodec: 'h264', AudioCodec: 'aac,mp3', Context: 'Streaming', MaxAudioChannels: '6', BreakOnNonKeyFrames: true }],
        SubtitleProfiles: ['srt', 'subrip', 'vtt', 'webvtt', 'ass', 'ssa'].map((f) => ({ Format: f, Method: 'External' })),
      },
    };
    if (opts.audioStreamIndex != null) infoBody.AudioStreamIndex = opts.audioStreamIndex;
    if (opts.mediaSourceId) infoBody.MediaSourceId = opts.mediaSourceId;
    const info = await jf(`Items/${id}/PlaybackInfo?userId=${uid}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(infoBody),
    });
    const source = (info.MediaSources || [])[0];
    if (!source) throw new Error('Jellyfin returned no media source for this item.');
    state.mediaSourceId = source.Id;
    state.playSessionId = info.PlaySessionId || null;
    const audioStreams = (source.MediaStreams || []).filter((s) => s.Type === 'Audio');
    const subStreams = (source.MediaStreams || []).filter((s) => s.Type === 'Subtitle');
    const audioSel = $('audioSelect');
    audioSel.innerHTML = audioStreams.map((s) => `<option value="${s.Index}" data-lang="${esc(s.Language || '')}">${esc(s.DisplayTitle || s.Language || ('Audio ' + s.Index))}</option>`).join('')
      || '<option value="">Default audio</option>';
    const subSel = $('subtitleSelect');
    // Text subtitles only, TV TEXT_SUBTITLE_FORMATS. Track order matches the select order.
    const textSubs = subStreams.filter((s) => ['srt', 'subrip', 'vtt', 'webvtt', 'ass', 'ssa'].includes(String(s.Codec || '').toLowerCase()));
    state.subtitleTracks = textSubs;
    subSel.innerHTML = '<option value="">Off</option>' + textSubs.map((s, i) => `<option value="${i}">${esc(s.DisplayTitle || s.Language || ('Subtitle ' + s.Index))}</option>`).join('');
    video.innerHTML = '';
    textSubs.forEach((s) => {
      const track = document.createElement('track');
      track.kind = 'subtitles';
      track.label = s.DisplayTitle || s.Language || 'Subtitle';
      track.srclang = (s.Language || 'en').slice(0, 2);
      // External sidecars use the server's DeliveryUrl through the proxy; embedded text
      // tracks use Jellyfin's standard subtitle stream endpoint. No token is in the page.
      if (s.DeliveryUrl) {
        track.src = '/api/jellyfin/' + String(s.DeliveryUrl).replace(/^\//, '');
      } else {
        track.src = `/api/jellyfin/Videos/${id}/${s.Index}/Subtitles/${s.Index}/Stream.vtt?MediaSourceId=${source.Id}`;
      }
      video.appendChild(track);
    });
    // Default audio: remembered preference (English by default, like the TV apps), else first stream.
    const prefLang = String((state.settings && state.settings.audioLanguage) || 'eng').toLowerCase();
    const prefStream = audioStreams.find((st) => String(st.Language || '').toLowerCase().slice(0, 3) === prefLang.slice(0, 3));
    const chosenAudio = opts.audioStreamIndex != null ? opts.audioStreamIndex : ((prefStream || audioStreams[0]) ? (prefStream || audioStreams[0]).Index : null);
    if (chosenAudio != null) audioSel.value = String(chosenAudio);
    const audioParam = chosenAudio != null ? `&AudioStreamIndex=${chosenAudio}` : '';
    // TV PlaybackNegotiator.choose: direct play/stream first, else a transcode (HLS here).
    if (source.SupportsDirectPlay === true || source.SupportsDirectStream === true) {
      state.playMethod = 'DirectPlay';
      video.src = `/api/direct/${id}?MediaSourceId=${source.Id}${audioParam}`;
      note.textContent = 'Playing via direct stream through the server proxy.';
    } else if (source.SupportsTranscoding === true) {
      state.playMethod = 'Transcode';
      // TV PlaybackNegotiator.choose: the transcode URL is the server's own TranscodingUrl. Its
      // params (VideoCodec=h264, AudioCodec=aac, ...) are what make the server transcode to a
      // browser-playable format; a bare master.m3u8 request lets Jellyfin fall back to defaults
      // (it picked HEVC for an HEVC source), which Chrome cannot decode, leaving a stuck 0:00 player.
      // Credentials are stripped - the token stays server-side in the proxy.
      const tq = new URLSearchParams();
      const tRaw = String(source.TranscodingUrl || '');
      const qIdx = tRaw.indexOf('?');
      if (qIdx >= 0) {
        for (const [k, v] of new URLSearchParams(tRaw.slice(qIdx + 1))) {
          if (k.toLowerCase() === 'api_key' || k.toLowerCase() === 'apikey') continue;
          tq.set(k, v);
        }
      }
      tq.set('PlaySessionId', state.playSessionId || '');
      if (chosenAudio != null) tq.set('AudioStreamIndex', String(chosenAudio));
      const tFile = (qIdx >= 0 ? tRaw.slice(0, qIdx) : tRaw).split('/').filter(Boolean).pop() || 'master.m3u8';
      const hlsUrl = `/api/hls/${id}/${source.Id}/${tFile}?${tq.toString()}`;
      if (video.canPlayType('application/vnd.apple.mpegurl')) {
        video.src = hlsUrl;
      } else if (window.Hls && window.Hls.isSupported()) {
        state.hls = new window.Hls();
        state.hls.loadSource(hlsUrl);
        state.hls.attachMedia(video);
      } else {
        throw new Error('This browser cannot play HLS (no native support and hls.js failed to load).');
      }
      note.textContent = 'Playing via HLS transcode through the server proxy.';
    } else {
      throw new Error('This file cannot be played or converted for this browser.');
    }
    $('playerBar').hidden = false;
    pbSync();
    applyPersistedSubtitle();
    video.playbackRate = parseFloat($('speedSelect').value || '1');
    if (startTicks > 0) {
      video.addEventListener('loadedmetadata', function once() {
        video.removeEventListener('loadedmetadata', once);
        video.currentTime = startTicks / 10000000;
      });
    }
    video.play().catch(() => {});
    // Skip Intro: only where Jellyfin reports an intro segment (TV MediaSegments, 10.10+).
    state.introSegments = [];
    if (state.settings && state.settings.skipIntro) {
      try {
        const segs = await jf(`MediaSegments/${id}?includeSegmentTypes=Intro`);
        state.introSegments = (segs.Items || []).filter((s) => String(s.Type || '').toLowerCase() === 'intro');
      } catch {}
    }
    // Next episode for autoplay: next in server episode order, from a full episode list.
    state.nextEpisode = null;
    try {
      if (item.Type === 'Episode' && item.SeriesId) {
        const eps = await jf(`Shows/${item.SeriesId}/Episodes?userId=${uid}&limit=2000`);
        state.nextEpisode = pickNextEpisode(eps.Items || [], id);
      }
    } catch {}
    startProgress(id, source.Id);
  } catch (e) {
    note.textContent = 'Playback failed: ' + e.message;
  }
}
function activeIntroSegment() {
  const posMs = $('player').currentTime * 1000;
  return state.introSegments.find((s) => s.EndTicks > s.StartTicks && posMs >= s.StartTicks / 10000 && posMs < s.EndTicks / 10000 - 1000);
}
function startProgress(itemId, mediaSourceId) {
  stopProgress();
  state.playingItemId = itemId;
  const video = $('player');
  if (state.playFailed !== true) state.playFailed = !!(video && video.error);
  const report = (kind) => {
    const ticks = Math.round(video.currentTime * 10000000);
    const endpoint = kind === 'start' ? 'Sessions/Playing' : kind === 'stopped' ? 'Sessions/Playing/Stopped' : 'Sessions/Playing/Progress';
    const body = { ItemId: itemId, MediaSourceId: mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, IsPaused: video.paused, PlayMethod: state.playMethod };
    if (kind === 'stopped') body.Failed = state.playFailed === true;
    jf(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).catch(() => {});
  };
  report('start');
  if (state.playFailed) {
    // Media errored before the session report: close it immediately as failed.
    report('stopped');
    state.playingItemId = null;
    state.playFailed = false;
    const note = $('playerNote');
    if (note) note.textContent = 'Playback failed: this video could not be decoded by the browser.';
    return;
  }
  state.progressTimer = setInterval(() => report('progress'), 10000);
  video.onpause = () => report('progress');
  video.onended = () => {
    report('stopped');
    state.playingItemId = null;
    if (state.settings && state.settings.autoplay && state.nextEpisode) {
      const box = $('upNext');
      box.hidden = false;
      let left = 5;
      box.innerHTML = `<strong>Up next:</strong> ${esc(state.nextEpisode.Name)} in <span id="upNextCount">${left}</span>s <button type="button" id="upNextPlay">Play now</button> <button type="button" id="upNextCancel">Cancel</button>`;
      $('upNextPlay').addEventListener('click', () => { clearInterval(state.upNextTimer); location.replace('#/player?id=' + encodeURIComponent(state.nextEpisode.Id)); });
      $('upNextCancel').addEventListener('click', () => { clearInterval(state.upNextTimer); box.hidden = true; });
      state.upNextTimer = setInterval(() => {
        left -= 1;
        const c = $('upNextCount'); if (c) c.textContent = left;
        if (left <= 0) { clearInterval(state.upNextTimer); location.replace('#/player?id=' + encodeURIComponent(state.nextEpisode.Id)); }
      }, 1000);
    }
  };
}
$('player').addEventListener('error', () => {
  const video = $('player');
  if (!video.error) return; // emptied src on teardown also fires error; ignore those
  if ($('view-player').hidden) return;
  // The error can fire before startProgress opens the session; record it either way.
  state.playFailed = true;
  if (state.playingItemId != null) stopPlayback(true);
  const note = $('playerNote');
  if (note) note.textContent = 'Playback failed: this video could not be decoded by the browser.';
});

// Leaving the player (nav, new title, sign-out) must actually end playback:
// pause, report Stopped once, tear down hls.js, and release the element.
function stopPlayback(reportStopped) {
  const video = $('player');
  if (reportStopped && state.playingItemId != null) {
    const ticks = Math.round((video.currentTime || 0) * 10000000);
    jf('Sessions/Playing/Stopped', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ItemId: state.playingItemId, MediaSourceId: state.mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, Failed: state.playFailed === true }),
    }).catch(() => {});
  }
  state.playFailed = false;
  stopProgress();
  const pb = $('playerBar'); if (pb) pb.hidden = true;
  const pov = $('playerWrap'); if (pov) pov.classList.remove('landscape-fill');
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  if (state.hls) { try { state.hls.destroy(); } catch {} state.hls = null; }
  if (video) { video.pause(); video.removeAttribute('src'); video.load(); }
  state.playingItemId = null;
}
function applyPersistedSubtitle() {
  const pref = state.settings && state.settings.subtitleLanguage;
  if (!pref || !state.subtitleTracks.length) return;
  const i = state.subtitleTracks.findIndex((st) => String(st.Language || '').toLowerCase().slice(0, 3) === String(pref).toLowerCase().slice(0, 3));
  if (i < 0) return;
  $('subtitleSelect').value = String(i);
  const tracks = $('player').textTracks;
  for (let t = 0; t < tracks.length; t++) tracks[t].mode = 'disabled';
  if (tracks[i]) tracks[i].mode = 'showing';
}
function saveSettingPatch(patch) {
  state.settings = { ...(state.settings || {}), ...patch };
  api('/api/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(patch) })
    .then((merged) => { state.settings = merged; })
    .catch(() => {});
}
function clearSearch() {
  $('searchInput').value = '';
  $('searchResults').innerHTML = '';
}
function stopProgress() {
  if (state.progressTimer) clearInterval(state.progressTimer);
  if (state.upNextTimer) clearInterval(state.upNextTimer);
  state.progressTimer = null; state.upNextTimer = null;
  const video = $('player');
  if (video) { video.onpause = null; video.onended = null; }
}

// ---------- Routing / wiring ----------
function route() {
  const hash = location.hash || '#/home';
  const [pathPart, queryPart] = hash.slice(2).split('?');
  const params = new URLSearchParams(queryPart || '');
  if (pathPart !== 'player') stopPlayback(true);
  if (pathPart !== 'home') stopHeroRotation();
  if (pathPart !== 'search') clearSearch();
  if (pathPart === 'home') loadHome();
  else if (pathPart === 'shows') loadLibrary('shows', true);
  else if (pathPart === 'movies') loadLibrary('movies', true);
  else if (pathPart === 'mystuff') loadMyStuff();
  else if (pathPart === 'search') { showView('search'); if (params.get('q')) { $('searchInput').value = params.get('q'); loadSearch(params.get('q')); } else loadSearchRecommendations(); }
  else if (pathPart === 'settings') { ensureSignedIn().then((ok) => { if (ok) { showView('settings'); const f = $('settingsForm'); for (const key of Object.keys(state.settings || {})) { const el = f.elements[key]; if (!el) continue; if (el.type === 'checkbox') el.checked = Boolean(state.settings[key]); else el.value = state.settings[key]; } } }); }
  else if (pathPart === 'details' && params.get('id')) goDetails(params.get('id'));
  else if (pathPart === 'actor' && params.get('id')) goActor(params.get('id'));
  else if (pathPart === 'player' && params.get('id')) goPlayer(params.get('id'), { fromStart: params.get('start') === '1' });
  else if (pathPart === 'signin') showView('signin');
  else loadHome();
}
window.addEventListener('hashchange', route);
async function finishSignIn(userId, username) {
  state.userId = userId; state.username = username;
  state.settings = await api('/api/settings');
  applyPosterSize();
  $('userLabel').textContent = state.username || '';
  $('signoutBtn').hidden = false;
  location.hash = '#/home';
}

// Quick Connect: the server shows a short code, the user approves it in another
// Jellyfin app, and the server-side secret completes sign-in (TV LoginScreen flow).
let qcPollTimer = null;
let qcPendingId = null;
function stopQuickConnect() {
  if (qcPollTimer) { clearInterval(qcPollTimer); qcPollTimer = null; }
  if (qcPendingId) {
    fetch('/api/quickconnect/cancel', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id: qcPendingId }) }).catch(() => {});
    qcPendingId = null;
  }
  $('quickConnectPanel').hidden = true;
  $('quickConnectBtn').hidden = false;
}
$('quickConnectBtn').addEventListener('click', async () => {
  $('signinError').textContent = '';
  try {
    const res = await fetch('/api/quickconnect/start', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ server: $('serverInput').value.trim() }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || !data.id) {
      $('signinError').textContent = data.error === 'quickconnect_unavailable'
        ? "Quick Connect isn't enabled on this server. Sign in with your password instead."
        : 'Could not start Quick Connect.' + (data.error ? ` (${data.error})` : '');
      return;
    }
    qcPendingId = data.id;
    $('quickConnectCode').textContent = data.code;
    $('quickConnectPanel').hidden = false;
    $('quickConnectBtn').hidden = true;
    const deadline = Date.now() + (data.expiresInSeconds || 300) * 1000;
    if (qcPollTimer) clearInterval(qcPollTimer);
    qcPollTimer = setInterval(async () => {
      if (Date.now() > deadline) {
        stopQuickConnect();
        $('signinError').textContent = "Quick Connect wasn't approved in time. Try again or use your password.";
        return;
      }
      try {
        const st = await (await fetch('/api/quickconnect/status?id=' + encodeURIComponent(qcPendingId))).json();
        if (st.status === 'authorized') {
          const uid = st.userId, name = st.username;
          stopQuickConnect();
          await finishSignIn(uid, name);
        } else if (st.status === 'expired' || st.status === 'failed') {
          stopQuickConnect();
          $('signinError').textContent = st.status === 'expired'
            ? "Quick Connect wasn't approved in time. Try again or use your password."
            : 'Quick Connect sign-in failed. Try again or use your password.';
        }
      } catch {}
    }, 2000);
  } catch (err) { $('signinError').textContent = err.message; }
});
$('quickConnectCancel').addEventListener('click', () => { stopQuickConnect(); });

$('signinForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('signinError').textContent = '';
  try {
    const res = await fetch('/api/auth/login', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ server: $('serverInput').value.trim(), username: $('usernameInput').value.trim(), password: $('passwordInput').value }),
    });
    if (!res.ok) {
      let detail = '';
      try { const errData = await res.json(); if (errData.error) detail = ` (${errData.error}${errData.detail ? ': ' + errData.detail : ''})`; } catch {}
      throw new Error('Sign-in was rejected by Jellyfin, or the server could not be reached. Check the server URL, username, and password.' + detail);
    }
    const data = await res.json();
    await finishSignIn(data.userId, data.username);
  } catch (err) { $('signinError').textContent = err.message; }
});
async function signOutToSignIn() {
  stopPlayback(true);
  stopQuickConnect();
  stopHomeAutoRefresh();
  stopHeroRotation();
  await fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
  // Clear every per-user remnant so the next account never sees a stale flash.
  state.userId = null; state.username = null; state.settings = null;
  state.heroList = []; state.heroIndex = 0; state.heroItem = null;
  state.views = []; state.watchlistIds = [];
  state.currentItem = null; state.allEpisodes = []; state.seasons = []; state.playTarget = null;
  document.documentElement.style.setProperty('--card-w', POSTER_WIDTHS.Medium);
  $('signoutBtn').hidden = true; $('userLabel').textContent = '';
  location.hash = '#/signin';
}
$('signoutBtn').addEventListener('click', signOutToSignIn);
$('switchUserBtn').addEventListener('click', signOutToSignIn);
$('searchForm').addEventListener('submit', (e) => { e.preventDefault(); location.hash = '#/search?q=' + encodeURIComponent($('searchInput').value.trim()); });
$('kidsFilter').addEventListener('change', () => loadLibrary(libraryKind));
$('librarySort').addEventListener('change', () => { saveSettingPatch({ librarySort: $('librarySort').value }); loadLibrary(libraryKind); });
$('librarySortOrder').addEventListener('change', () => { saveSettingPatch({ librarySortOrder: $('librarySortOrder').value }); loadLibrary(libraryKind); });
$('watchedFilter').addEventListener('change', () => loadLibrary(libraryKind));
$('settingsForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = $('settingsForm'); const payload = {};
  for (const key of Object.keys(state.settings || {})) { const el = f.elements[key]; if (!el) continue; payload[key] = el.type === 'checkbox' ? el.checked : el.value; }
  state.settings = await api('/api/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
  applyPosterSize();
  $('settingsSaved').textContent = 'Saved.';
});
$('player').addEventListener('timeupdate', () => {
  const btn = $('skipIntroBtn');
  const seg = state.settings && state.settings.skipIntro ? activeIntroSegment() : null;
  btn.hidden = !seg;
});
$('skipIntroBtn').addEventListener('click', () => {
  const seg = activeIntroSegment();
  if (seg) $('player').currentTime = seg.EndTicks / 10000;
});
$('audioSelect').addEventListener('change', async () => {
  const video = $('player');
  const pos = video.currentTime;
  const idx = parseInt($('audioSelect').value, 10);
  const opt = $('audioSelect').selectedOptions[0];
  const lang = opt && opt.dataset.lang;
  if (lang) saveSettingPatch({ audioLanguage: lang });
  const id = new URLSearchParams(location.hash.split('?')[1] || '').get('id');
  if (id) { await goPlayer(id, { audioStreamIndex: idx, mediaSourceId: state.mediaSourceId, preservePosition: pos }); }
});
$('subtitleSelect').addEventListener('change', () => {
  const idx = $('subtitleSelect').value;
  const tracks = $('player').textTracks;
  for (let i = 0; i < tracks.length; i++) tracks[i].mode = 'disabled';
  if (idx !== '') {
    const track = tracks[parseInt(idx, 10)];
    if (track) track.mode = 'showing';
    const st = state.subtitleTracks[parseInt(idx, 10)];
    saveSettingPatch({ subtitleLanguage: (st && st.Language) || null });
  } else {
    saveSettingPatch({ subtitleLanguage: null });
  }
});
$('playerBack').addEventListener('click', () => { if (history.length > 1) history.back(); else location.hash = '#/home'; });
// Fullscreen the wrapper (not the bare video) so Back and Skip Intro stay usable.
// Custom player bar: browser-native controls render as an unthemed white strip on
// mobile browsers; these keep pause/seek/mute in the app's accent style. Fullscreen
// stays on the existing overlay button.
const PB_PLAY = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" focusable="false"><path d="M8 5v14l11-7z"/></svg>';
const PB_PAUSE = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" focusable="false"><path d="M6 5h4v14H6zM14 5h4v14h-4z"/></svg>';
const PB_VOL = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" focusable="false"><path d="M3 9v6h4l5 5V4L7 9H3z"/><path d="M16 8a5 5 0 0 1 0 8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
const PB_MUTED = '<svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true" focusable="false"><path d="M3 9v6h4l5 5V4L7 9H3z"/><path d="M16 9l6 6M22 9l-6 6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>';
function pbFmt(sec) {
  if (!isFinite(sec) || sec < 0) sec = 0;
  const t = Math.floor(sec);
  const h = Math.floor(t / 3600), m = Math.floor((t % 3600) / 60), ss = String(t % 60).padStart(2, '0');
  return h > 0 ? h + ':' + String(m).padStart(2, '0') + ':' + ss : m + ':' + ss;
}
function pbSync() {
  const v = $('player');
  if (!v || $('playerBar').hidden) return;
  const d = v.duration;
  $('pbPlay').innerHTML = v.paused ? PB_PLAY : PB_PAUSE;
  $('pbPlay').setAttribute('aria-label', v.paused ? 'Play' : 'Pause');
  $('pbTime').textContent = pbFmt(v.currentTime) + ' / ' + pbFmt(d);
  if (isFinite(d) && d > 0 && !$('pbSeek').matches(':active')) $('pbSeek').value = String(Math.round((v.currentTime / d) * 1000));
  $('pbSeek').style.setProperty('--fill', (parseInt($('pbSeek').value, 10) / 10) + '%');
  const muted = v.muted || v.volume === 0;
  $('pbMute').innerHTML = muted ? PB_MUTED : PB_VOL;
  $('pbMute').setAttribute('aria-label', muted ? 'Unmute' : 'Mute');
}
['play', 'pause', 'timeupdate', 'durationchange', 'volumechange', 'loadedmetadata'].forEach((ev) => $('player').addEventListener(ev, pbSync));
$('pbPlay').addEventListener('click', () => { const v = $('player'); if (v.paused) v.play().catch(() => {}); else v.pause(); });
$('player').addEventListener('click', () => { const v = $('player'); if (v.paused) v.play().catch(() => {}); else v.pause(); });
$('pbSeek').addEventListener('input', () => {
  const d = $('player').duration;
  if (isFinite(d) && d > 0) $('pbTime').textContent = pbFmt((parseInt($('pbSeek').value, 10) / 1000) * d) + ' / ' + pbFmt(d);
  $('pbSeek').style.setProperty('--fill', (parseInt($('pbSeek').value, 10) / 10) + '%');
});
$('pbSeek').addEventListener('change', () => {
  const v = $('player'); const d = v.duration;
  if (isFinite(d) && d > 0) v.currentTime = (parseInt($('pbSeek').value, 10) / 1000) * d;
});
$('pbMute').addEventListener('click', () => { $('player').muted = !$('player').muted; });

// Rotate-to-fullscreen: on mobile, turning to landscape during playback fills the screen.
// Real requestFullscreen() needs a user gesture on most mobile browsers, so when it is
// rejected or unavailable, fall back to a fixed-viewport "landscape-fill" presentation.
function syncLandscapeFill() {
  const ov = $('playerWrap');
  const view = $('view-player');
  if (!ov || !view || view.hidden) return;
  // Rotate-to-fill is a touch-device behavior; never reshape desktop playback.
  if (!window.matchMedia('(pointer: coarse)').matches) return;
  const wrap = document.querySelector('.videowrap');
  const v = $('player');
  const landscape = window.innerWidth > window.innerHeight;
  const playing = v && !v.paused && !v.ended;
  if (landscape && playing) {
    if (document.fullscreenElement) { ov.classList.remove('landscape-fill'); return; }
    if (wrap && wrap.requestFullscreen) {
      wrap.requestFullscreen()
        .then(() => ov.classList.remove('landscape-fill'))
        .catch(() => ov.classList.add('landscape-fill'));
    } else {
      ov.classList.add('landscape-fill');
    }
  } else {
    ov.classList.remove('landscape-fill');
    if (!landscape && document.fullscreenElement) document.exitFullscreen().catch(() => {});
  }
}
['play', 'pause', 'ended'].forEach((ev) => $('player').addEventListener(ev, syncLandscapeFill));
window.addEventListener('orientationchange', () => setTimeout(syncLandscapeFill, 250));
window.addEventListener('resize', syncLandscapeFill);
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement) setTimeout(syncLandscapeFill, 60); });

$('fsBtn').addEventListener('click', () => {
  const wrap = document.querySelector('.videowrap');
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  else if (wrap && wrap.requestFullscreen) wrap.requestFullscreen().catch(() => {});
});
$('speedSelect').addEventListener('change', () => { $('player').playbackRate = parseFloat($('speedSelect').value); });

function reportStoppedOnUnload() {
  if (state.playingItemId == null) return;
  const ticks = Math.round((($('player') || {}).currentTime || 0) * 10000000);
  fetch('/api/jellyfin/Sessions/Playing/Stopped', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, keepalive: true,
    body: JSON.stringify({ ItemId: state.playingItemId, MediaSourceId: state.mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, Failed: state.playFailed === true }),
  }).catch(() => {});
}
window.addEventListener('beforeunload', reportStoppedOnUnload);
window.addEventListener('pagehide', reportStoppedOnUnload);

(async () => {
  try {
    state.config = await api('/api/config');
    if (state.config && state.config.jellyfinUrl) $('serverInput').value = state.config.jellyfinUrl;
    if (state.config && state.config.serverLocked) {
      $('serverInput').readOnly = true;
      $('serverInput').setAttribute('aria-readonly', 'true');
      $('signinForm').classList.add('locked');
      $('serverLockedNote').hidden = false;
    }
  } catch {}
  route();
})();
