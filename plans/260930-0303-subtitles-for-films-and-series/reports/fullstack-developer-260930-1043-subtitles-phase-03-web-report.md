# Phase 03 implementation report — web player: subtitle files, playback rule, picker, profile setting, holds

Plan: `plans/260930-0303-subtitles-for-films-and-series/phase-03-web-subtitle-files-default-rule-and-settings.md`
Worktree: `/home/andre/Workspace/mediagram/.claude/worktrees/agent-adda19c5da1e60db8`, branch `worktree-agent-adda19c5da1e60db8`.
Base: merged `worktree-agent-aa581a9d67fed3199` (phase 02, tip `5a29b419`, 0.84.0) `--ff-only` before any edit, confirmed tip/version first.

## What shipped

**Rule module** (`web/public/lib/playback/subtitle-choice.js`, new, 141 lines): `trackKey`, `sameLanguage`, `audioLanguage` (`ger`/`deu`→`de`, `eng`→`en`, else lowercase passthrough, else the set's `alang[0]`), `chooseSubtitles` (`{audio, regular, forced}`), `toggleOn`, `visibility` (`{pickerRows, ccVisible, styleVisible}`). All 15 cases of the shared fixture (`web/test/fixtures/subtitles/choice-cases.json`, phase 02's) pass unmodified — `web/test/subtitle-choice.test.ts` drives every case plus unit cases for the four helpers. The cascade (`same key → same lang plain → same lang SDH → profile preference → none`) is one internal `cascadeMatch` shared by the "regular" resolution and `toggleOn`'s `last`/`preferred` tiers.

**Server reads** (`web/src/catalog/subtitle-tracks.ts`, new, 105 lines): `subtitleTracksBySet(db)` (v13 rows, else inline `assets` numbered by `ORDER BY lang`, never mixed for one set — checked against the set of v13-covered ids *captured before* the inline loop starts, not against the map being built by it, which was the first bug the tests caught), `bundleRef`, `legacyBody`. `catalog/assets.ts` trimmed to `summary` only; `subtitle`/`subtitleLanguages` moved here.

**Bundle fetch/cache** (`web/src/catalog/subtitle-bundles.ts`, new, 186 lines): `SubtitleBundles` — memory map keyed by `sha256` (32 entries, LRU), disk store at `heldSubtitlesDir(cacheDir)` = `<cacheDir>.subtitles` written only by `hold`/`reconcile`. Integrity: sha shape + bytes cap checked before any fetch; fetched bytes hashed before anything is written; `node:zlib gunzipSync({maxOutputLength})`, not `Bun.gunzipSync`. One `resolve(ref, fetcher, persist)` per read: the shared in-flight task returns `{bundle, gz}` (`gz: null` when it came from disk, already there); the *persist* decision is made by each caller after the shared task settles, not baked into the task — this is deliberate, see "Concerns" below for the one race it still leaves. `hold(ref)` is a no-op for `ref === null`, so the caller never needs to check. `reconcile(db, held)` holds every `held.ids` set's bundle and deletes nothing, tolerant of an index with neither v13 table.

Deviation from the literal spec text: `hold(setId)` became `hold(ref: BundleRef | null)` — the disk/memory keys are both `sha256`, and `setId` added nothing `ref` didn't already carry; callers (`preload-route.ts`, `reconcile`) already have `db` and look up the ref themselves, symmetric with how `reconcile(db, held)` already threads `db` explicitly rather than storing it.

**Route + wiring**: `SUBTITLE_PATH` in `catalog/routes.ts` changed from `/subtitles/:lang.vtt` to `/subtitles/:n.vtt` (`\d{1,3}`) — several tracks per language, so position is the key now, not language. Playable check first (matches the audio route). `tracksBySet` built once per router; `forBrowser` returns it. `CatalogRouterOptions.subtitles?: Pick<SubtitleBundles, "vtt"|"hold">`. `preload-route.ts`'s accept loop calls `subtitles?.hold(bundleRef(db, setId))` fire-and-forget. `web/src/routes.ts`'s one-line preload call passes `options.subtitles` through (no other change there). `application/catalog-follow.ts`: after `held.replaceExpected` succeeds, `subtitles?.reconcile(next, held)` fires in the background (not awaited — it's the `background` fetcher's whole point), warns on failure without hiding the catalog swap.

**`held.ts` gained `get ids()`** (readonly string[] from the last scan) so `reconcile` can enumerate held sets — the one addition outside the phase's literal file list; ratchet raised 202→207 with a dated comment, same convention the file's own header already uses.

**Browser**: `subtitle-picker.js` (new, 117 lines) — `mountSubtitlePicker({video, subs, picker, styleTrigger, recall, remember})`, reading `state.preferenceOf("profile","subtitle")` directly for the profile fallback (a fixed scope string, not per-show). `offer(tracks, alangJson)` resets `last`/`audioTag` per title; `setAudio(tag)` re-runs the rule (only `forced`/redundant-but-harmless picker rebuild actually depend on it); `toggle()` implements 'c'; the `<select>`'s own `change` remembers a manual pick as `last` too. `transport.js` lost its whole subtitle half (`subtitleOptions`, `lastSubtitle`, `applySubtitles`, the `subPicker` listener, `offerSubtitles`) — 471→388 lines, ratchet lowered with a dated comment — and gained one injected `toggleSubtitles` callback for `'c'`. `player.js`'s `attachSubtitles` now builds one `<track>` per API entry (`src` by number, `label` verbatim from the server, no `default`) and calls `subtitles.offer(...)`; `offerAudioTracks` and the audio picker's `change` both call `subtitles.setAudio(...)` with the *stream's own* tag (never the catalog's `alang`). Net line count: exactly 996 (the ratchet), reached by trimming comments rather than cutting logic — see the diff in the report's own git history if that trade is worth revisiting.

**Settings → Profile → Subtitles**: `settings-page.js` gained a row (Off/German/English), disabled with "Choose a profile first" until one is; reads/writes `preferenceOf`/`setPreference("profile", "subtitle", ...)` — no changes needed to `watch-state.js`, since a preference under a fixed scope string already worked.

**Facts fix**: `film-page.js`'s `details()` — `languages()` required an array, got `set.alang`/`set.slang` as JSON strings, so both rows were silently empty on every film. Fixed with a local `parsedLangs` (mirrors `series-summary.js`'s own, which `plan.md` explicitly keeps separate). `series-summary.js` untouched.

**Preview**: `scripts/preview-subtitles.ts` (new) — `PREVIEW_SUBTITLES=<setId>` adds the v13 tables to the index *copy* if missing, inserts `crates/mlib-spec/tests/fixtures/subtitle-bundle-v1.json`'s three tracks (the same fixture `mlib-spec` tests its own codec against — German Forced/German/English SDH, matching fixture case 1 exactly), gives the router a `SubtitleBundles` whose `fetch`/`background` both return the gzip'd fixture bytes (sha matches). `preview.ts`: the index copy opens writable only when `PREVIEW_SUBTITLES` is set (`{readonly:false}` itself throws `SQLITE_MISUSE` on this Bun version — confirmed by testing, so it's an absent-vs-present options object, not a boolean inside one), `subtitles` passed to `startServer`.

## Verified live (not just tests)

Ran the preview against a real machine index (`~/.cache/mediagram-channel-index/current/library.db`) with `PREVIEW_SUBTITLES=<Blade: Trinity's set id>` (a real title with `alang` starting `de`):
- `GET /api/sets` for that set returns exactly the three fixture tracks with `track`/`lang`/`forced`/`sdh`/`label`.
- `GET /api/sets/<id>/subtitles/0.vtt` → the forced track's WEBVTT body (`Achtung!`/`Da drüben.`).
- `GET /api/sets/<id>/subtitles/1.vtt` → the regular German track's body.

No browser (headed) screenshot: the gstack `/browse` skill's tool wasn't in this session's toolset, so I did not have a way to drive a real browser. The DOM-level coverage below exercises the identical code path (`mountSubtitlePicker` over a fake `video.textTracks`) the live browser would, and `browser-html-player.test.ts` runs the *real bundled* `player.js`/`app.js` through `Bun.build` against a fake DOM — that's the closest thing to a headed check this toolset offered. Worth a real headed pass before this ships if that matters to the lead.

## Tests

- `subtitle-choice.test.ts`: 23 (15 fixture cases + trackKey/sameLanguage/audioLanguage units).
- `subtitle-tracks.test.ts` / `subtitle-bundles.test.ts`: 30 (v13/inline/never-mixed/missing-table; memory dedup incl. concurrent-miss dedup; disk write only for `hold`/`reconcile`, never `vtt`; the "played-then-held still reaches disk" case that exists because of the persist-decision-after-await design; bad sha/bytes/mismatch/gzip-bomb refusals; corrupt-disk delete+refetch; reconcile scoping and idempotence).
- `subtitle-picker.test.ts`: 8, DOM-level (`browser-environment.ts`) — forced shows off/hides once regular shows/never shows with unknown audio; 'c' remembers per show; manual pick remembered as `last`; profile preference resolving to an SDH-only track and skipped when itself Off; picker row order/labels.
- `settings-page.test.ts`: 3 — disabled without a profile, persists a change, reads back a stored one. (No prior test file for `settings-page.js` existed; added the fixture harness's missing `childElementCount` getter, which `tabs.js`'s lazy panel build needs and the lightweight fake lacked — a real, if previously untested, DOM property.)
- `film-page.test.ts`: 3 — the JSON-string parse fix, missing columns, malformed columns.
- `browser-html-player.test.ts`: 2 of its existing 8 tests rewritten for the new default-off + lang-keyed-picker behaviour (real bundled app, not a stub); the rest updated only where a `pick("1")` ordinal became `pick("de")`.
- `http.test.ts`, `assets.test.ts`, `catalog-read-failures.test.ts`, `application-catalog.test.ts`, `series-preload.test.ts`, `transport.test.ts`: updated for the moved/renamed reads and the new `hold`/`reconcile` hooks; two new `application-catalog.test.ts` cases cover the reconcile wiring and its failure path.

`cd web && bun run typecheck && bun run lint && bun test`: clean, 2431/2431 pass. `bash scripts/check.sh` (jniLibs copied from the main checkout per instructions, not committed — confirmed gitignored): clippy clean, `cargo test --all` green across every crate, gradle `testDebugUnitTest`/`lint`/`compileDebugAndroidTestKotlin` all green (Android untouched by this phase; the run only proves nothing else broke).

## Deviations from the phase's literal text

- `hold(setId)` → `hold(ref: BundleRef | null)` — see above.
- `held.ts` gained `get ids()`, not in the phase's file list — needed for `reconcile` to enumerate held sets; small, additive, ratchet-documented.
- Added `childElementCount` to `test/support/player-environment.ts`'s base `Node` — a missing real DOM property the fixture needed for `settings-page.js`'s lazy tab-panel build to test at all; `browser-application.ts`'s `PageNode` already had the identical override, now redundant there but harmless.
- Added new test files not named in the phase (`settings-page.test.ts`, `film-page.test.ts`) since neither component had any prior test coverage and both gained real logic this phase.

None of these touch a file another phase in the rollout table owns.

## Not done here

No device access, no publish/push-index/upload, no headed browser screenshot (tool unavailable this session — see above). `web/src/index.ts` untouched, per instructions (another session's uncommitted work may still land there).

## Follow-up needed once `web/src/index.ts` lands (for whoever wires it)

Per the phase spec, `index.ts` should build one `SubtitleBundles` beside the existing `AudioTrackReader`, threaded through `startServer`, `CatalogFollower`, and `preload`:

```ts
import { SubtitleBundles, heldSubtitlesDir } from "./catalog/subtitle-bundles";
import { connectionFetcher, backgroundFetcher } from "./telegram/part-fetch"; // connectionFetcher: new import; backgroundFetcher already imported from ./telegram/source

const subtitles = cache
  ? new SubtitleBundles({
      fetch: (messageId, offset, length) => connectionFetcher(connection, messageId)(offset, length),
      background: (messageId, offset, length) => backgroundFetcher(connection, messageId)(offset, length),
      heldDir: heldSubtitlesDir(config.cacheDir),
    })
  : undefined;
```

Pass `subtitles` into the `startServer({...})` options object (alongside `audio`) and into `new CatalogFollower({..., subtitles})`. Startup reconcile (per spec, "beside `AudioTrackReader`"): `if (subtitles && held) void subtitles.reconcile(db, held);` right after the initial `held.refresh()` call. No file this phase owns needs to change for this — every type (`CatalogRouterOptions.subtitles`, `FollowOptions.subtitles`, `preloadResponse`'s fourth param) already accepts it. `SubtitleBundles` is gated on `cache` (mirrors `held`/`reader`/`preload`'s own gating) since a disabled cache means no `heldDir` worth writing and no reader to fetch a whole file through — `connectionFetcher`/`backgroundFetcher` do not themselves need the cache, only `hold`/`reconcile`'s disk writes do; a build with caching off would still serve `vtt()` correctly with `subtitles` present, so gating on `cache` here is a judgement call for whoever wires it, not a hard requirement — the spec doesn't say either way and I have not built it live to confirm which reads more naturally once the other session's `index.ts` changes are in front of it.

**Status:** DONE
**Summary:** Web player reads v13 subtitle tracks (falling back to legacy inline rows), fetches and caches bundles through a new `SubtitleBundles` (memory + held-only disk, fully integrity-checked, `node:zlib`-capped), applies the shared default+toggle rule via a new pure `subtitle-choice.js` proved against all 15 fixture cases, the picker (extracted from `transport.js` into `subtitle-picker.js`) offers Off+regular only and disappears for forced-only titles while the style trigger stays, 'c' remembers per show, Settings gained a per-profile Subtitles row, held titles' bundles are reconciled after every catalog swap, and the film page's JSON-string parse bug is fixed. Verified live against a real index (curl, three tracks, correct VTT bodies) in addition to 69 new/updated tests plus the full existing suite (2431/2431). `web/src/index.ts` wiring is the one piece left for whoever lands that file next — exact snippet above. Version 0.85.0 across `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` `versionName`, and all five workspace crates in `Cargo.lock` (`cargo metadata --locked --offline` confirms).
**Concerns:** (1) The bundle-fetch race noted in `subtitle-bundles.ts`'s own doc comment — two concurrent `hold`s of a bundle neither had on disk yet both write it; harmless (identical bytes, unique tmp names) but not lock-free in the strict sense. (2) No headed browser check this session (tool unavailable) — the DOM-level and real-bundled-app tests plus the live curl check are the strongest substitute available, but a five-minute headed pass before merge would be worth it given how much of this phase is picker UI. (3) `index.ts` wiring (above) is genuinely undone, not just deferred — it cannot be done without touching an excluded file.
**Branch:** `worktree-agent-adda19c5da1e60db8`
**Report path:** `/home/andre/Workspace/mediagram/.claude/worktrees/agent-adda19c5da1e60db8/plans/260930-0303-subtitles-for-films-and-series/reports/fullstack-developer-260930-1043-subtitles-phase-03-web-report.md`
