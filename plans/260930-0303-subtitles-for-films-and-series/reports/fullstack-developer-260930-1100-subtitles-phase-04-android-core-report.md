# Phase 04 implementation report — Android core + player: subtitle files, cache, offline holds, the playback rule

Plan: `plans/260930-0303-subtitles-for-films-and-series/phase-04-android-core-subtitle-files-and-default-rule.md`
Worktree: `/home/andre/Workspace/mediagram/.claude/worktrees/agent-aea3d5881fe6709d7`, branch `worktree-agent-aea3d5881fe6709d7`, based on `worktree-agent-aa581a9d67fed3199` (5a29b419, phase 02, 0.84.0) via `git merge --ff-only`, confirmed before any edit.

## What shipped

**Rust core (`mediagram-core`)**

- `catalog_subtitles.rs` (new): `tracks_by_set` (v13 `subtitle_tracks` per set, else inline `assets` rows synthesised in `ORDER BY lang`, bundled sets never fall back — mirrors the uploader's own merge invariant), `bundle_ref` (chat/message/bytes/sha256, never crossing the FFI boundary), `legacy_body`, `course_run` (a lesson plus up to `next` that follow it, ordered by `group_key`/`season`/`CAST(episode AS INTEGER)`). 12 tests.
- `catalog.rs`: `PlayableSet`/`COLUMNS` gained `alang`/`slang`, parsed tolerantly from the JSON columns (`languages_in`, mirrors the web's own `languagesIn`).
- `catalog_assets.rs`: kept the summary half only; `text()` refuses `"subtitle"` now, matching `Core::set_text`'s own refusal.
- `dto/subtitle.rs` (new): `SubtitleTrack { track, lang, forced, sdh, label }`. `dto/summary.rs`: `SetSummary.subtitles: Vec<SubtitleTrack>`, `alang`, `slang` added.
- `api/channel/download.rs` (new): the capped `iter_download` loop extracted out of `install.rs`, parameterised over `max_bytes`/`what`/an `oversize` error closure — shared now by the index snapshot and a subtitle bundle. `install.rs` calls it unchanged in behaviour; `responses::Responses` widened to `pub(in crate::api)` to match.
- `api/subtitles.rs` + `api/subtitles_cache.rs` (new, the latter a private submodule kept apart to hold the 200-line limit): `Core::subtitle_text(setId, track)`, `Core::hold_subtitles(setId) -> bool`, `Core::hold_course_subtitles(setId)`. The cache: `<data_dir>/subtitles/<sha>.json.gz`, sha256-verified before a rename ever makes a download visible, `.tmp`+`sync_all`+rename, a corrupt or missing cache entry silently refetched, a 64 MiB LRU trim (mtime-ordered) run only after a write grows the directory — never on install. A process-wide per-sha lock table (`ponytail`-marked: global rather than per-`Core`, since one `Core` lives per app run; flagged for a per-`Core` field if a test ever needs two live `Core`s not to share it) serialises concurrent fetches of the same bundle. 9 tests covering bad-sha/oversize refusal before any fetch, a cache hit needing no route, a corrupt hit triggering (and failing cleanly on) a refetch, LRU eviction order, and lock exclusivity.
- `api/mod.rs`: `mod subtitles;` only — the lock table's own field was dropped from `Core` to stay inside the crate's 200-line-per-file rule (enforced by `crates/mediagram/tests/code_standards.rs`, which I ran into directly), replaced by the process-wide static above.
- `api/set_text.rs`: kind `"subtitle"` now answers `None` before touching the database, matching the new per-track path.

**Android — model/data (`core:model`, `core:data`, `core:playback`, `core:testing`)**

- `model.SubtitleTrackInfo` (new): `track, lang, forced, sdh, label`. `MediaSet` gained `subtitles: List<SubtitleTrackInfo>`, `alang`, `slang`; kept `subtitleLanguages: List<String>` as a *derived* compatibility field (`subtitles.map{it.lang}.distinct()`) — see "ui-tv" below for why.
- `CatalogRepository.toMediaSet` maps the new `SetSummary` fields straight across; a new default method `holdCourseSubtitles(setId)` (no-op default, so no fake needs it) delegates to `core.holdCourseSubtitles`.
- `SubtitleTrackSource.load(setId, track: Int)` (was `lang: String`) calls `core.subtitleText(setId, track.toUInt())`.
- `CacheDataSourceWriter.write()` opens with `runCatching { currentCore()?.holdSubtitles(item.setId) }` ahead of the video cache write — best-effort, sequential (bundles are small; no new coroutine scope needed), covers both `SeriesPreloader` and `FilmPreloader` since both funnel through this one writer.
- `FakeCore`: `subtitleText`/`holdSubtitles`/`holdCourseSubtitles` added (harmless defaults: `null`/`false`/`Unit`).

**Android — `feature:player` (the rule + controllers)**

- `SubtitleChoice.kt`: `audioLanguage`, `trackKey`, `chooseSubtitles` (regular + forced together), `toggleOn`, `subtitleOptions`, `visibility` — ported from `plan.md`'s playback-rule text line for line (no reference JS existed in this worktree; phase 03 is parallel and hadn't landed here). Verified against the full shared fixture, `web/test/fixtures/subtitles/choice-cases.json` (14 cases) via `SubtitleChoiceTest.kt`'s `matchesTheWebsFixtures`, walking up from the module directory the same way `feature:catalog`'s own fixture tests do. **Found and fixed a real bug while wiring this test**: the first draft only tried `preferred` as a fallback *after* `remembered` failed, never as the primary candidate when `remembered` was absent — the fixture caught it (`toggle on with last unset falls to a set profile preference` failed before the fix, passed after).
- `SubtitleChoiceController.kt`: rewritten around tracks/audio/toggle rather than languages. `onTracksKnown` (was `onLanguagesKnown`), `onAudioLanguageChanged` (new, fed by `AudioChoiceController`), `onPreferencesLoaded(scope, profileId, remembered, preferred)`, `choose`, `toggle` (new — the CC/captions-key entry point phase 05 wires), `reset`. Cues follow whichever of {the shown regular track, an applicable forced one} is active, fetched by `SubtitleTrackSource.load(setId, track.track)`.
- `AudioChoiceController.kt`: gained `onLanguageChanged: (String?) -> Unit = {}`, reporting the playing track's language (pinned, remembered, or ExoPlayer's own default selection) at every point `onOptionsChanged` already fires — `SubtitleChoiceController`'s only source for "the playing stream's language tag" in the playback rule. Its one existing direct-construction call site (`AudioChoiceControllerTest.kt`) needed a named-argument fix: adding a defaulted parameter after `onOptionsChanged` silently changed what a *trailing lambda* call bound to.
- `PlayerChoicesController.kt`: `subtitleChoice` declared ahead of `audioChoice` (an init-order note — `AudioChoiceController`'s own init block can invoke callbacks eagerly under `Dispatchers.Main.immediate`, before the rest of the constructor body has run). `resolve()` loads the profile's own default subtitle language under a fixed `"profile"` scope, alongside the show's own `scopeOf`-keyed "remembered" load; fires `catalogRepository.holdCourseSubtitles(setId)` fire-and-forget when the opened set is a `Kind.TUTORIAL`. New `toggleSubtitles()`.
- `PlayerChoices.kt`: `subtitleStyleVisible: Boolean` added for phase 05's style/offset gate.
- `PlayerViewModelDelegates.kt`: `toggleSubtitles()` delegate (kept `PlayerViewModel.kt` itself untouched, at 198 lines).
- Tests rewritten for the new decisions: `SubtitleChoiceControllerTest.kt` (off by default, a forced track showing regardless — including with subtitles off — an unknown audio language never showing one, a remembered per-show choice over a profile default, the toggle-on chain including "last wins", a pick racing the preference load), `SubtitleChoiceLifecycleTest.kt` (rotation keeps the choice, a different title resets to *its own* default — now off, not "first track" — independence from ExoPlayer's `Tracks` events), `SubtitleTestFixtures.kt`/`FakeSubtitleTrackSource.kt` updated to the new shapes.

**ui-tv (not touched, per instructions — a real, bounded conflict)**

`SubtitleTrackSource` gained a `@Deprecated` `load(setId, lang: String): List<TimedCue> = emptyList()` overload purely so `ui-tv`'s own in-flight branch, whose three test files mock the old by-language shape (`TvPlayerCueRoomTest.kt`, `TvPlayerSubtitlesTest.kt`, `TvPlayerSubtitleSettingsTest.kt`), keep *compiling*. `MediaSet.subtitleLanguages` was kept for the same reason (those same files call `.copy(subtitleLanguages = ...)`). Both fixes are compile-only: **13 ui-tv unit tests still fail** (`ComposeTimeoutException`, no cue ever renders) because those fixtures mock the deprecated overload while production now calls the `Int` one exclusively, *and* because they were written against the retired "nothing remembered → first language wins" default, which the phase's own decision ("off by default") replaces. I could not fix this without editing `android/ui-tv/**`, which the task explicitly forbids (another branch is concurrently changing those same files). This is the **one thing standing between `scripts/check.sh` and green** — clippy, the full Rust workspace test suite, `bun test` (skipped, no `node_modules`, pre-existing), and every Gradle module's compile/lint/test that I own are all clean; `:ui-tv:testDebugUnitTest` is the sole failure, deterministic across two full runs.

## Bindings and native rebuild

Ran `ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh` (cross-compiles `mediagram-core` for all 4 ABIs via `cargo ndk`, then `uniffi-bindgen`). `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` is committed (the generated `.so` files under `android/core/rust/src/main/jniLibs/*/` are gitignored, as designed — CI/the lead reruns the same script and diffs the committed Kotlin output). **The lead must run this script (or at least `scripts/build-android-core.sh`) again before any device install** — the `.so` files in this worktree are real but untracked, and a fresh checkout/merge has none.

`android/core/ffmpeg/src/main/jniLibs/*/libffmpegJNI.so` (a separate, unrelated native lib, gitignored) is **not present in this worktree** — it was never copied here, per the instructions naming it as something to fetch from the main checkout before a device install. Nothing in this phase touches ffmpeg; flagging only because a device install needs it.

## Deviations from the phase's literal file list

- `crates/mediagram-core/src/api/subtitles_cache.rs` is a file the phase's "Create (Rust)" list did not name (only `api/subtitles.rs`/`api/subtitles_tests.rs`, mirroring `api/channel/download.rs`). Split out once `api/subtitles.rs` combining the uniffi surface *and* the cache's sha/tmp/rename/LRU/lock machinery would have meaningfully exceeded the 200-line limit — the same kind of split phase 02's report made for `schema_versions.rs`.
- `tests/dto_mapping.rs`, `api/store/editorial_tests.rs`, `tests/index_extras_in_catalog.rs`: not in the phase's file list, but direct, required consequences of `PlayableSet`/`SetSummary.subtitles` changing shape — every one was already failing to compile before the fix, none is owned by another phase.
- `MediaSet.subtitleLanguages` (compatibility field) and `SubtitleTrackSource`'s deprecated `load(setId, lang: String)` overload: not requested by the phase, added solely to keep `android/ui-tv/**` — explicitly off-limits — compiling. See above; still leaves 13 ui-tv tests failing on behaviour, not compilation.
- `docs/system-architecture.md`/`docs/project-changelog.md` updated per the phase's own "Docs" line.

None of these touch a file another phase in the rollout table owns.

## Verification

- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `cargo test --workspace --all-targets`: all green (435 in `mediagram-core`'s own lib suite alone, plus every integration suite across the workspace) — `every_source_file_stays_under_the_line_limit` included, which caught the `api/mod.rs` overrun described above.
- `cargo metadata --locked --offline`: succeeds at 0.85.0 for all five workspace crates.
- Android: `:core:model:test`, `:core:data:testDebugUnitTest`, `:core:playback:testDebugUnitTest`, `:core:testing:testDebugUnitTest`, `:feature:catalog:testDebugUnitTest`, `:feature:player:testDebugUnitTest`, `:ui-mobile:testDebugUnitTest` all pass. `:app:compileDebugKotlin` and `:ui-tv:compileDebugKotlin`/`compileDebugUnitTestKotlin` compile clean. `:ui-tv:testDebugUnitTest` fails its 13 subtitle-cue-rendering tests, as described.
- `bash scripts/check.sh`: fails, once, at `:ui-tv:testDebugUnitTest`, for the reason above — reproduced deterministically across two full runs, same 13 test names both times, nothing else in the whole script (clippy, cargo test, gradle compile, gradle lint, every other module's tests) failed either time.
- Read every file the phase's Context Links cited against the actual working tree (set_text.rs, catalog_assets.rs, transport/stream.rs, api/channel/install.rs, api/read.rs, api/mod.rs, subtitle_bundle.rs, the six Android controller/preferences/cache files it named, TitleDetailScreen.kt/SeriesSummary.kt, FakeCore.kt) before writing anything; none of the cited lines had drifted.

## Not done here (explicitly out of scope)

No device access, no publish/push-index/upload. `android/ui-tv/**` untouched beyond what compiling against the two changed interfaces required nothing further — no behavioural fix attempted there.

## Concerns / things worth the lead's attention

1. **`scripts/check.sh` is not fully green** — see above. This needs either: the ui-tv branch rebasing onto (or after) this commit and updating its own three test files (mock `load(setId, track: Int)`; seed a remembered/preferred value, or a forced track, since nothing self-selects any more), or an explicit lead decision to accept this as a known gap until that branch lands. I did not touch those files myself.
2. **Course hold's `next` count (10) and its SQL ordering (`group_key`, then `season`, then `CAST(episode AS INTEGER)`)** is my own reading of `set_lookup.rs`'s existing `group_key + season + episode` course-lookup shape, not something re-derived from the web (phase 03 hadn't landed here to cross-check against). Worth a second look once phase 03/08 land, in case the web's own course-ordering differs.
3. **Deprecated `SubtitleTrackSource.load(setId, lang: String)`** and **`MediaSet.subtitleLanguages`** are compatibility shims for `ui-tv` alone — nothing in `feature:player`/`ui-mobile` calls either any more. Safe to delete once `ui-tv`'s branch has moved onto the new shapes; flagged with `ponytail:`/doc-comment notes at both sites.
4. **The lead must rebuild the native core** (`scripts/build-android-core.sh`, or the whole `generate-android-bindings.sh`) before any device install — this worktree's `.so` files are real (all 4 ABIs) but gitignored, so a fresh checkout of this commit has none.

## Lead's device checklist

- Rebuild native: `ANDROID_NDK_HOME=<ndk path> bash scripts/build-android-core.sh` (or the full `generate-android-bindings.sh` if the Kotlin bindings might also have drifted — they should not have, since nothing changed after this commit).
- Copy `libffmpegJNI.so` into `android/core/ffmpeg/src/main/jniLibs/*/` from the main checkout (gitignored, unrelated to this phase, needed for any install regardless).
- Tablet: `ANDROID_SERIAL=caad49da ./gradlew :app:installDebug` (per the standing rule: always pin the serial — a TV box and emulators may be attached).
- TV box: `installBenchmark` + `compile -m speed` against `192.168.0.35:5555` (per the standing rule for that box).
- **Test profile only** — per the standing rule, never the real profile (device playback tests land in Continue).
- Manual check (installs with phase 05, not fully exercisable until its CC/captions-key wiring lands): open a Geldhochschule lesson with subtitles off, confirm no cues; there is currently no UI surface in this phase alone to pick a track by hand (that is `PlayerSettingsSheet`'s `SubtitleSection`, already wired to `chooseSubtitleLanguage`, so it should work today if a bundled title with tracks is opened) — watch logcat tag `subtitles` for a bundle fetch/hold failure on that title if cues do not appear.
- Nothing here writes to the channel or `push-index`; the bundle path itself (an actual Telegram fetch of a real bundle) is only exercised end-to-end once phase 06 has shipped a real upload, per the plan's own rollout gate.

**Status:** DONE_WITH_CONCERNS
**Summary:** Rust core (tracks/bundle reader, sha-checked cache with LRU trim and per-sha lock, the three new `Core` methods, bindings regenerated and native `.so` rebuilt for all 4 ABIs) and the Android `feature:player`/`core:*` side (the ported playback rule verified against the shared fixture, the rewritten subtitle controller with forced-track/audio-fallback/toggle-on/last-track-memory, preload and course holds wired in) are implemented, tested, and committed at 0.85.0. `scripts/check.sh` fails only at `:ui-tv:testDebugUnitTest` (13 tests), a direct and unavoidable consequence of the phase's own required interface change colliding with `ui-tv`'s concurrently-edited, explicitly off-limits test fixtures — not a defect in this phase's own code, and not fixable without touching files I was told not to touch.
**Concerns:** See the four numbered points above; #1 is the one that blocks a fully green `check.sh` and needs the lead's call.
**Branch:** `worktree-agent-aea3d5881fe6709d7`
**Commit:** `b40e5fdf`
**Report path:** `/home/andre/Workspace/mediagram/.claude/worktrees/agent-aea3d5881fe6709d7/plans/260930-0303-subtitles-for-films-and-series/reports/fullstack-developer-260930-1100-subtitles-phase-04-android-core-report.md`
