# Phase 1 implementation report — index: original language, anime overrides, edit --anime

Branch `feat/anime-index` off `main`. Steps 1–9 of
`phase-01-index-language-and-anime-overrides.md` only; step 10 (operator
backfill/publish) not run, per the hard limits.

## Files changed

**Schema**
- `crates/mlib-spec/src/schema.rs` — `V11` (`shows.original_language`,
  `anime_overrides` table); `SCHEMA_VERSION` 10→11; `READABLE_SCHEMAS`,
  `GROUPS` extended; doc comments updated.
- `web/src/catalog.ts` — `EXPECTED_SCHEMA = 11`, doc comment.

**TMDB**
- `crates/mediagram-tmdb/src/tmdb_title_refs.rs` (new) — `CollectionRef`,
  `CreatedBy`, `SeasonRef` moved out of `tmdb_types.rs`, re-exported via
  `pub use`.
- `crates/mediagram-tmdb/src/tmdb_types.rs` — `DetailsResponse.original_language`;
  now 143 lines (was 200, would have been 203 with the new field).
- `crates/mediagram-tmdb/src/lib.rs` — `mod tmdb_title_refs;`.
- `crates/mediagram-tmdb/src/details.rs` — `TitleDetailsRow.original_language`;
  `from_details` fills it, blank → `None`.

**Writer**
- `crates/mediagram-core/src/shows/mod.rs` — `upsert`/`get` carry the column;
  185 lines (was 181).

**Overrides store, merge, edit**
- `crates/mediagram/src/index/anime_overrides.rs` (new) — `get`/`set`.
- `crates/mediagram/src/index/anime_overrides_tests.rs` (new).
- `crates/mediagram/src/index/merge_anime_overrides.rs` (new) — last-writer-wins merge.
- `crates/mediagram/src/index/merge.rs` — `MergeReport.anime_overrides_taken`,
  wired into `copy_kept`, module doc table.
- `crates/mediagram/src/index/merge_tests.rs` — 7 new cases (see below).
- `crates/mediagram/src/index/mod.rs` — `pub mod anime_overrides; mod merge_anime_overrides;`.
- `crates/mediagram/src/channel_index/report.rs` — prints override count.
- `crates/mediagram/src/edit/anime.rs` (new) — `AnimeChoice`, `target`, `run`.
- `crates/mediagram/src/edit/anime_tests.rs` (new).
- `crates/mediagram/src/edit/mod.rs` — `pub mod anime;`.
- `crates/mediagram/src/commands/args.rs` — `EditArgs.anime` with `conflicts_with_all`.
- `crates/mediagram/src/commands/edit.rs` — dispatch right after `get_set`.
- `crates/mediagram/src/cli_tests.rs` — parse/reject/conflict test for `--anime`.

**Test-literal fixes** (new required field on `TitleDetailsRow`)
- `crates/mediagram-core/src/api/enrich/details_tests.rs`
- `crates/mediagram-core/src/shows/sidecar_tests.rs`
- `crates/mediagram-core/tests/shows_query.rs`
- `crates/mediagram-core/tests/shows_upsert_covers_schema.rs`
- `crates/mediagram-core/tests/index_extras_fetched_genres.rs`
- `crates/mediagram/tests/index_shows.rs`
- `crates/mediagram-tmdb/tests/details.rs` — plus 2 new tests (language read, blank → None).

**Docs, changelog, versions**
- `docs/mlib-spec.md` — new "Schema v11 additions" §6 section.
- `docs/system-architecture.md` — §10.1 heading and bullets → v11.
- `docs/mlib-package-v1.md` — 3 example JSON blobs' `"schema"` 10→11
  (deviation, see below).
- `README.md` — `metadata`/`edit` command table rows.
- `docs/project-changelog.md` — new `0.76.0` top entry.
- `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`
  only) — `0.75.5` → `0.76.0`. `Cargo.lock` regenerated for the 5 workspace
  crates via `cargo check`; `cargo metadata --locked --offline` succeeds.

## Tests added

- `anime_overrides_tests.rs`: automatic default, set/get yes, auto clears but
  keeps the row, film/series share an id but hold separate overrides, setting
  again replaces rather than duplicates.
- `edit/anime_tests.rs`: writes a keyed override, `auto` clears and keeps the
  row, `--dry-run` writes nothing, no-TMDB-id refusal, tutorial-kind refusal,
  same-value-again refusal.
- `merge_tests.rs`: channel-only inserted / local-only kept, newer channel
  replaces local, older channel does not, newer channel `NULL` (clear)
  overwrites a local forced value, v10 channel (no table) merges as 0, a
  second merge reports 0, `original_language` fills across a `lang` mismatch
  (not `LANGUAGE_TEXT`).
- `cli_tests.rs`: `--anime yes|no|auto` parse, `--anime maybe` rejected,
  `--anime yes --title x` rejected as a conflict.
- `mediagram-tmdb/tests/details.rs`: `original_language` read, blank → `None`.

## Deviations from the phase file

- **`docs/mlib-package-v1.md` not named in the phase's file list**, but its
  three worked examples embed a literal `"schema":10` that
  `tests/package_spec_examples.rs` checks byte-for-byte against
  `SCHEMA_VERSION`; bumped to 11 or `cargo test --all` fails. Same category
  as the named doc updates, just not listed.
- **`tmdb_types.rs` line count**: the phase said the split leaves it "exactly
  200 lines"; it is 143 after the split plus the new field — the split
  removed more than it needed to reach 200, but it was cleaner to move all
  three ref structs together than to split only enough to hit a round number.
- Everything else in Related Code Files / Implementation Steps 1–9 matched
  the real code as found (line numbers for `shows/mod.rs`, `merge.rs`,
  `edit.rs`, `args.rs` were close enough to locate by content, not by exact
  line).

## check.sh result

Green: `cargo clippy --all-targets --all-features -- -D warnings`,
`cargo test --all` (all crates, 0 failures), `bun run lint` + `bun test`,
`gradle testDebugUnitTest :core:model:test lint
:ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`
(BUILD SUCCESSFUL, lint baseline unchanged — 14 warnings/1 error already
filtered by the existing baseline, no new ones).

## Left undone

- Step 10 (operator `pull-index` → `metadata` → `push-index` on the real
  data dir) — explicitly out of scope per the hard limits; the branch is not
  merged or pushed.
