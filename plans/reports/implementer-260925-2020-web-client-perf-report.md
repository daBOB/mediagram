# Web client perf fixes — implementation report

Plan: `plans/260925-2020-web-streaming-and-startup-perf/plan.md` ("Client" section).
Findings: `plans/reports/perf-review-260925-2040-web-client-report.md`.
Scope: `web/public/**` and `web/test/**` (browser code tests only). No touch to `web/src/**`
(owned by the server implementer, working the same worktree in parallel — their in-flight
changes were present and untouched by this work).

## Changes, by finding

1. **Startup waterfall** (`app.js`): `loadLink`, `state.loadKids`, `loadCatalog`,
   `state.loadProfiles` now run via one `Promise.all`; only `useProfile` waits on the profile
   list. Rewrote the stale "asked for first" / "who, before anything else" comments to match.
2. **Player module lazy-load**: removed the static `import ... from "./lib/playback/player.js"`.
   Added `loadPlayer()` (memoized dynamic `import()` + `initializePlayer()`), warmed
   fire-and-forget right after the first `route()` call. `play()`/`openTitle()` now await it
   before calling `openPlayer`; a request superseded while it loads never opens. Added
   `modulepreload` hints in `index.html` for the deepest modules on the *first-shelf* critical
   path (library, watch-state, playable, codec-support, link, set-badge, plate, shelf-view,
   course-view) — the player's own graph is intentionally not preloaded, since it now loads
   lazily.
3. **Stylesheets**: `style.css` held only the five `@import`s, nothing else, so it is deleted;
   `index.html` links the five files directly in the same order. No selector/rule changes.
4. **Play opens immediately**: `play()` starts `state.refreshState()` without awaiting it,
   passes the eventual position as `options.freshResume` (a `Promise<number|null>`, `null` if
   superseded). `openPlayer` (in `player.js`) now always registers the `loadedmetadata` resume
   listener (not only when a local position exists), tracks whether metadata has already landed
   (`metadataSeen`), and applies `freshResume` on arrival — through the pending listener if
   metadata hasn't fired yet, or directly to `video.currentTime` if it has — but only when
   `Math.abs(filmTime() - resume) <= RESUME_DRIFT_SECONDS` (3s: "has not really begun"). Skipped
   for conversions (`converting`) — restarting an encode for a few seconds' difference isn't
   worth the churn; this is a documented, deliberate narrower scope than direct playback, called
   out in the code comment.
5. **HLS parallel fetch** (`hls-playback.js`): `loadHls()` now starts alongside
   `acquireSession(...)` (when MSE is needed) instead of after it resolves.
6. **Bare URL**: startup now does `history.replaceState(null, "", "#/home")` instead of
   `location.hash = "#/home"`, and manually syncs `shownHash` (the value the `hashchange`
   handler would otherwise have set) since `replaceState` fires no `hashchange`. One render,
   and Back no longer lands on a phantom Movies entry.
7. **Shared collator** (`library.js`): one `Intl.Collator(undefined, {numeric:true}).compare`
   (`COLLATE`) replaces three per-call `localeCompare(..., {numeric:true})` sites (movie titles,
   show/course-folder titles, show/course names). Same ordering, hoisted comparator.

Findings 6 (double redraw) and 8 (unpaged genre grid) from the report were left alone — out of
the requested scope for this pass and each still not "trivial and clearly safe" (6 touches every
`route()` caller's timing assumptions app.js and the tests already made about them; 8 changes URL
shape or adds a new CSS rule to a file under active restyle).

## Deviation from the report's suggested `play()` fix, and one I found while implementing

- The report's own sketch reused `readState`'s promise across `useProfile`; not done — the
  existing `useProfile(id)` signature was kept as-is, since the task only asked to stop other
  requests waiting on the profile list, not to restructure profile selection itself.
- Lazy-loading the player uncovered a real gap: three existing tests
  (`browser-html-player.test.ts`, "renaming required HTML control ... makes actual controller
  initialization fail") asserted that a broken player template made the *whole page* fail to
  load, because `initializePlayer()` used to run synchronously at module top level. With it
  deferred, an initialization error is now unhandled unless caught explicitly. Fixed by having
  `loadPlayer()` reset itself and rethrow on failure (mirrors `hls-playback.js`'s `loadHls`
  pattern), and `openTitle()` catch that failure and report it in `main` as an error, rather than
  leaving it an uncaught rejection. Rewrote the three tests to assert the new, arguably better
  behaviour: the library still loads and is browsable; pressing Play on a broken player reports
  the failure instead of silently doing nothing.

## Files touched

- `web/public/app.js` — startup, `loadPlayer`, `play`/`openTitle`, bare-URL fix
- `web/public/lib/playback/player.js` — `freshResume` handling, `RESUME_DRIFT_SECONDS`
- `web/public/lib/playback/streaming/hls-playback.js` — parallel `loadHls()`
- `web/public/lib/library.js` — shared `COLLATE`
- `web/public/index.html` — stylesheet links, modulepreload hints
- `web/public/style.css` — deleted (only `@import`s, now redundant)
- `web/test/browser-application.test.ts` — rewrote 2 tests for the new open/resume timing, added
  2 (startup concurrency, bare-URL `replaceState`), added 1 (late-arriving-resume-not-applied)
- `web/test/browser-html-player.test.ts` — rewrote the 3 "renaming required HTML control" tests
- `web/test/support/browser-application.ts` — added `history.replaceState`/`.length` to the fake
  `history` (was `pushState`/`back` only; needed for the bare-URL fix and its test)
- `web/test/library.test.ts` — added a numeric movie-title-sort test through the shared collator

## Test evidence

From `web/`:
- `bun run lint` — clean (`eslint public --max-warnings 0`).
- `bun test` — **1818 pass, 0 fail** (132 files), including all of the above plus the
  pre-existing suite and the other implementer's in-flight server-side changes present in the
  same worktree. Confirmed the targeted files individually first
  (`browser-application.test.ts`, `browser-html-player.test.ts`, `library.test.ts`,
  `hls-playback-lifetime.test.ts`, `player-manual-play.test.ts`, `player-initialization.test.ts`,
  `player-lifetime.test.ts`) before the full run.
- Did not start the real player; all verification is static checks + the existing stub-harness
  tests (`test/support/player-environment.ts`, `test/support/browser-application.ts`).

## Unresolved / worth a second look

- `RESUME_DRIFT_SECONDS = 3` is a judgement call ("has not really begun"); no prior constant to
  anchor it to. Reasonable given `TOO_EARLY_SECONDS = 30` in `resume-point.js` is the nearest
  existing threshold of this kind, but flagging in case a different value was expected.
- Findings 6 and 8 remain open per report, not fixed here — confirm they're intentionally
  deferred to a later pass rather than expected in this one.
