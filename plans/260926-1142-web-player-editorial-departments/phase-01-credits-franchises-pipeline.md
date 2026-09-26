# Phase 1 — Credits, franchises, series type (Rust, schema v9)

**Priority:** P1. **Status:** pending. It runs in parallel with phases 3–4.

## Context
- `crates/mediagram-tmdb/src/details.rs:23` (append_to_response), `tmdb_types.rs`
- `crates/mlib-spec/src/schema.rs` (V8 → V9, `READABLE_SCHEMAS`)
- `crates/mediagram-core/src/shows/{mod,facts}.rs` (writers of `shows`)
- `crates/mediagram/src/index/merge_columns.rs` (the channel merge must carry the new columns)
- `crates/mediagram-core/src/package/reader.rs`, `web/src/catalog.ts` (reader gates)
- `crates/mediagram-tmdb/src/posters.rs` (keyed image files)

## Key insights
- Every addition must be optional to readers, as v7 and v8 were. A v8 reader of a v9
  snapshot must keep working, and so must a v9 reader of a v8 snapshot.
- The disk cache never expires, so cached details lack credits. The backfill must
  re-request details with credits and not reuse the cached body.

## Requirements
- V9: `shows` gains `collection_id INTEGER`, `collection_name TEXT` (movie franchise)
  and `series_type TEXT` (TMDB tv `type`: Miniseries, Scripted…). A new table:
  `credits(source, kind, id, ord, person_id, name, role, dept, PRIMARY KEY(source,kind,id,ord))`.
  It stores the top 12 cast (`dept='cast'`, `role`=character) plus directors and
  creators (`dept='crew'`, `role`='Director'/'Creator').
- Portraits: `tmdb-person-{id}.jpg` (w185) beside the posters, fetched by the same
  `mediagram posters` pass. Backdrops are excluded from the export package; portraits
  are excluded the same way.
- `READABLE_SCHEMAS = [6,7,8,9]`. The web `OLDEST_READABLE_SCHEMA` stays at 6.

## Steps
1. Add `credits` to `append_to_response`. Add the types for credits and `belongs_to_collection`.
2. Add the V9 group and bump the constants. Update `schema_tests`, `snapshot_open_accepts_older_schema`
   and `package_format`.
3. Write the new columns and credits rows where `shows` is written. Carry them in `merge_columns.rs`.
4. `mediagram metadata --refresh-credits` (or the existing flag, if there is one): re-fetch details
   for titles without credits rows.
5. Portrait resolution in `posters.rs`, deduplicated by person id across titles.

## Todo
- [ ] types + fetch  - [ ] V9 + gates  - [ ] write + merge  - [ ] backfill  - [ ] portraits  - [ ] tests

## Success criteria
`cargo test` is green. Against a scratch copy of `library.db`, a v9 migration and
backfill give non-empty credits for most films, and the Star Trek films share one
`collection_id`. The v8 fixture still opens.

## Risks
- A TMDB rate limit on the backfill (~1,000 calls). Throttle as the existing fetch does.
- Two uploaders (memory: two-uploaders-share-the-channel-index). **Both machines need
  a v9 build before either pushes.** Otherwise a v8 push drops the credits. This goes
  into the handoff.

## Security
The TMDB key is used only in the existing client. Credits are public data.
