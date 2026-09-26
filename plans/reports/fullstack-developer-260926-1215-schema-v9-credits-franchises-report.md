# Schema v9 — credits, franchises, series type, cast portraits (Rust)

Plan: `plans/260926-1142-web-player-editorial-departments/phase-01-credits-franchises-pipeline.md`
Branch: `feat/editorial-departments`, worktree `/home/andre/Workspace/mediagram-editorial`

## Schema v9

`crates/mlib-spec/src/schema.rs` (`SCHEMA_VERSION = 9`, `READABLE_SCHEMAS = [6,7,8,9]`,
`OLDEST_READABLE_SCHEMA` stays 6). New `V9` group:

- `ALTER TABLE shows ADD COLUMN collection_id INTEGER`
- `ALTER TABLE shows ADD COLUMN collection_name TEXT`
- `ALTER TABLE shows ADD COLUMN series_type TEXT`
- `CREATE TABLE credits(source TEXT NOT NULL, kind TEXT NOT NULL, id INTEGER NOT NULL, ord INTEGER NOT NULL, person_id INTEGER NOT NULL, name TEXT NOT NULL, role TEXT, dept TEXT NOT NULL, profile TEXT, PRIMARY KEY(source, kind, id, ord))`
  — `profile` added per the lead's mid-task spec correction (bare TMDB `profile_path`,
  e.g. `/abc.jpg`, NULL when none), so a device with no TMDB cache of its own can still
  resolve a portrait from a channel snapshot.
- `CREATE TABLE franchises(source TEXT NOT NULL, id INTEGER NOT NULL, name TEXT NOT NULL, overview TEXT, PRIMARY KEY(source, id))`

`V1`–`V6` moved out of `schema.rs` into `crates/mlib-spec/src/schema_versions.rs` (sibling
`#[path]` module, `pub(super)` consts) — the 200-line ratchet in
`crates/mediagram/tests/code_standards.rs` would otherwise fail on `schema.rs` alone.

## Files changed

**New (Rust):**
- `crates/mediagram-tmdb/src/credits.rs` (+ `credits_tests.rs`) — `/movie|tv/{id}/credits`,
  own cache entry, empty query. Top 12 cast by `order`, crew filtered to `job="Director"`,
  plus (series) creators from the details payload's `created_by` (already cached, no
  second request). `ord` is sequential across cast → director(s) → creators.
- `crates/mediagram-tmdb/src/franchise.rs` — `/collection/{id}`, own cache entry, empty
  overview → `None`.
- `crates/mediagram-core/src/credits.rs` (+ `credits_tests.rs`) — `upsert` (delete-then-insert
  whole title), `has` (backfill skip check), `portraits` (reads `credits.profile` from
  whatever connection it's given — local index or a `--index` snapshot — tolerating a
  v8 connection with no `credits` table at all).
- `crates/mediagram-core/src/franchises.rs` — `upsert`/`get`/`has`.
- `crates/mediagram-core/src/shows/poster_key.rs` — `title_of`/`read` moved out of
  `shows/mod.rs` (line-limit ratchet).
- `crates/mediagram/src/index/merge_credits.rs` — channel-merge for the two new tables.
- `crates/mlib-spec/src/schema_versions.rs` — `V1`–`V6` DDL, split out (see above).

**Edited:**
- `crates/mediagram-tmdb/src/tmdb_types.rs` — `DetailsResponse` gains
  `belongs_to_collection: Option<CollectionRef>`, `series_type: Option<String>`
  (`#[serde(rename = "type")]`), `created_by: Vec<CreatedBy>`. All three are already in
  the existing `append_to_response=external_ids` payload; the query is untouched.
- `crates/mediagram-tmdb/src/details.rs` — `TitleDetailsRow` gains `collection_id`,
  `collection_name`, `series_type`; `from_details` fills them.
- `crates/mediagram-tmdb/src/posters.rs` — `PORTRAIT_WIDTH = 185` const; `is_image_path`
  made `pub` (used by `mediagram_core::credits::portraits` to validate a `profile` path
  read out of a possibly-foreign snapshot before it reaches a URL).
- `crates/mediagram-tmdb/Cargo.toml` — `tokio` features gain `macros`, `rt` (first async
  tests in this crate).
- `crates/mediagram-core/src/shows/mod.rs` — `upsert`/`get` carry the 3 new columns;
  `get` uses `optional_column` for them (v8 reader compat).
- `crates/mediagram-core/src/lib.rs`, `crates/mediagram-tmdb/src/lib.rs` — register the
  new modules.
- `crates/mediagram/src/metadata/title_details.rs` — `backfill_credits`,
  `backfill_franchise`: skip when already recorded, log-and-continue on TMDB failure
  (never fails the title's own description).
- `crates/mediagram/src/commands/metadata.rs` — calls the two backfills per title after
  `shows::upsert`; prints counts.
- `crates/mediagram/src/commands/posters.rs` — extends the fetched refs with
  `mediagram_core::credits::portraits(&conn)` (DB-sourced, not a TMDB call — see below);
  same summary line, portraits counted in the same total.
- `crates/mediagram/src/index/{merge.rs,mod.rs}`, `crates/mediagram/src/commands/pull_index/mod.rs`
  — `MergeReport` gains `credits_added`/`franchises_added`; printed when non-zero.
- Test files updated for the 3 new `TitleDetailsRow` fields: `shows_upsert_covers_schema.rs`,
  `shows_query.rs`, `index_extras_fetched_genres.rs`, `sidecar_tests.rs`, `details_tests.rs`,
  `index_shows.rs`. `merge_tests.rs` gained 3 tests for the new tables incl. a v8-channel
  tolerance case.
- `docs/mlib-package-v1.md` — embedded JSON examples' `"schema":8` → `9`
  (`package_spec_examples.rs` asserts these verbatim).
- `web/src/catalog.ts` — `EXPECTED_SCHEMA = 9`, `OLDEST_READABLE_SCHEMA` doc comment
  updated (`READABLE_SCHEMAS` is derived, no change needed). `open-catalog.ts` only
  imports `READABLE_SCHEMAS`, needed no edit.

## Portrait pipeline — revised mid-task per the lead

Original plan had `mediagram posters` fetch portraits from TMDB directly. The lead
(owning the web reader side) asked that portraits instead be sourced from
`credits.profile` in the index being read, because a machine running
`mediagram posters --index <channel snapshot>` has no TMDB credits cache of its own.
Implemented: `mediagram_core::credits::portraits(conn)` reads `person_id, MIN(profile)`
grouped from the `credits` table (empty for a v8 connection), validates each path with
`is_image_path`, and yields `PosterRef{key: "tmdb-person-<id>", path, backdrop_width:
Some(185)}`. `mediagram posters` extends its poster/backdrop refs with these; no second
TMDB round trip. Portraits are downloaded to `<key>.jpg` by the existing
`poster_files::download_into`, same as posters/backdrops.

Export exclusion: `export_package.rs`'s `fetch_posters` only calls `resolve_posters`
(never backdrops or portraits) — already the mechanism that keeps backdrops out of the
encrypted package; portraits are excluded the same way, by never being fetched there.
The Android on-device fetch (`mediagram-core/src/api/enrich/fetch.rs`) was not touched,
so it does not fetch portraits either.

## Commands run

```
cargo check --workspace
cargo test --workspace          # 104 test binaries, 0 failed
cargo clippy --workspace --all-targets   # clean, no warnings
cargo fmt -- --check <touched files>     # clean
```

`cargo fmt` note: an early invocation with an empty file array silently fell back to
formatting the *entire* workspace (cargo-fmt resolves named files to their crate and
reformats the whole crate tree, not just the named files). This surfaced ~37 files
under `mediagram-core` (api/, search/, state/) that were already not rustfmt-clean at
HEAD — pre-existing drift, unrelated to this phase. All 37 were reverted with
`git checkout --`; verified against the initial `git status` (which showed a clean
`crates/` tree before this session) that none of it was someone else's in-flight work.
Final diff is exactly the 30 files listed above.

## End-to-end (scratch copy of `library.db`, no Telegram)

Copied `~/.local/share/mediagram/library.db` (+ `-wal`/`-shm`) and `tmdb-cache` to a
scratch dir; ran `MEDIAGRAM_DATA_DIR=<scratch> cargo run -p mediagram -- metadata` and
`... -- posters`. Real `library.db` mtime unchanged throughout (verified before/after);
only the scratch copy's `tmdb-cache` gained entries.

- Schema migrated 8 → 9 on first open.
- `908 title(s) described, 914 held in total`
- `908 title(s) got a cast and crew list` (100% of titles that got a description also
  got credits — `credits` table: 11,706 rows across 908 titles, ~12.9 rows/title)
- `202 franchise(s) recorded`; 363 `shows` rows carry a `collection_id`
- **Star Trek**: exactly 11 "Star Trek" movies in the library. TMDB does not group them
  under one collection — it splits into its own 3 real sub-franchises, and the pipeline
  reproduced that split correctly: 6 films → `collection_id 151` ("Raumschiff
  Enterprise" / TOS films I–VI), 3 films → `115570` ("Das nächste Jahrhundert" / TNG
  films), 1 film → `115575` ("Kelvin-Zeitachse" / 2009 reboot). The 11th, *Star Trek:
  Sektion 31* (a standalone TV movie, id 1114894), correctly got no `collection_id` —
  TMDB itself does not put it in any film collection.
- `mediagram posters`: `8719 image(s) fetched, 0 already held`, including 6,886
  `tmdb-person-<id>.jpg` files at 185×278px (confirmed via `file`) — matches the 6,886
  distinct `person_id`s with a non-null `profile` in `credits` (11,119 of 11,706 rows
  have one).

## Left for the web side

- Table: `credits(source, kind, id, ord, person_id, name, role, dept, profile)`. `dept`
  is `'cast'` or `'crew'`; `role` is the character for cast, `'Director'`/`'Creator'`
  for crew; `ord` is the display order within a title (cast first, by billing, then
  director(s), then series creators) and is part of the primary key.
- Table: `franchises(source, id, name, overview)` — `id` is TMDB's collection id, the
  same value as `shows.collection_id`.
- `shows` gains `collection_id INTEGER`, `collection_name TEXT`, `series_type TEXT`
  (TMDB tv `type`: `Scripted`, `Miniseries`, `Documentary`, `Reality`, `News`,
  `Talk Show`, `Video` — absent for a movie).
- Portrait key format: `tmdb-person-<id>` (matches `poster_key_is_valid`'s
  `source-kind-digits` shape — `person` is just another `kind` string). File name
  `tmdb-person-<id>.jpg`, 185px wide. `credits.profile` (when present) is the same bare
  TMDB path (`/abc.jpg`) a poster/backdrop path would be — build the CDN URL the same
  way (`https://image.tmdb.org/t/p/w185<profile>`), or read the local file the
  `mediagram posters` pass already wrote.
- `EXPECTED_SCHEMA` in `web/src/catalog.ts` is now `9`; `OLDEST_READABLE_SCHEMA` (6) and
  `READABLE_SCHEMAS` are unchanged in shape (still derived from the two constants).
- **Both uploading machines need a v9 build before either pushes** (per the phase file's
  own risk note) — a v8 push would silently drop credits/franchises from the channel
  index until the merge in a later v9 pull recovers them from whichever side pushes v9
  first.

## Unresolved questions

- None blocking. One judgment call worth flagging: `series_type` is populated for only
  6 of 908 titles in the real library, because the library is overwhelmingly films —
  this is expected, not a bug (TMDB's tv `type` is a tv-only field).

**Status:** DONE
**Summary:** Schema v9 (shows columns + `credits`/`franchises` tables incl. lead's
`credits.profile` addition) implemented, backfilled by `mediagram metadata`, portraits
resolved from the index (not TMDB) by `mediagram posters`, web reader version gate
bumped. `cargo test`/`clippy --workspace` clean; e2e on a scratch copy of the real
library.db (1249 sets, no Telegram touched) credited 908/908 described titles, recorded
202 franchises, and correctly reproduced TMDB's real 3-way Star Trek film split.
**Concerns:** An early `cargo fmt` misfire reformatted ~37 unrelated pre-existing-dirty
files in `mediagram-core`; all reverted and verified clean against the session's initial
git status. Worth a note to whoever formats this crate next: `cargo fmt -- <files>`
reformats those files' *whole containing crate*, not just the named files.
