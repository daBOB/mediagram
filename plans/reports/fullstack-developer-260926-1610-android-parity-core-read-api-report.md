# Phase 1 — Core read API (Rust, UniFFI) report

Plan: `plans/260926-1330-android-editorial-departments-parity/phase-01-core-read-api.md`
Worktree: `feat/android-editorial-parity`, work context `/home/andre/Workspace/mediagram-android-parity`

## API added (names phase 2 builds on)

### UniFFI (`Core`, async)
- `title_credits(key: String) -> TitleCreditsRecord` — cast/crew for a poster key.
- `person(person_id: u64) -> Option<PersonRecord>`
- `franchises() -> Vec<FranchiseRecord>` — alphabetical.
- `search_people(query: String) -> Vec<PeopleHitRecord>` — umlaut-tolerant, most-credited first, capped at 12.
- `fetch_portrait(person_id: u64) -> Option<String>` — lazy w185 download, returns the file path; idempotent.

### DTOs (`mediagram_core::dto`, `crates/mediagram-core/src/dto/credits.rs`)
- `CreditRecord { person_id: u64, name: String, role: Option<String>, portrait_key: Option<String> }`
- `TitleCreditsRecord { cast: Vec<CreditRecord>, crew: Vec<CreditRecord> }` (derives `Default`)
- `PersonRecord { person_id: u64, name: String, portrait_key: Option<String>, title_keys: Vec<String> }`
- `FranchiseRecord { id: u64, name: String, overview: Option<String> }`
- `PeopleHitRecord { person_id: u64, name: String, portrait_key: Option<String>, title_keys: Vec<String> }`

`portrait_key` is the key (`tmdb-person-<id>`), present only when the file already exists on
this device — the same rule `SetSummary::backdrop_key` follows; Kotlin resolves it to a path
via the existing `poster_path`. Kotlin field names (verified against phase 2's already-landed
`PersonCandidate`): `personId`, `name`, `portraitKey`, `titleKeys` — matches exactly.

### `SetSummary` (`dto/summary.rs`) gained
`show_status: Option<String>`, `collection_id: Option<u64>`, `collection_name: Option<String>`,
`series_type: Option<String>` — attached in `store::list_sets` from an extended
`shows::ShowFacts` (now also carrying these four), by poster key, exactly like `tagline`/
`rating`/`popularity`. `None` throughout for a v8 index (the three v9 columns) — `show_status`
reads from `shows.status`, present since the base table (v5), so it is never gated.

### Rust read modules
- `credits::for_title(conn, kind, id) -> TitleCredits { cast, crew }` — billing order, in
  `crates/mediagram-core/src/credits_read.rs` (declared as `credits::read` via `#[path]`,
  re-exported from `credits.rs`, mirroring the flat-sibling-file pattern `catalog_assets.rs`
  already uses).
- `credits::for_person(conn, person_id) -> Option<PersonCredits { name, title_keys }>`
- `credits::people_matching(conn, query) -> Vec<PeopleHit>` — ported from
  `web/src/catalog/credits.ts#peopleSearch`: `search::normalize::{terms, variants}` for the
  umlaut-tolerant match, `search::rank::collator()` for the German tie-break, limit 12.
- `credits::profile_of(conn, person_id) -> Option<String>` — one person's profile path, for
  `fetch_portrait`.
- `franchises::all(conn) -> Vec<Franchise>` — alphabetical, tolerant of a missing table.

All four (`for_title`/`for_person`/`people_matching`/`franchises::all`) answer empty/`None`
for an index predating the table (v8) rather than erroring — same accommodation
`shows::optional_column` and `credits::portraits` already make. None of them merge with the
device's fetched sidecar: nothing populates that copy's `credits`/`franchises` tables today
(no fetch path writes them), so doing so would only ever read empty — documented in
`credits_read.rs`'s module doc rather than built speculatively.

`fetch_portrait` *does* check the sidecar as a fallback (`profile_of` in `api/credits.rs`),
since the phase's own bullet 4 names that order explicitly and the cost is a few lines,
reusing `enrich::details::open_fetched_ro`.

## Files

### Modified
- `crates/mediagram-core/src/shows/facts.rs` — `ShowFacts` +4 fields, `facts()` query, 2 tests.
- `crates/mediagram-core/src/dto/summary.rs` — `SetSummary` +4 fields, `summary_from` defaults.
- `crates/mediagram-core/src/api/store/editorial.rs` — attaches the 4 new fields in `list_sets`.
- `crates/mediagram-core/src/franchises.rs` — `all()` + 2 tests.
- `crates/mediagram-core/src/credits.rs` — `mod read` wiring, `profile_of`, `portraits()` now
  shares `read::has_table`.
- `crates/mediagram-core/src/dto.rs` — registers `dto::credits`.
- `crates/mediagram-core/src/api/mod.rs` — registers `mod credits;` (trimmed one doc line to
  stay at the 200-line cap).
- `crates/mediagram-core/tests/index_extras_in_catalog.rs` — 2 tests (v9 status/franchise, v8 empty).
- `crates/mediagram-core/tests/api_surface.rs` — 1 test (fresh-install departments surface is empty).
- `crates/mediagram-core/tests/dto_mapping.rs` — 1 test (flattening alone never fills franchise/status).
- `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` — regenerated.
- `android/core/data/src/test/kotlin/FakeCore.kt` — the raw `SetSummary(...)` test-fixture
  constructor needed the 4 new required params; added with `null` defaults on the `summary()`
  builder (in-scope per the phase's "only if you need to keep the build compiling" carve-out —
  this broke `core:data`'s unit test compile once the bindings regenerated, unrelated to
  phase 2's own `MediaSet` change).

### Created
- `crates/mediagram-core/src/credits_read.rs` (+ `credits_read_tests.rs`)
- `crates/mediagram-core/src/dto/credits.rs`
- `crates/mediagram-core/src/api/credits.rs` (+ `api/credits_tests.rs`)

Every touched/created `src/` file is under the 200-line cap (checked via
`tests/code_standards.rs`, which also runs in CI).

## Commands run and results

- `cargo build -p mediagram-core` — clean.
- `cargo test --workspace` — **all green** (376 lib tests in `mediagram-core` alone; no
  `FAILED` anywhere in the full run, grepped).
- `cargo clippy --workspace --all-targets -- -D warnings` — clean.
- `ANDROID_NDK_HOME=.../28.2.13676358 scripts/generate-android-bindings.sh` — cross-compiled
  all 4 ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`) and regenerated
  `mediagram_core.kt`. `jniLibs/` is gitignored build output, as expected.
- `cd android && ./gradlew testDebugUnitTest` — **BUILD SUCCESSFUL**, all modules (after the
  `FakeCore.kt` fix above), including `feature:catalog`'s phase-2 tests and `core:data`.

## Interaction with phase 2 (already landed in this shared worktree)

A `fullstack-developer` session had already completed phase 2's pure-rule half in this same
worktree (`plans/reports/fullstack-developer-260926-1612-android-parity-pure-rules-report.md`,
`android/core/model/MediaSet.kt` + 5 new `feature/catalog` files) before I started. No file
overlap with my ownership (`crates/mediagram-core/**`, `android/core/rust/**`,
`android/core/data/**`) — confirmed by `git status` before touching anything. Their
`PersonCandidate(personId, name, portraitKey, titleKeys)` field names match my
`PersonRecord`/`PeopleHitRecord` exactly (UniFFI's camelCase conversion), so phase 2's rules
should wire against this phase's DTOs with no renaming.

## Left for later phases

- Nothing device-side writes `credits`/`franchises` into the per-device fetched sidecar
  (`details.db`) — if a future phase wants those departments backfilled by the phone's own
  TMDB fetch rather than only from a channel snapshot, that's a new `fetch_missing` extension,
  out of this phase's scope.
- `android/core/data/CoreClient`/`DefaultCoreClient` were **not** touched — the raw UniFFI
  `Core` object already exposes the five new methods directly; nothing in the production
  Kotlin surface calls them yet (that's phase 2's remaining view-model half / phase 4–6), so
  there was nothing to keep compiling there.

**Status:** DONE
**Summary:** Rust core + UniFFI surface for editorial departments (credits, people, franchises,
lazy portraits) implemented, tested (workspace `cargo test`/`clippy` clean), bindings
regenerated and cross-compiled for all 4 Android ABIs, and the whole Android app's
`testDebugUnitTest` verified green against them.
**Concerns/Blockers:** none.
