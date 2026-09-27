# Android catalog reads: performance refactor (phases 01-03)

Work context: `/home/andre/Workspace/mediagram-channel-index` (worktree, branch `refactor/catalog-reads`). Plan: `plans/260927-1636-android-catalog-reads/plan.md` (phases 01-03 ticked `done`; phase 04 is the lead's tablet work — untouched here, no device/emulator was used).

## What changed

**Rust core (`crates/mediagram-core`)**

- `SetSummary` (`dto/summary.rs`) now carries `poster_path`, `backdrop_path` (was `backdrop_key`) and `season_poster_path`, all resolved absolute paths or `None` — `poster_key` stays as the identifier `titleInfo`/`titleCredits`/preference scoping still need.
- `api/store/editorial.rs`: `list_sets` and new `media_set` share one `enrich()` pass. It resolves poster/backdrop/season-poster once per row using the *one* SQLite connection already open for the listing, and checks `crate::artwork::table_exists` once for the whole pass rather than once per key. Poster and season poster materialize from the index's `artwork` table on a miss (matching today's `poster_path` behaviour); backdrop stays disk-only (matching today's `list_sets` gate) — this was the one place keeping "match today's display exactly" and "batch" both true required a real decision, not a mechanical change.
- `api/store/resolve.rs` (new): the shared low-level resolver (`on_disk` + `poster_path` + `resolve_with`), split out to keep `store.rs` under the line limit.
- `api/mod.rs`: added `Core::media_set(set_id)` (`catalog::playable_set`'s indexed lookup, not a full listing); **deleted `Core::poster_path`** — no Kotlin caller survives this refactor (see below), so its stale doc went with it. The internal `store::poster_path` free function stays, used by credits/portrait resolution and by the listing's own `resolve_with`.
- `api/credits.rs` / `dto/credits.rs`: `CreditRecord`/`PersonRecord`/`PeopleHitRecord.portrait_key` renamed to `portrait_path`, now the resolved path — `portrait_if_held` was computing the full path and discarding it to keep just the key; it now keeps the path.
- `artwork.rs`: added `table_exists`, `get` now shares it.
- Tests: `api/store/editorial_tests.rs` (new, 12 cases) — resolved poster/backdrop/season-poster paths, present vs missing artwork, episode resolves its **show's** key not its own, season poster present/absent, backdrop never materializes from the table (poster does), one-set lookup hit/miss/before-any-catalog. `api/credits_tests.rs` +1 (a cast portrait already on disk resolves onto the record). `tests/api_surface.rs`: removed the FFI-level `poster_path` test (method gone), added 3 `media_set` FFI tests. `tests/index_extras_in_catalog.rs`: backdrop test updated to assert a resolved path, not a bare key.

**Android (`android/`)**

- `CatalogRepository.kt`: `sets()`/`mediaSet()` read `posterPath`/`backdropPath`/`seasonPosterPath` straight off `SetSummary` — no more `core.posterPath(key)` per set (was 2 crossings/set: poster + backdrop). `mediaSet(id)` now calls the new `core.mediaSet(id)` FFI method instead of `core.listSets().find{}}` (was one full-catalog crossing per open). Credits/person/search-people map `portraitPath` straight through — no more `core.posterPath(key)` per cast/crew/hit. **Removed** the `posterPath(key)` repository method entirely (only remaining caller was the TV season-plate wiring, removed below).
- `SeasonWall.kt`: `SeasonPlate.posterKey` → `posterPath`, read from `division`'s episodes (`MediaSet.seasonPosterPath`, new field) rather than derived from `collection.posterKey` + season number and resolved via a Core call.
- `TvCollectionHeader.kt` / `TvCollection.kt`: `TvSeasonPlate` no longer takes a `posterPath` lookup lambda; `TvCollection`'s `posterPath` param removed entirely (its only use was that lambda).
- `CollectionScreen.kt` (mobile): removed the unused `posterPath` param (task named this one explicitly — confirmed unused, nothing in the body read it).
- `RememberLookups.kt`: removed `rememberPosterPath` (its one caller, `TvSeasonPlate`, no longer needs it).
- `FakeCore.kt`: removed `posterPath` override + `posters`/`posterPathCalls`; added `mediaSet` override (with `mediaSetCalls` tracking) and a `listSetsCalls` counter so a test can assert "no full listing happened."
- `CoreContract.kt`: added `aFreshCatalogsOneSetLookupAnswersNoneForAnyId` — runs against both `FakeCore` (here) and the real core (`RealCoreContractTest`, tablet, lead's job).
- Bindings regenerated via `scripts/generate-android-bindings.sh` (rebuilt `.so` for all 4 ABIs + refreshed `mediagram_core.kt`) — the committed Kotlin diff is generator output, not hand-edited; verified by diff inspection.

## `poster_path` outcome

Deleted from the Core FFI surface. Audited every Kotlin call site of `core.posterPath(...)`/`repository.posterPath(...)` before removing: two direct per-set calls in `toMediaSet` (poster, backdrop — now resolved fields), three in credits/person mapping (now resolved fields), and the TV season-plate lookup (now `MediaSet.seasonPosterPath`, resolved on the listing). The mobile `CollectionScreen`'s `posterPath` param was already dead (never read in the body). Once all six were gone, `CatalogRepository.posterPath`/`CatalogViewModel.posterPath` had no remaining callers and were removed too. The internal Rust `store::poster_path` free function stays — credits/portrait resolution and the listing's batched resolver both still use it.

## Tests

Rust (`mediagram-core`), `cargo test -p mediagram-core -q`: **496 → 511 passed** (+15: editorial_tests.rs new file +12, credits_tests.rs +1, api_surface.rs net +2 [-1 removed FFI test, +3 `media_set` tests]), 1 ignored, 0 failed. `cargo test --workspace -q`: green (other crates untouched, web untouched). `cargo clippy -p mediagram-core --all-targets`: clean.

Android JVM unit tests (`./gradlew testDebugUnitTest`): **1479 → 1477** (net -2: `SeasonWallTest` -1 [3 `posterKey`-derivation tests replaced by 2 `posterPath`-passthrough tests], `RememberLookupTest` -2 [`rememberPosterPath`'s two tests, function deleted], `CoreContract` +1 [`mediaSet` offline case, doubles as a tablet case via `RealCoreContractTest`], `CatalogRepositoryTest` net 0 [1 dead test removed, 1 new artwork-passthrough test added]). No test weakened — every removed test covered a capability that was itself removed (the FFI `poster_path` method, `SeasonPlate.posterKey` derivation, `rememberPosterPath`); every capability that moved (credits portrait resolution, season poster resolution, single-set lookup) kept or gained coverage.

Full requested check green: `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin` — `BUILD SUCCESSFUL`.

## Docs & release

- `docs/system-architecture.md` §10.1 (artwork table): rewritten to describe the one-pass batched resolver (`editorial.rs`) instead of the old per-key `poster_path` claim.
- `docs/project-changelog.md`: new top entry `## 0.68.14 — the Android catalog asks its core once per question` (Changed: batched listing artwork, resolved credit portraits, `media_set`, artwork-table-existence-once; Internal: `Core::poster_path` removed, field renames, TV/mobile lookup removal).
- Version bumped 0.68.13 → 0.68.14 in `Cargo.toml` (workspace, all members inherit), `web/package.json`, `android/app/build.gradle.kts` `versionName` (not `versionCode`, per project rule — pre-1.0, this is a minor-equivalent internal change, no schema/behaviour break). `cargo check -q -p mediagram` refreshed `Cargo.lock` (5 lines).
- Plan phases 01-03 ticked `done` in `plan.md` (no `ck` CLI available in this environment; edited the Status column directly per the fallback instruction).

## Not done here (explicitly out of scope)

Phase 04 — tablet contract suite (`RealCoreContractTest`), before/after `mediaSet` timing and cold-launch timing on `caad49da`, smoke test. No device/emulator was touched, no install run, per instructions.

## Files changed

Rust: `Cargo.toml`, `Cargo.lock`, `crates/mediagram-core/src/{artwork.rs, dto/summary.rs, dto/credits.rs, api/mod.rs, api/store.rs, api/store/editorial.rs, api/credits.rs, api/credits_tests.rs}`, new `crates/mediagram-core/src/api/store/{resolve.rs, editorial_tests.rs}`, `crates/mediagram-core/tests/{api_surface.rs, index_extras_in_catalog.rs}`.

Android: `android/app/build.gradle.kts`, `android/core/rust/.../mediagram_core.kt` (generated), `android/core/{data,model,testing}/...`, `android/feature/catalog/...`, `android/feature/player/src/test/.../FakeCatalogRepository.kt`, `android/ui-common/...`, `android/ui-mobile/...`, `android/ui-tv/...`.

Docs: `docs/system-architecture.md`, `docs/project-changelog.md`, `web/package.json`, `plans/260927-1636-android-catalog-reads/plan.md`.

**Status:** DONE (phases 01-03 as originally reported)

## Code-review fix round (same branch, no commit)

Addressed 9 findings from the coordinator's review, all in `mediagram-channel-index`, no device/commit touched.

- **M1** (real bug): the listing was *not* reading artwork once — `has_custom_art: bool` only gated "does the table exist," so every set with a missing-on-disk key still called `crate::artwork::get` (two queries) regardless of whether *that* key was actually in the table. Fixed: `crate::artwork::keys(conn)` reads every key the table holds into a `HashSet` once per enrichment; `resolve_with` takes that set and only queries a key that's actually in it. Added `resolve_cached` (a `HashMap<String, Option<String>>` memo in `enrich`) per the ask, defensive against a failed write being retried per row. New tests prove both halves with a `#[cfg(test)]` call-counter on `artwork::get` (thread-local, compiled out of release): a key absent from the table is queried 0 times even when the table holds other keys; a key shared by 5 sets is queried exactly 1 time.
- **M2**: fixed the `media_set` doc — it does *not* save the facts/genres/subtitles reads (those stay whole-table, same cost as a full listing); what it actually saves is scanning/enriching every other row, resolving artwork for every other row, and not crossing every other row back over FFI. Did not narrow the `shows`/`catalog_assets` queries by set id — would mean new query variants across 4 files (`shows/mod.rs`, `shows/genres.rs`, `shows/facts.rs`, `catalog_assets.rs`), not a small/contained change, and the coordinator's own instruction made that conditional.
- **M3**: confirmed and documented — the real index holds 12 `title-*-bg` rows in `artwork` with no disk file; the web shows them (`has()` counts the table), Android's disk-only backdrop check does not. Kept the behaviour (no visible change, as instructed), fixed the wording everywhere it overclaimed parity (`summary.rs`, `editorial.rs`'s `resolve_artwork` doc, `editorial_tests.rs`, `project-changelog.md`), and filed **https://github.com/daBOB/mediagram/issues/1** (`needs-triage` — the label didn't exist yet in the tracker despite being the documented convention in `docs/agents/triage-labels.md`, so created it first). Referenced `daBOB/mediagram#1` in the changelog line and in code.
- **L1**: `resolve_with` now calls `poster_key_is_valid` first, matching its own doc's claim that every lookup sits behind it (previously true only of `poster_path`).
- **L2**: `seasonPlatesOf` now gates on `division.season` itself (`division.season?.let { items.firstNotNullOfOrNull { ... } } }`), not just on whether an episode happens to carry a resolved `seasonPosterPath`. Strengthened `SeasonWallTest.kt`'s unnumbered-division case to give the division's episode a stray non-null `seasonPosterPath` and assert the plate still gets none — this fails without the guard, where the previous version (all-null test data) could not.
- **L3**: `one_set_lookup_finds_the_same_resolved_row_a_listing_would` now seeds real genres/subtitles/a materialized backdrop and asserts the *whole* `SetSummary` from `media_set` equals the listing's row (`assert_eq!(&found, from_listing)`), not just `set_id`. Added a season-poster-materializes-from-the-table test. Added present-portrait tests for `person()` and `search_people()` (previously only `title_credits` had one).
- **L4**: `run_title_credits`/`run_person`/`run_search_people` each build one `Portraits` helper (the connection they already opened + one `artwork::keys` read) and resolve every cast/crew/hit member's portrait through it via `store::resolve_with` — no more per-person reopen of `library.db` through `store::poster_path`'s own miss-triggered `open()`.
- **L5**: corrected every doc pointed at — `editorial.rs`'s module doc (describes the key-set + memo, not the old boolean), `summary.rs:33`'s `poster_path` doc (per-key once, not "once per listing" read as one value for the whole listing), `project-changelog.md` (baseline corrected to the true 107–251 ms range; the artwork-table line rewritten to describe the key set), `system-architecture.md` (rewritten around the shared `resolve_with`, credits now genuinely going through it too).
- **L6**: `write_from_conn` now writes to a uniquely-named temp file (pid + a process-wide atomic counter) beside the destination, then `rename`s into place — `media_set` and a listing materializing the same key at once, each on its own connection, can no longer race a reader into seeing a half-written file, and two writers can no longer share one temp path.

No `#[uniffi::export]` signature or `#[derive(uniffi::Record)]` field changed in this round (only internal `resolve.rs`/`editorial.rs`/`credits.rs`/`artwork.rs` implementation) — **bindings not regenerated**, confirmed via `git diff` on `mediagram_core.kt` being unchanged since the last regeneration.

**Counts after this round:**
- `cargo test -p mediagram-core -q`: **511 → 518 passed** (+7: `artwork.rs` +2 `keys()` tests, `editorial_tests.rs` +2 [absent-key-not-queried, shared-key-queried-once], `credits_tests.rs` +3 [person/search-people present-portrait, L3's franchise/backdrop-richer listing test replaced the old thin one]), 0 failed, 1 ignored.
- `cargo test --workspace -q`: green. `cargo clippy -p mediagram-core --all-targets`: clean.
- Android `testDebugUnitTest`: **1477 → 1477** (no net change — `SeasonWallTest`'s guard test was strengthened in place, not added).
- Full requested check (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): `BUILD SUCCESSFUL`.

**Files touched this round** (beyond the phase 01-03 set): `crates/mediagram-core/src/artwork.rs`, `api/store/resolve.rs`, `api/store/editorial.rs`, `api/store/editorial_tests.rs`, `api/credits.rs`, `api/credits_tests.rs`, `dto/summary.rs`, `docs/project-changelog.md`, `docs/system-architecture.md` (Rust-side only); `android/feature/catalog/src/main/kotlin/SeasonWall.kt` and `.../test/kotlin/SeasonWallTest.kt` (the one Kotlin change, L2).

**Status:** DONE

## Unresolved questions

None. M3's disclosed trade-off (backdrop disk-only vs. the web's table-aware `has()`) is now tracked at https://github.com/daBOB/mediagram/issues/1 rather than left as a silent gap.
