# Phase 1 — Backdrop pipeline

Priority: high (blocks the cover story). Status: done 2026-09-25 (pending: push v8 index, install new binary — user).

## Context
- Explorer map: poster flow TMDB → `resolve_posters` → `download_into` → `posters/<key>.jpg`
  → `PosterStore` → `/api/posters/<key>.jpg` → `forBrowser` adds `poster`.
- Cache: 842/844 cached details carry `backdrop_path`. The payload is localized
  (`de-DE`), so a few backdrops may carry German text. Accepted, because it needs no
  new API calls.

## Requirements
- Movies and shows get `tmdb-{kind}-{id}-bg.jpg` at **w1280** (hero width ~1440 CSS px;
  w1280 ≈ 150–250 KB).
- The existing poster behaviour is unchanged. A missing backdrop is not an error.
- The web catalog row gains `backdrop: string|null`, the key only when the file exists,
  exactly like `poster`.
- Not added to the encrypted export package (size budget `export/budget.rs:9`).
- Android's on-device fetch (`enrich/fetch.rs`) skips `-bg` for now. See the plan's
  open question.

## Files
- `crates/mediagram-tmdb/src/tmdb_types.rs`: `backdrop_path: Option<String>` on `DetailsResponse`.
- `crates/mediagram-tmdb/src/posters.rs`: `PosterRef` gains a `size`; emit a backdrop
  ref; `backdrop_key()`.
- `crates/mediagram-tmdb/src/poster_files.rs`: URL from the ref's size; raise the cap
  if 4 MB is too tight (it should not be).
- `crates/mlib-spec/src/package/mod.rs:127`: accept the `-bg` suffix in key validation.
- `crates/mediagram/src/export/stage.rs`: filter `-bg` out of the package.
- `crates/mediagram-core/src/api/enrich/fetch.rs`: skip backdrop refs on device.
- `web/src/package/posters.ts`: `KEY` regex plus `backdropKeyFor`.
- `web/src/catalog/artwork-routes.ts`: path regex.
- `web/src/catalog/routes.ts`: `backdrop` in `forBrowser`.
- `web/public/lib/library.d.ts`: `backdrop: string | null`.

## Steps
1. TDD: extend `crates/mediagram/tests/export_posters.rs`-style tests for the backdrop
   URL/key, the key validation, and the package exclusion.
2. Implement the Rust side, then `cargo test -p mediagram-tmdb -p mlib-spec -p mediagram`.
3. Web: a test that `forBrowser` sets `backdrop` only when the file exists; extend the
   artwork route test.
4. Backfill locally: `mediagram posters` (with `--index` for the pushed snapshot). Check
   the `-bg.jpg` count ≈ 840. **Does not touch the running player or uploads.**

## Success
- About 840 backdrops on disk; `/api/posters/tmdb-movie-X-bg.jpg` serves; the catalog
  rows carry `backdrop`.
- All existing Rust and web tests pass.

## Risks
- Localized backdrops with burned-in text. Mitigation: the hero scrim sits over the
  left third; revisit with `include_image_language=null` only if it looks bad.
- Another machine's uploader runs an older binary. Harmless: it simply fetches no
  backdrops.

## 1b — Store TMDB popularity (for TRENDING)
- `tmdb_types.rs`: `popularity: Option<f64>` on `DetailsResponse`.
- `crates/mlib-spec/src/schema.rs`: **schema v8** adds `shows.popularity REAL`
  (pre-release bumps are fine). Keep `OLDEST_READABLE_SCHEMA` readable, or bump it
  consciously; check the other machine's uploader (memory:
  two-uploaders-share-the-channel-index).
- `mediagram-core/src/shows/mod.rs` upsert, and the `dto.rs` field.
- Backfill: `mediagram metadata` re-reads the never-expiring cache, so it makes no
  API calls.
- Web: `forBrowser` exposes `rating`, `popularity` and `tagline` for movies (joins
  `shows`), so home needs no request per title.
- Popularity is a snapshot from when the cache was filled, not live. The eyebrow
  wording stays honest ("on TMDB"); refreshing it is a later concern.

## Outcome (2026-09-25)

- Design change from the steps above: `export/stage.rs` and `enrich/fetch.rs` were **not
  touched**. The separate `resolve_backdrops` is called only by `commands/posters.rs`,
  so the package and the phone leave backdrops out by construction, and a test pins
  it (`resolving_posters_never_yields_a_backdrop`). The width comes from the key
  through `PosterRef::url()`; no new struct field.
- Key rules moved to `mlib-spec/src/package/artwork_key.rs` (for the line limit);
  schema tests to `schema_tests.rs`.
- **Bug found and fixed:** the package readers' schema lists were `[oldest, current]`,
  so v8 would have refused v7 packages. Now `READABLE_SCHEMAS` covers the full range
  (Rust and web), with tests.
- Local index backed up to `library.before-popularity-260925.db`, migrated to v8, and
  `metadata` backfilled: 587/587 popularity. Taglines, ratings and certifications are
  unchanged against the backup.
- Tests: Rust 1111 pass, clippy clean; web 1817 pass, lint clean. The one pre-existing
  `tsc` error in `test/search-shared-fixtures.test.ts` is untouched.
- The live player (bun --watch, PID 558266) reloaded on the `web/src` edits and serves
  the new fields. `/api/sets` was checked read-only.
- Not yet: the version bump happens at commit time (§ Versioning).
- Backdrops: `posters` on the local index fetched 586, then `posters --index <channel
  snapshot>` fetched 251 more for the other machine's titles. The live player shows
  **829/831 films and 163/163 episodes** with a backdrop; 137 MB on disk.
- **Popularity is not live yet.** The player reads the channel snapshot
  (`~/.cache/mediagram-channel-index/v-1790361175`, v7), not the local v8 index. It
  appears once a v8 index is pushed (`mediagram push-index`, outward-facing, so the
  user's call). Until then Trending must fall back gracefully (phase 3). The other
  machine's pushes carry no popularity until it runs a v8 build.
- The player's automatic snapshot hook runs the **installed** `mediagram`
  (`~/.cargo/bin`, old), so new titles get no backdrop until the new binary is
  installed.

## Review (code-reviewer, 2026-09-25): no critical or high findings
- Fixed: the poster count included backdrops (L2); version bumped to 0.55.0 (M2).
- **Ordering (M1):** install the new player and phone builds **before** the first
  `export-package --publish` from a v8 index. Installed readers carry `[6,7]` and would
  refuse a v8 package. The channel path is unaffected.
- Deferred, pre-existing (L1): a downgrade then upgrade of the phone app can record
  sidecar v7 over a v8 store, and the next open re-runs `ADD COLUMN`. Fix later: never
  lower a recorded version.
- Accepted (L3): three file checks per catalog row; cheap.
