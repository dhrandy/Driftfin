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
    if (hasLogo) return `<img src="${logoUrlFor(item)}" alt="" style="max-height:90px;max-width:60%" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">`;
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
function metaLine(item) {
  const parts = [];
  if (item.OfficialRating) parts.push(item.OfficialRating);
  if (item.Type === 'Series' && item.ChildCount != null) parts.push(item.ChildCount === 1 ? '1 Season' : item.ChildCount + ' Seasons');
  const air = formatAirDate(item.PremiereDate);
  if (item.Type === 'Series' && air) parts.push(air);
  else if (item.ProductionYear) parts.push(item.ProductionYear);
  if (item.CommunityRating) parts.push(Math.round(item.CommunityRating * 10) + '%');
  return parts.map((p) => `<span>${esc(p)}</span>`).join(' ');
}
const img = (item, type, maxWidth) => {
  if (!item || !item.Id) return '';
  return `/api/jellyfin/Items/${item.Id}/Images/${type}?maxWidth=${maxWidth || 400}&quality=90`;
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
  const pct = item.UserData && item.UserData.PlayedPercentage ? Math.round(item.UserData.PlayedPercentage) : 0;
  const watched = item.UserData && item.UserData.Played === true;
  const title = item.SeriesName ? `${item.SeriesName} - ${item.Name}` : item.Name;
  const label = episodeLabel(item) ? episodeLabel(item) + ' ' : '';
  return `<button class="card${wide ? ' wide' : ''}" data-id="${esc(item.Id)}" data-type="${esc(item.Type || '')}">
    <img src="${wide ? cardImageWide(item) : cardImage(item)}" alt="" loading="lazy" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
    ${watched ? '<span class="watched">&#10003;</span>' : ''}
    <div class="card-title">${esc(label + title)}</div>
    ${pct ? `<div class="progress"><div style="width:${pct}%"></div></div>` : ''}
  </button>`;
}
function bindCards(container) {
  container.querySelectorAll('.card').forEach((el) => {
    el.addEventListener('click', () => goDetails(el.dataset.id));
  });
}
function rowHtml(title, items, wide) {
  if (!items || !items.length) return '';
  return `<section class="row"><h2>${esc(title)}</h2><div class="row-scroll">${items.map((i) => cardHtml(i, wide)).join('')}</div></section>`;
}
function showView(name, navOverride) {
  document.querySelectorAll('.view').forEach((v) => { v.hidden = true; });
  const el = $('view-' + name);
  if (el) el.hidden = false;
  const navName = navOverride || name;
  document.querySelectorAll('[data-nav]').forEach((a) => {
    a.classList.toggle('active', a.dataset.nav === navName);
  });
  window.scrollTo(0, 0);
}
async function ensureSignedIn() {
  if (state.userId) return true;
  try {
    const me = await api('/api/auth/me');
    state.userId = me.Id; state.username = me.Name;
    state.settings = await api('/api/settings');
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
    renderHero(state.heroItem);
    startHeroRotation();
    let html = '';
    html += rowHtml('Continue Watching', resume, true);
    html += rowHtml('Next Up', nextUp.Items || [], true);
    html += rowHtml('Recently Added Movies', latestMovies.Items || latestMovies || []);
    html += rowHtml('Recently Added Episodes', latestEpisodes.Items || latestEpisodes || [], true);
    html += rowHtml('Favorites', favorites.Items || []);
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
  } catch (e) {
    wrap.innerHTML = `<p class="error">Could not load Home from Jellyfin: ${esc(e.message)}</p>`;
  }
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
function renderHero(item) {
  const hero = $('hero');
  if (!item) { hero.innerHTML = '<div class="hero-empty">No unwatched movie found for the home banner.</div>'; return; }
  hero.innerHTML = `
    <img class="hero-backdrop" src="${img(item, 'Backdrop', 1600)}" alt="" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
    <div class="hero-body">
      ${titleHtml(item)}
      <div class="hero-meta">${metaLine(item)} ${formatBadges(item)}</div>
      <p>${esc((item.Overview || '').slice(0, 300))}</p>
      <button type="button" id="heroPlay">${isResumeable(item) ? 'Resume' : 'Play'}</button>
      <button type="button" id="heroInfo">More Info</button>
    </div>`;
  $('heroPlay').addEventListener('click', () => goPlayer(item.Id));
  $('heroInfo').addEventListener('click', () => goDetails(item.Id));
}

// ---------- Library (Shows / Movies) ----------
let libraryKind = 'shows';
async function loadLibrary(kind) {
  libraryKind = kind;
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
    let items = [];
    for (const view of viewsToUse) {
      const res = await jf(`Items?userId=${uid}&ParentId=${view.Id}&IncludeItemTypes=${type}&Recursive=true&SortBy=${encodeURIComponent($('librarySort').value)}&SortOrder=${encodeURIComponent($('librarySortOrder').value)}&limit=200&fields=Overview,Genres,MediaStreams`);
      items = items.concat(res.Items || []);
    }
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No items returned for this filter.</p>';
    bindCards(grid);
  } catch (e) {
    grid.innerHTML = `<p class="error">Could not load library from Jellyfin: ${esc(e.message)}</p>`;
  }
}

// ---------- Details (full season switching) ----------
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
    if (item.Type === 'Series') {
      // TV DetailsScreen: seasons via Items (parentId + IncludeItemTypes=Season, SortName),
      // all episodes in one call (limit 2000, fields Overview,PremiereDate,People), filtered client-side.
      const [seasonsRes, episodesRes] = await Promise.all([
        jf(`Items?userId=${uid}&parentId=${item.Id}&includeItemTypes=Season&sortBy=SortName`),
        jf(`Shows/${item.Id}/Episodes?userId=${uid}&fields=Overview,PremiereDate,People&limit=2000`),
      ]);
      state.seasons = seasonsRes.Items || [];
      state.allEpisodes = episodesRes.Items || [];
      state.playTarget = pickEpisodeTarget(state.allEpisodes) || item;
      state.selectedSeasonId = keepSeasonId || (state.playTarget && state.playTarget.SeasonId) || (state.seasons[0] && state.seasons[0].Id) || null;
    }
    renderDetails();
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
  const target = state.playTarget || item;
  const isFav = item.UserData && item.UserData.IsFavorite;
  // Watched button acts on the play-target episode for a series, never the series id
  // (marking the series id would mark every episode watched on the server).
  const watchedTarget = isSeries && target.Type === 'Episode' ? target : item;
  const watchedPlayed = watchedTarget.UserData && watchedTarget.UserData.Played;
  let seasonsHtml = '';
  if (isSeries) {
    const shown = state.allEpisodes.filter((ep) => ep.SeasonId === state.selectedSeasonId);
    const list = shown.length ? shown : state.allEpisodes;
    const chips = state.seasons.map((s) => `<button type="button" class="season-chip${s.Id === state.selectedSeasonId ? ' active' : ''}" data-season="${esc(s.Id)}">${esc(s.Name)}</button>`).join(' ');
    const episodesHtml = list.map((ep) => {
      const pct = ep.UserData && ep.UserData.PlayedPercentage ? Math.round(ep.UserData.PlayedPercentage) : 0;
      const air = formatAirDate(ep.PremiereDate);
      return `<div class="episode" data-id="${esc(ep.Id)}">
        <div class="episode-main">
          <span><strong>${esc(episodeLabel(ep) || '')}</strong> ${esc(ep.Name)}</span>
          <span class="muted">${air ? esc(air) + ' · ' : ''}${runtimeLabel(ep)}${ep.UserData && ep.UserData.Played ? ' · ✓ Watched' : ''}</span>
          ${pct ? `<div class="progress"><div style="width:${pct}%"></div></div>` : ''}
        </div>
        <div class="episode-actions">
          <button type="button" class="episode-play" data-id="${esc(ep.Id)}">${isResumeable(ep) ? 'Resume' : 'Play'}</button>
          <button type="button" class="episode-watched" data-id="${esc(ep.Id)}">${ep.UserData && ep.UserData.Played ? 'Mark unwatched' : 'Mark watched'}</button>
        </div>
      </div>`;
    }).join('') || '<p class="muted">No episodes returned for this season.</p>';
    seasonsHtml = `<h2>Seasons and Episodes</h2><div class="season-chips">${chips}</div>${episodesHtml}`;
  }
  // Cast follows the play-target episode on series (TV rule), else the item's own People.
  const cast = (isSeries && target && target.Type === 'Episode' && (target.People || []).length ? target.People : item.People) || [];
  const castHtml = cast.length
    ? `<h2>Cast</h2><div class="cast-row">${cast.map((p) => `
        <button type="button" class="cast-person" data-person="${esc(p.Id)}">
          <img src="/api/jellyfin/Items/${esc(p.Id)}/Images/Primary?maxWidth=220&quality=90" alt="" loading="lazy" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
          <div class="cast-name">${esc(p.Name || '')}</div>
          ${p.Role ? `<div class="cast-role">${esc(p.Role)}</div>` : ''}
        </button>`).join('')}</div>`
    : '';
  el.innerHTML = `
    <img class="details-backdrop" src="${img(item, 'Backdrop', 1600)}" alt="" onerror="this.src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='">
    ${titleHtml(item)}
    <div class="hero-meta">${metaLine(item)} ${formatBadges(item)}</div>
    <p>${esc(item.Overview || '')}</p>
    <button type="button" id="detailPlay">${playLabel(target)}</button>
    ${isResumeable(target) ? `<button type="button" id="detailFromStart">Play from start</button>` : ''}
    <button type="button" id="detailFav">${isFav ? 'Remove from My List' : 'Add to My List'}</button>
    <button type="button" id="detailWatched">${watchedPlayed ? 'Mark unwatched' : 'Mark watched'}${isSeries && target.Type === 'Episode' ? ' (' + esc(episodeLabel(target) || target.Name) + ')' : ''}</button>
    ${castHtml}
    ${seasonsHtml}`;
  el.querySelectorAll('.cast-person').forEach((btn) => btn.addEventListener('click', () => { location.hash = '#/actor?id=' + encodeURIComponent(btn.dataset.person); }));
  $('detailPlay').addEventListener('click', () => goPlayer(target.Id));
  const fromStart = $('detailFromStart');
  if (fromStart) fromStart.addEventListener('click', () => goPlayer(target.Id, { fromStart: true }));
  $('detailFav').addEventListener('click', async () => {
    const method = isFav ? 'DELETE' : 'POST';
    const updated = await api(`/api/jellyfin/UserFavoriteItems/${item.Id}?userId=${uid}`, { method });
    if (updated) state.currentItem.UserData = updated;
    renderDetails();
  });
  $('detailWatched').addEventListener('click', async () => {
    await toggleWatched(watchedTarget);
  });
  el.querySelectorAll('.season-chip').forEach((btn) => btn.addEventListener('click', () => {
    // Season switching only changes the list; Play/Resume keeps pointing at the series target.
    state.selectedSeasonId = btn.dataset.season;
    renderDetails();
  }));
  el.querySelectorAll('.episode-play').forEach((btn) => btn.addEventListener('click', (e) => { e.stopPropagation(); goPlayer(btn.dataset.id); }));
  el.querySelectorAll('.episode-watched').forEach((btn) => btn.addEventListener('click', async (e) => {
    e.stopPropagation();
    const ep = state.allEpisodes.find((x) => x.Id === btn.dataset.id);
    if (ep) await toggleWatched(ep);
  }));
  el.querySelectorAll('.episode').forEach((node) => node.addEventListener('click', (e) => {
    if (e.target.closest('button')) return;
    goPlayer(node.dataset.id);
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
async function loadSearch(term) {
  if (!(await ensureSignedIn())) return;
  showView('search');
  const grid = $('searchResults');
  if (!term) { grid.innerHTML = ''; return; }
  grid.innerHTML = '<p class="muted">Searching Jellyfin…</p>';
  try {
    const res = await jf(`Items?userId=${state.userId}&SearchTerm=${encodeURIComponent(term)}&IncludeItemTypes=Movie,Series,Episode&Recursive=true&limit=30&fields=Overview`);
    const items = res.Items || [];
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No results from Jellyfin.</p>';
    bindCards(grid);
  } catch (e) { grid.innerHTML = `<p class="error">Search failed: ${esc(e.message)}</p>`; }
}
async function loadMyStuff() {
  if (!(await ensureSignedIn())) return;
  showView('mystuff');
  const grid = $('mystuffGrid');
  grid.innerHTML = '<p class="muted">Loading from Jellyfin…</p>';
  try {
    const res = await jf(`Items?userId=${state.userId}&Filters=IsFavorite&Recursive=true&IncludeItemTypes=Movie,Series&SortBy=DateCreated&SortOrder=Descending&limit=100&fields=Overview`);
    const items = res.Items || [];
    grid.innerHTML = items.length ? items.map((i) => cardHtml(i)).join('') : '<p class="muted">No favorites in Jellyfin yet.</p>';
    bindCards(grid);
  } catch (e) { grid.innerHTML = `<p class="error">Could not load My Stuff: ${esc(e.message)}</p>`; }
}

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
      const hlsUrl = `/api/hls/${id}/${source.Id}/master.m3u8?PlaySessionId=${state.playSessionId || ''}${audioParam}`;
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
  const report = (kind) => {
    const ticks = Math.round(video.currentTime * 10000000);
    const endpoint = kind === 'start' ? 'Sessions/Playing' : kind === 'stopped' ? 'Sessions/Playing/Stopped' : 'Sessions/Playing/Progress';
    const body = { ItemId: itemId, MediaSourceId: mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, IsPaused: video.paused, PlayMethod: state.playMethod };
    if (kind === 'stopped') body.Failed = false;
    jf(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).catch(() => {});
  };
  report('start');
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
      $('upNextPlay').addEventListener('click', () => { clearInterval(state.upNextTimer); goPlayer(state.nextEpisode.Id); });
      $('upNextCancel').addEventListener('click', () => { clearInterval(state.upNextTimer); box.hidden = true; });
      state.upNextTimer = setInterval(() => {
        left -= 1;
        const c = $('upNextCount'); if (c) c.textContent = left;
        if (left <= 0) { clearInterval(state.upNextTimer); goPlayer(state.nextEpisode.Id); }
      }, 1000);
    }
  };
}
// Leaving the player (nav, new title, sign-out) must actually end playback:
// pause, report Stopped once, tear down hls.js, and release the element.
function stopPlayback(reportStopped) {
  const video = $('player');
  if (reportStopped && state.playingItemId != null) {
    const ticks = Math.round((video.currentTime || 0) * 10000000);
    jf('Sessions/Playing/Stopped', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ItemId: state.playingItemId, MediaSourceId: state.mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, Failed: false }),
    }).catch(() => {});
  }
  stopProgress();
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
  else if (pathPart === 'shows') loadLibrary('shows');
  else if (pathPart === 'movies') loadLibrary('movies');
  else if (pathPart === 'mystuff') loadMyStuff();
  else if (pathPart === 'search') { showView('search'); if (params.get('q')) { $('searchInput').value = params.get('q'); loadSearch(params.get('q')); } }
  else if (pathPart === 'settings') { ensureSignedIn().then((ok) => { if (ok) { showView('settings'); const f = $('settingsForm'); for (const key of Object.keys(state.settings || {})) { if (f.elements[key]) f.elements[key].checked = Boolean(state.settings[key]); } } }); }
  else if (pathPart === 'details' && params.get('id')) goDetails(params.get('id'));
  else if (pathPart === 'actor' && params.get('id')) goActor(params.get('id'));
  else if (pathPart === 'player' && params.get('id')) goPlayer(params.get('id'));
  else if (pathPart === 'signin') showView('signin');
  else loadHome();
}
window.addEventListener('hashchange', route);
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
    state.userId = data.userId; state.username = data.username;
    state.settings = await api('/api/settings');
    $('userLabel').textContent = state.username || '';
    $('signoutBtn').hidden = false;
    location.hash = '#/home';
  } catch (err) { $('signinError').textContent = err.message; }
});
$('signoutBtn').addEventListener('click', async () => {
  stopPlayback(true);
  await fetch('/api/auth/logout', { method: 'POST' }).catch(() => {});
  state.userId = null; state.username = null;
  $('signoutBtn').hidden = true; $('userLabel').textContent = '';
  location.hash = '#/signin';
});
$('searchForm').addEventListener('submit', (e) => { e.preventDefault(); location.hash = '#/search?q=' + encodeURIComponent($('searchInput').value.trim()); });
$('kidsFilter').addEventListener('change', () => loadLibrary(libraryKind));
$('librarySort').addEventListener('change', () => { saveSettingPatch({ librarySort: $('librarySort').value }); loadLibrary(libraryKind); });
$('librarySortOrder').addEventListener('change', () => { saveSettingPatch({ librarySortOrder: $('librarySortOrder').value }); loadLibrary(libraryKind); });
$('settingsForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const f = $('settingsForm'); const payload = {};
  for (const key of Object.keys(state.settings || {})) { if (f.elements[key]) payload[key] = f.elements[key].checked; }
  state.settings = await api('/api/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
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
$('speedSelect').addEventListener('change', () => { $('player').playbackRate = parseFloat($('speedSelect').value); });

window.addEventListener('beforeunload', () => {
  if (state.playingItemId == null) return;
  const ticks = Math.round((($('player') || {}).currentTime || 0) * 10000000);
  fetch('/api/jellyfin/Sessions/Playing/Stopped', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, keepalive: true,
    body: JSON.stringify({ ItemId: state.playingItemId, MediaSourceId: state.mediaSourceId, PlaySessionId: state.playSessionId, PositionTicks: ticks, Failed: false }),
  }).catch(() => {});
});

(async () => {
  try { state.config = await api('/api/config'); if (state.config && state.config.jellyfinUrl) $('serverInput').value = state.config.jellyfinUrl; } catch {}
  route();
})();
