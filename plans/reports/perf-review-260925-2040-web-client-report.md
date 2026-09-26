# Perf review — web client (web/public), framework-agnostic subset of react-best-practices

Date: 2026-09-25. Report-only, no code changed.
Scope: app.js, index.html, style.css + styles/*.css, lib/** (hls vendored lib excluded; web/src only cited where it sets the impact).

## Ground truth used

- Real catalog from the running player (`GET /api/sets`): **619 KB uncompressed, 1154 sets** (829 movies, 163 eps in 6 shows, 162 lessons in 1 course). 991 have posters. Largest genre: Action = 348 films.
- Module graph from app.js (static imports): **59 modules, 350 KB unminified, longest chain 7 deep** (app → course-view → shelf-view → plate → set-badge → link → codec-support). Player subgraph alone: 35 modules / 201 KB.
- Server serves static files `cache-control: no-cache` with **no ETag/Last-Modified and no compression** (web/src/http/static-files.ts:73-80; grep finds no etag/gzip). So every load re-downloads all JS/CSS + catalog. Plain `node:http` (HTTP/1.1, 6 conns/host).
- plans/260925-1923-web-player-redesign/lighthouse-mobile.json: perf 0.73, **LCP 8.0 s**, LCP element = `main > header.home-intro > p` (text drawn by JS after the whole startup chain), render-blocking CSS est. 600 ms.
- Microbench (node 22 / V8, real catalog): JSON.parse 1.3 ms, groupLibrary 3.5 ms, homeShelves 0.3 ms, flatten all collections 0.06 ms, 3x nextAfter on 162-lesson course 0.1 ms.

Conclusion up front: the JS compute is cheap at this library size. What users feel is **network waterfalls** (startup, play, transcode) and **wasted full re-renders**.

## Findings, ranked by user impact

### 1. Startup API waterfall — 4 serial round trips before first paint [async-parallel, async-dependencies, async-defer-await] HIGH
app.js:755-766
```
await loadLink();                 // /api/player
await state.loadKids();           // /api/kids
await Promise.all([loadCatalog(), state.loadProfiles()]);  // /api/sets ∥ /api/profiles
... await state.useProfile(known) // /api/profiles/:id/state
```
- loadLink and loadKids gate nothing the catalog/profile fetches need: `applyCatalog` inside loadCatalog runs with no profile (unfiltered), and app.js:773 re-applies after profile selection anyway; colophon is re-rendered there too.
- State read depends only on the *validation* of the remembered id against /api/profiles; the id itself is sync (localStorage).
- Impact: every page load pays 3 extra RTT before the 619 KB catalog even starts (Lighthouse chain: /api/player 58 ms → /api/kids 59 → /api/sets 76 → state 81 on localhost). Over tailnet/phone that is +3×RTT on LCP, every load.
- Fix: one level.
```js
const remembered = state.rememberedId();           // raw localStorage read, unvalidated
const earlyState = remembered ? state.prefetchState(remembered) : null; // readState promise
const [, , , profilesLoaded] = await Promise.all([loadLink(), state.loadKids(), loadCatalog(), state.loadProfiles()]);
const known = profilesLoaded ? state.rememberedProfile() : null;
const selected = known ? await state.useProfile(known, undefined, earlyState) : false; // reuse promise if ids match
```
- Plus [bundle-preload]: start the catalog while modules are still loading: `<link rel="preload" href="/api/sets" as="fetch" crossorigin>` in index.html (same-origin; fetch() default credentials match `crossorigin=anonymous`). Overlaps the largest download with the 7-deep module waterfall.
- Behavior: none visible. loadKids calls `changed()` (watch-state.js:395) — harmless before pageReady. A speculative state read for a deleted profile is simply discarded.

### 2. Everything loads before first paint; delivery is a 7-deep, uncached waterfall [bundle-conditional, bundle-preload] HIGH (remote/mobile), LOW (LAN desktop)
- app.js:24 static-imports player.js → 35 modules/201 KB only needed after the first Play; app.js:30 featured-reel, :34 profile-picker, :23 status-view also only on interaction. All 59 must download+parse before app.js runs, so before any shelf.
- CSS: style.css:2-6 is `@import` ×5 → 2-level render-blocking chain; styles/playback.css (751 lines, 19 KB) only styles the dialog.
- index.html:10 preloads fraunces-latin.woff2 (67 KB) at high priority — used only for the wordmark (theme.css comment) — competes with the critical JS/CSS.
- Impact: with no-cache+no-ETag (server) nothing is ever revalidated as 304, so each load = 59 JS + 6 CSS requests queued on 6 HTTP/1.1 connections, 7 sequential levels. Main contributor to mobile LCP 8.0 s together with the 619 KB catalog.
- Fix (client-side, cheapest first):
  1. `<link rel="modulepreload">` for the deep chain (at least course-view, shelf-view, plate, set-badge, link, codec-support, watch-state, library) → flattens 7 levels to ~2.
  2. Replace @imports with 5 `<link rel=stylesheet>` in index.html; load playback.css as `<link rel=stylesheet href=... media="print" onload="this.media='all'">` or with the lazy player (below).
  3. Lazy player: `const playerModule = () => (loaded ??= import("./lib/playback/player.js").then(m => (m.initializePlayer(), m)));` — in `play()` run it in parallel with the existing `refreshState()` wait (app.js:289) so it adds **zero** latency to Play; warm it on `requestIdleCallback` after first route. Same for featured-reel (on Featured click) and profile-picker (only first run/switch).
  4. Drop the Fraunces preload (keep @font-face, swap).
- Server-side (out of scope, but it multiplies all of the above): ETag or content-hashed URLs + gzip/br. Catalog JSON compresses ~8-10x.
- Behavior: lazy player — `player.addEventListener("close")` in app.js:363 is on the static dialog, still fine; `initializePlayer` registers `pagehide` teardown only once it has loaded, which is correct (nothing to tear down before). Keyboard shortcuts in player-keys are dialog-scoped, so nothing is lost before first open.

### 3. Every Play waits on a full state read before the dialog even opens [async-defer-await] HIGH (perceived)
app.js:285-292: `state.refreshState().finally(() => openTitle(...))`, timeout 1500 ms (watch-state.js:211).
- Impact: each click = 1 RTT + server state serialization of the whole profile (6 KB here) with **no visual feedback**; on a slow link the click looks dead for up to 1.5 s. The stream request (`video.src`, player.js:684) cannot start until then, so time-to-first-frame = state RTT + stream TTFB instead of max().
- Fix: open the dialog and start direct-play loading immediately; apply the resume at `loadedmetadata` from whichever is newer (local vs refreshed). For direct play resume is already applied at loadedmetadata (player.js:667-676), so only the value source changes:
```js
const fresh = state.refreshState();           // start, don't await
openTitle(set, queue, { ...options, freshResume: fresh }); // player awaits it inside the loadedmetadata handler
```
  Conversions put the resume in the transcode URL, so keep awaiting for that path only (or await with a shorter timeout there). Alternative lower-risk: fire `refreshState()` on card `pointerenter`/`focus` (throttled, bundle-preload style) so the Play-time read is usually already settled.
- Behavior change: yes — "Carrying on from X" note may update after open; the up-next/title state is unchanged. Needs the stale-position race kept (request-id check at app.js:290).

### 4. Transcode start: hls.js (593 KB) fetched only after the server answers [async-parallel] MEDIUM
lib/playback/streaming/hls-playback.js:174-185 — `await acquireSession()` (server blocks until first segment exists, i.e. seconds) **then** `await loadHls()`.
- Impact: first conversion per day (hls.mjs is max-age=86400) on MSE browsers pays the 593 KB download serially after the encoder is ready. LAN: tens of ms; remote/mobile: ~1 s+ added to time-to-first-frame.
- Fix: `const hlsP = needsNativeHls() ? null : loadHls(); const session = await acquireSession(...); const Hls = await hlsP;` Optionally warm it in openPlayer when `playbackFor(set).kind !== "direct"`. loadHls already memoizes and resets on failure. No behavior change (release path unchanged; a load failure still throws after session acquired → existing catch releases).

### 5. First visit without a hash renders Home twice (+ a view transition) [js-early-exit / wasted work] MEDIUM
app.js:781-783: `location.hash = "#/home"` queues a `hashchange`, then `route()` renders; the hashchange handler (app.js:656-669) then calls `route()` again inside `startViewTransition`.
- Impact: every bare-URL load (the normal bookmark) builds Home twice, creates two sets of poster <img>, snapshots a view transition on first paint. Also pushes a history entry: Back returns to the bare URL, which routes to **Movies** (app.js:570 default), not the previous page.
- Fix: `if (!location.hash) history.replaceState(null, "", "#/home");` then `shownHash = location.hash;` then `route()`.
- Behavior change: yes, intentionally — Back no longer lands on a phantom Movies entry.

### 6. One user action → two or more full synchronous re-renders [js-batch-dom-css (batch the redraw)] MEDIUM
- `markFinished` = `clearProgress` + `setWatched` (watch-state.js:377-380), each calls `changed()` → `invalidateShelf()` → full `route()` (app.js:349-357). "Mark finished" on Continue rebuilds the shelf twice.
- Profile switch: `useProfile` → `changed()` → `route()` against the *old* library/kids filter (profile-picker.js:66 → watch-state.js:192), then the who-handler does `applyCatalog(); route()` again (app.js:396-398). Two full renders, the first with the wrong filter (not painted, but wasted — on Genre/Movies that is up to 348 cards).
- Fix: coalesce redraws per frame:
```js
let redrawQueued = false;
function scheduleRoute() { if (redrawQueued) return; redrawQueued = true;
  requestAnimationFrame(() => { redrawQueued = false; route(); }); }
```
  use it in `invalidateShelf` and the who-handler. Counts can stay synchronous.
- Behavior change: redraw lands on next frame instead of synchronously; check tests that assert DOM right after a state mutation.

### 7. `localeCompare(..., {numeric:true})` in sort comparators [js-hoist-regexp / js-cache-function-results — hoist the Intl object] MEDIUM-LOW
lib/library.js:117 (`byTitle`), :154, :274. V8 builds a collator per call when options are passed.
- Measured on real catalog (node 22): sorting 829 movies 3.10 ms → 0.13 ms with a hoisted collator (~24x). It is ~90% of `groupLibrary` (3.5 ms).
- Runs: twice at startup (loadCatalog app.js:499 + applyCatalog app.js:773), on every catalog change via SSE, on every profile switch. Mobile CPU ×4-6 → ~15-20 ms per pass on the main thread.
- Fix: `const COLLATE = new Intl.Collator(undefined, { numeric: true }).compare;` and use it in all three. Identical ordering (spec-equivalent). Also consider skipping the pre-profile `applyCatalog` at startup (build once after profile) — but it doubles as validation before commit (app.js:496-501), so keep unless validation is separated.

### 8. Genre shelf renders all matching films unpaged [rendering-content-visibility] MEDIUM-LOW
app.js:229-231 `movieGrid(films, ...)` with no pager; genres.js:35. Action = 348, Drama = 320 cards (each: plate, badges, `transcodeBadge` → `decidePlayback`).
- Impact: ~350 cards × ~8 nodes built on every visit, every SSE catalog change and every state change while on that page (full route()), plus a view-transition snapshot of a very tall page. Movies shelf avoids this with 48/page (app.js:147-153).
- Fix (either): reuse `pageOf`/`pager` as Movies does (consistent), or CSS `.grid.plates > .card { content-visibility: auto; contain-intrinsic-size: auto 300px; }` (list mode: `auto 96px`). CSS-only fix keeps behavior; paging changes URLs.

### 9. Catalog refresh on SSE re-downloads 619 KB each time, then a second serial RTT [async-parallel] MEDIUM (phones), needs server help
- app.js:736: every EventSource `open` (each time a tab becomes visible, app.js:742-749) → `refreshCatalog` → full `GET /api/sets`. Only the `text === catalogText` compare (app.js:494) avoids the re-render; the download still happens. A phone flipping back to the tab pays 619 KB uncompressed every time.
- app.js:702-706: `await loadCatalog()` then `await loadLink()` serially before redrawing.
- Fix: server ETag → the browser sends If-None-Match automatically under `no-cache` and gets a 304 (client needs no change; `response.text()` of a 304-revalidated cache hit returns the cached body). Client-side: `Promise.all([loadCatalog(), loadLink()])` in readCatalogOnce (costs one tiny /api/player call on unchanged refreshes — acceptable) or keep serial; low value alone.

### 10. Forced synchronous layout on every pointermove over the seek bar [js-batch-dom-css] LOW
lib/playback/thumb-strip.js:77-92: writes `frame.style.backgroundImage/Position`, `at.textContent`, then reads `bar.clientWidth` → reflow, then writes `strip.style.left`. Preceded by a `getBoundingClientRect` read (:98).
- Impact: one forced layout of the dialog per pointer event while scrubbing with previews. Dialog DOM is small, so few ms at most; visible only on weak devices during scrub.
- Fix: read `bar.clientWidth` (or reuse the rect width already read in `fractionOf`) before the writes.

### 11. Featured button shuffles the whole film pool just to test non-emptiness [js-early-exit] LOW
app.js:178-179: `picks().length > 0` runs `pickFeatured` = filter 829 + Fisher-Yates on every Movies render (page change, refresh). Sub-ms; fix is trivial: `library.movies.some((s) => s.poster && !state.isWatched(s.setId))`.

## Rules checked, nothing worth fixing

- **client-event-listeners**: all window/document listeners registered once (app.js top level; transport.js:342,347 inside one-time `initializePlayer`; featured-reel.js:83,87 inside `setUp`, guarded by `reel ??=` at :39). No per-render global listeners, no leaks.
- **js-index-maps / js-set-map-lookups**: `byId` Map already used for all id→set lookups (app.js:59,72,433); `held.*` are Map/Set. Remaining `.find`/`.includes` are on ≤ 7 collections, profiles, genre arrays, small lists.
- **js-combine-iterations**: groupLibrary 3 filters (library.js:366-370), colophon 5 passes (colophon.js) over 1154 rows — <0.1 ms. Not worth the readability loss.
- **js-cache-storage**: `shelfMode()` reads localStorage once per route; volume-store once per open; profile once at startup. Cold.
- **js-hoist-regexp**: all regexes are literals (no `new RegExp` anywhere); `Intl.DisplayNames` already hoisted (language-label.js:17). Only issue is the Collator (#7).
- **js-min-max-loop**: no sort-for-min/max. `Math.min(...)` in markdown.js:109 over a note's lines — fine.
- **js-tosorted-immutable**: every `.sort` is on an owned copy or a tree being built (library.js:298 `[...rest]`, home-shelves.js:154 `[...entries]`, watch-state.js:280 fresh array, series-summary copies). No shared-array mutation.
- **js-cache-function-results**: `byArrival(..., newestIn)` (home-shelves.js:57-58,154) recomputes `flattenCollection` inside the sort comparator (O(n log n) flattenings); `nextAfter` flattens up to 3× per play (app.js:311-313, 327-328). Real pattern, but measured 0.3 ms total at 6 shows / 1 course. Revisit (precompute key / WeakMap memo per collection) only if collection count reaches hundreds.
- **bundle-defer-third-party**: no third-party scripts; fonts self-hosted; hls.js already dynamically imported (hls-playback.js:39).
- **rendering-content-visibility** elsewhere: Movies paged at 48; course shows one folder level at a time; search capped server-side (50 hits for "a"); Continue/Watchlist small.
- Player hot loop: `timeupdate` (~4 Hz) does a few textContent writes (player.js:802-863) — fine.

## Notes on behavior-changing fixes
- #3 (open player before state refresh): resume note may change after open; conversions still need the awaited position.
- #5 (replaceState): Back-button history changes (improvement).
- #6 (rAF-coalesced route): redraw becomes async by one frame; tests asserting synchronous redraw need updating.
- #8 paging variant: new URLs `#/genre/X/page/N`; CSS variant has no behavior change.
- #2 lazy player: first Play loads player modules — hidden behind the existing refreshState wait only if done in parallel as described.

## Unresolved questions
- Is the tailnet/phone path a target for startup time? If viewers are LAN-desktop only, #1/#2/#9 shrink to tens of ms and #3/#6 lead.
- Server-side ETag + compression (web/src) is the biggest single lever for #2 and #9 — confirm it is covered by the separate server review.
