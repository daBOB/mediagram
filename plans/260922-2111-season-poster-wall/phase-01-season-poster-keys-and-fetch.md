# Phase 1 — Season poster keys and fetch (Rust)

**Priority:** first; phases 2–3 depend on it. **Status:** pending

## Changes
- `crates/mlib-spec/src/package/mod.rs` `poster_key_is_valid`: allow optional
  4th part `s<digits>` (only with kind `tv`). Add `season_poster_key(id, n)` helper.
  Tests: accepts `tmdb-tv-1-s2`; rejects `tmdb-movie-1-s2`, `tmdb-tv-1-s`, `tmdb-tv-1-x2`, 5 parts.
- `crates/mediagram-tmdb/src/tmdb_types.rs`: `DetailsResponse.seasons: Vec<SeasonRef>`
  (`season_number`, `poster_path`), `#[serde(default)]`.
- `crates/mediagram-tmdb/src/posters.rs` `resolve_posters`: for `tv` titles also
  push a `PosterRef` per season with a valid `poster_path` (same `is_image_path`
  guard). Same cached details call — no extra request.
- `crates/mediagram-core/src/api/catalog.rs` `count_posters`: fine as is
  (counts files); check the status line doesn't mislabel season posters as titles.
- Export stage (`export/stage.rs`) copies whatever `download_into` wrote — confirm
  season files ride along; extend `tests/export_posters.rs`.

## Todo
- [ ] spec validator + helper + tests
- [ ] types + resolve_posters + test against tv fixture with seasons
- [ ] export test covers a season poster
- [ ] `cargo test --workspace`, `cargo clippy`

## Risk
- Package readers built before this reject the new key → they just skip that
  poster (cosmetic). Acceptable pre-release.
