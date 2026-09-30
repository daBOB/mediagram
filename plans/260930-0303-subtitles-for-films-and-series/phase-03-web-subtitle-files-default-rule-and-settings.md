# Phase 03 — Web player: subtitle files, the playback rule, picker, profile setting, offline holds

## Context links
- Server: `web/src/catalog/routes.ts:25` (`SUBTITLE_PATH` keyed by language), `:36-45` (per-db lookups built once), `:52-66` (`forBrowser`), `:81` (`subtitles: subtitleLanguages(...)`), `:124-132` (audio + subtitle routes); `web/src/catalog/assets.ts:16-53`; `web/src/catalog.ts:79-84` (the browser never gets message ids).
- Fetch: `web/src/telegram/part-fetch.ts:42-45` (`connectionFetcher`), `:52-55` (`backgroundFetcher`, yields to viewer reads), `:97-121` (`fetchPartOnce` returns the whole document when `length >= size`); `web/src/telegram/client.ts:123-128,138-156`.
- Caches and holds: `web/src/cache/store.ts:137-139` (hits bump atime), `:257-306` (LRU walks every file under its root, skips `.tmp`); `web/src/cache/held.ts:1-9` (held = playable with no Telegram), `:68,102-105` (`HeldSets.has`); `web/src/cache/preload-route.ts:11-25` (next-episode preload); `web/src/application/catalog-follow.ts:23,122` (catalog swap, `held.replaceExpected`); `web/src/index.ts:143` (chunk cache exists only when `cacheMaxBytes > 0`), `:200-201` (`AudioTrackReader`); `web/src/config.ts:196` (`cacheDir`).
- **Another session's uncommitted edits**: `web/src/cache/{key,reader,series-preload,store,strategy}.ts`, `web/src/cache/in-flight-chunks.ts`, `web/src/index.ts`, `web/src/range.ts`, `web/src/telegram/source.ts`, `web/src/channel-index/find-newest-channel-index.ts`. Not edited here, except `web/src/index.ts` wiring after that work lands (rebase first).
- Browser: `web/public/lib/playback/player.js:170-190` (`attachSubtitles`), `:632-640` (open order; `:640` style trigger hidden with the picker), `:755-771` (audio tracks + remembered language), `:791-806` (audio change); `transport.js:299-326` (apply/toggle, `lastSubtitle`), `:374-411` ("first track on" at `:399-409`), `:389` (`subs.hidden = options.length < 2`), `:449-450` ('c'); `player-keys.js:80-81`; `preference-scope.js:30-38`; `watch-state.js:479-491`; `subtitle-panel.js:122-128,153-162`; `language-label.js:27-37`.
- Facts: `web/public/lib/catalog/film-page.js:98-108` (`languages()` needs an array, gets a JSON string → no Audio/Subtitles facts at all); `series-summary.js:46-49` ("read from the file's own tracks" — kept).
- Settings: `web/public/lib/catalog/settings-page.js:98-115`. Preview: `web/scripts/preview.ts:36-80`.
- Ratchets (`web/test/code-standards.test.ts`): `player.js` 996, `transport.js` 471, `src/index.ts` 441, `src/config.ts` 265 may not grow.
- Shared: `web/test/fixtures/subtitles/choice-cases.json`, `crates/mlib-spec/tests/fixtures/subtitle-bundle-v1.json` (phase 02). Rule text: `plan.md` → "Playback rule".
- Red team: scope-critic F2/F7, security F3/F4, failure-mode F4/F8, assumption-destroyer F3/F4.

## Overview
Priority P1 (reference surface). Effort 1.5d. Version: next **minor**. Status: pending. Depends on phase 02. Parallel with phase 04 (disjoint files).

## Key decisions
- **Old and new layouts both read** until phase 09: bundle, else inline `assets` rows (track `n` = n-th row by `ORDER BY lang`, label from the language).
- **URL keys by track number**: `/api/sets/:id/subtitles/:track.vtt` — several tracks per language; only this server's page calls it.
- **Cache (chosen, and why):** a bounded in-memory map for everything, plus a disk store only for held titles.
  - Memory: `Map<sha, Promise<Bundle | null>>`, 32 entries, re-inserted on hit. De-duplicates the forced + regular pair and repeat requests.
  - Disk: `<cacheDir>.subtitles/<sha>.json.gz`, a sibling of the chunk-cache root, so the chunk LRU never walks it. This needs no config key (`config.ts` is at its ratchet) and no edit to the other session's `web/src/cache` files.
  - Why not the chunk-cache LRU: it evicts by atime and needs `utimes` on hit (`store.ts:137-139`), which means editing those in-flight files. It could also evict a held title's bundle while that title's chunks stay, breaking the held badge's promise.
- **Offline parity with Android:** held titles keep their bundles.
  - `hold(setId)` runs when the next-episode preload accepts a set (`preload-route.ts`).
  - A background reconcile (via `backgroundFetcher`) holds every held set's bundle after startup and after each catalog swap. It never deletes: an index pushed without the v13 tables must not cost a held title its subtitles.
  - Lessons need no special hold on the web: a web lesson is only ever held by being watched in full, and the reconcile covers it.
- **Integrity:**
  - `sha256` must match `/^[0-9a-f]{64}$/` or the bundle is refused.
  - `bytes ≤ 16 MiB`; the sha is checked before writing.
  - Writes go to a unique `<sha>.<pid>.<rand>.tmp`, then fsync, then rename.
  - A disk hit that fails to decode is deleted and refetched.
  - `gunzipSync` comes from `node:zlib` with `maxOutputLength: 64 MiB`: `Bun.gunzipSync` has no cap (a 200 KB file inflated to 200 MB in the review).
- **Rule, toggle, visibility:** exactly `plan.md` "Playback rule" (forced shows when regular is off; 'c' switches regular only and remembers per show; style trigger visible when any track can show).
- **Audio language:** probed stream tag, else first of `set.alang`, through the rule module's `audioLanguage` with the explicit `ger|deu→de`, `eng→en` map (Bun's Intl does not canonicalise bibliographic codes).
- **Facts:** "Subtitles" keeps describing the file (`slang`), as `series-summary.js:46-49` decides. Only the film page's JSON-string parse bug is fixed (restores "Audio languages" and "Subtitles").

## Requirements
Server
- `web/src/catalog/subtitle-tracks.ts` (new): `subtitleTracksBySet(db) → Map<setId, SubtitleTrack[]>` built once per router (v13 rows, else inline rows; missing tables tolerated); `bundleRef(db, setId) → {messageId, bytes, sha256} | null`; `legacyBody(db, setId, track)`. `SubtitleTrack = {track, lang, forced, sdh, label}`.
- `web/src/catalog/subtitle-bundles.ts` (new): `SubtitleBundles({fetch, background, heldDir})` with `vtt(ref, track)`, `hold(setId)`, `reconcile(db, held)`; the rules above.
- `routes.ts`: track-number route (playable check first, as the audio route does; bundle or inline body; `text/vtt; charset=utf-8`, `Cache-Control: private, max-age=3600`); `forBrowser` → `subtitles: tracksBySet.get(id) ?? []`; `CatalogRouterOptions.subtitles?`.
- `assets.ts` keeps `summary` only.
- `preload-route.ts`: accepted items → `subtitles?.hold(setId)` (fire-and-forget); `src/routes.ts:115` passes it. Re-check `git status` first: this file must still be outside the other session's diff.
- `catalog-follow.ts`: after `held.replaceExpected` → `subtitles.reconcile(db, held)`; startup reconcile in the `index.ts` wiring beside `AudioTrackReader` (one `catalogReaders(...)` helper so `index.ts` does not grow).

Browser
- `playback/subtitle-choice.js` (new, pure, ≤ 150 lines): `trackKey`, `sameLanguage`, `audioLanguage(tag, alang)`, `chooseSubtitles`, `toggleOn`, `visibility` — the `plan.md` rule, tested against the shared fixture.
- `playback/subtitle-picker.js` (new): the subtitle half of `transport.js` moved out. Picker = Off + regular tracks; the forced track gets its own `<track>` shown per the rule; 'c' via `toggleOn`/off, remembering `trackKey`/`off` per show; `last` kept in memory per show scope; `audioLanguage` changes re-run the rule.
- `player.js`: `<track>` per API track (`src` by number, `label`, `data-key`, `data-forced`); after the audio probe (`:768-771`) and on audio change (`:791-806`) pass the audio language; style trigger (`:640`) follows `visibility.style`; `preferred = state.preferenceOf("profile", "subtitle")`. Net ≤ 996 lines.
- Settings → Profile panel: "Subtitles" Off / German / English → `setPreference("profile", "subtitle", value)`; hint "Forced subtitles still appear when a film switches language."; disabled "Choose a profile first" without a profile.
- `film-page.js`: parse `alang`/`slang` JSON strings before `languages()`.
- `library.d.ts`: `SubtitleTrack`, `subtitles: SubtitleTrack[]`.
- Preview: `scripts/preview-subtitles.ts` (new) — `PREVIEW_SUBTITLES=<setId>` adds the v13 tables to the index *copy* if missing, inserts the fixture's three tracks, and gives the router a `SubtitleBundles` whose fetch returns the gzip'd fixture (matching sha). Two lines in `preview.ts`.

## Architecture
```
GET /api/sets → tracksBySet (v13 | inline) → [{track,lang,forced,sdh,label}]
<track src=/api/sets/:id/subtitles/:n.vtt> → playable? → bundleRef?
   ├ yes: memory → <cacheDir>.subtitles/<sha> (decode ok?) → connectionFetcher(msg)(0,bytes) → sha ✓ → (held? write) → gunzip(node:zlib, capped) → tracks[n].vtt
   └ no:  inline body (n-th by lang)
preload-route accept → hold(setId);  startup / catalog swap → reconcile(held sets) via backgroundFetcher
subtitle-picker: rule(tracks, remembered(show), preferred(profile), audio, last) → regular | forced "showing"
```

## Related code files
- Modify: `web/src/catalog/routes.ts`, `web/src/catalog/assets.ts`, `web/src/cache/preload-route.ts`, `web/src/routes.ts` (preload call only), `web/src/application/catalog-follow.ts`, `web/src/index.ts` (net ≤ 0, after the other session lands), `web/public/lib/playback/player.js`, `transport.js`, `web/public/lib/library.d.ts`, `web/public/lib/catalog/settings-page.js`, `film-page.js`, `web/scripts/preview.ts`, tests asserting old shapes (`web/test/assets.test.ts`, `player-features.test.ts`, route/film-page tests), `docs/web-player.md`, `docs/project-changelog.md`.
- Create: `web/src/catalog/subtitle-tracks.ts`, `web/src/catalog/subtitle-bundles.ts`, `web/public/lib/playback/subtitle-choice.js`, `subtitle-picker.js`, `web/scripts/preview-subtitles.ts`; tests `web/test/subtitle-choice.test.ts`, `subtitle-tracks.test.ts`, `subtitle-bundles.test.ts`.
- Not touched: `web/src/catalog.ts` (phase 02), `web/src/state/**` (phase 08), the other session's files above, `series-summary.js`.

## Implementation steps
1. `subtitle-choice.js` + shared-fixture test (+ `sameLanguage`/`audioLanguage` cases).
2. `subtitle-tracks.ts` + tests (v13 ordered; inline fallback; v12 without tables; both present → v13).
3. `subtitle-bundles.ts` + tests with stub fetch: miss → fetch; memory hit → no fetch; held write/read; bad sha shape → refused, no fetch; sha mismatch → null, nothing written; over `bytes` cap → no fetch; gzip bomb → null; corrupt disk file → deleted + refetched; concurrent misses → one fetch; reconcile holds held sets only and deletes nothing, also against an index without the v13 tables.
4. Route, `forBrowser`, preload hook, catalog-swap hook, wiring; route tests (404 unplayable/bad/missing track; inline; bundle).
5. `subtitle-picker.js` extracted; `player.js` tracks, audio hook, style trigger.
6. Settings row; film-page parse fix; `library.d.ts`; preview helper.
7. `bun test`, `bun run typecheck`, `bun run lint`; ratchets hold. Bump by pattern; changelog; `docs/web-player.md` "Subtitles" (endpoint, rule, caches, holds).
8. Deploy (lead): restart the running player.

## Todo
- [ ] rule module + shared fixture
- [ ] tracks reader (v13 + inline)
- [ ] bundles: memory map, held store, integrity, node:zlib cap
- [ ] route, preload hold, reconcile, wiring
- [ ] picker extraction, forced when off, 'c' remembers, style trigger
- [ ] Settings → Profile → Subtitles
- [ ] film-page parse fix (facts stay `slang`)
- [ ] preview helper; docs; manifests; deployed

## Success criteria
- `cd web && bun test && bun run typecheck && bun run lint` green.
- Preview (`cd web && PREVIEW_SUBTITLES=<film set id whose alang starts with de> bun run preview`; never the real player for UI; video does not play there, so cue state is read from `textTracks` in the browser): picker Off / German / English (SDH), no Forced row; opens on Off with the forced `<track>` in mode `showing` (audio from the `alang` fallback); 'c' turns German on and a reopened episode of the same show keeps it; 'c' again → forced still shows; a forced-only fixture variant shows the style trigger and no picker; Settings → Profile shows the Subtitles row; the film page shows "Audio languages" and "Subtitles" from the file.
- API on the restarted player (no UI work): `curl -s localhost:8770/api/sets | jq '[.[] | select((.subtitles|length) > 0)] | length'` ≈ lessons with subtitles; `curl -s localhost:8770/api/sets/<lesson id>/subtitles/0.vtt | head -1` → `WEBVTT`.

## Tests
| Level | What |
|---|---|
| Unit | rule (shared fixture), audio fallback, toggle, visibility |
| Unit | tracks reader; bundles (memory, held store, integrity, caps, reconcile) |
| Route | shapes, 404s, inline vs bundle |
| Browser harness | picker rows; forced shown when Off; 'c' remembers; profile preference; style trigger for forced-only |
| Manual | preview walkthrough; curl checks |

## Risk assessment
| Risk | L × I | Mitigation |
|---|---|---|
| Ratcheted files grow | High × Low | Move code out first (picker module, readers helper) |
| Conflict with the other session's `index.ts` edit | Med × Low | Wire after it lands; rebase; net ≤ 0 |
| Held store grows without bound | Low × Low | Written only for held or preloaded titles, tens of KB each. Ponytail: no eviction; add a size cap if the directory passes ~100 MB |
| A lesson opens without subtitles (off by default) | Certain × Low | Decision 3; per-course memory keeps a choice; changelog says so |
| Profile-roles plan edits `settings-page.js` too | Med × Low | Sequential, rebase |

## Security
Set id and track validated by the route regex; playable check before any Telegram call; message ids never leave the server; sha shape checked before a file name is built; bytes verified before caching; decompression capped via `node:zlib`; labels rendered as text only.

## Rollback
Revert and restart the player. The index is untouched; held bundles are inert files.

## Next
Phase 06's first real upload exercises the bundle path end to end; phase 08 syncs the profile preference; phase 09 removes the inline path.

## Results (2026-09-30)
Preview walkthrough (headless Chromium, `PREVIEW_SUBTITLES=<ep1>,<ep2>,forced:<film>`), all as specified: episode opens Off with the forced German `<track>` showing (audio from `alang`); picker Off / German / English (SDH), no Forced row; 'c' → German on, forced off; 'c' → off, forced back; the next episode of the show opens with German on; forced-only film → forced showing, style trigger, no picker; film page Details: "Audio languages German, English", "Subtitles German"; Settings › Profile shows the Subtitles row (restyled as a pill select, 0.88.4). `index.ts` wiring landed in 0.88.3. Left: restart the running player (lead, with the user).
