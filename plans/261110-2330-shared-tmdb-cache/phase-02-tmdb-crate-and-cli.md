# Phase 02: the shared TMDB crate and the CLI

Spec: `docs/superpowers/specs/2026-11-10-shared-tmdb-cache-design.md`. Global Constraints and Review Focus: `plan.md`.

### Task 3: `HomeCache` client and `DiskCachedApi` through the server

**Files:**
- Create: `crates/mediagram-tmdb/src/home_cache.rs`, `crates/mediagram-tmdb/src/home_cache_tests.rs` (fake server: a tokio `TcpListener` answering canned HTTP, as the crate's existing tests do; record every request line, header and body)
- Modify: `crates/mediagram-tmdb/src/lib.rs` (`pub mod home_cache;`), `crates/mediagram-tmdb/src/disk_cache.rs` (`with_home_cache`), `crates/mediagram-tmdb/src/tmdb_client.rs` (`NoKey`)

**Interfaces:**
- Consumes: Task 2's routes; `mlib_spec::lan_signature::{sign, SCHEME}` (Task 1).
- Produces:
  - `pub struct HomeCache` (Send + Sync); `HomeCache::new(http: reqwest::Client, url: impl Into<String>, token: Option<String>) -> HomeCache`.
  - `async fn get(&self, path: &str) -> Option<Vec<u8>>` (2 s timeout; any failure starts the 30 s skip; non-2xx → `None`).
  - `async fn has(&self, path: &str) -> bool` (HEAD; false on failure or skip).
  - `async fn put(&self, path: &str, body: &[u8])` (no token, or skipping → no request; errors swallowed).
  - `pub fn tmdb_path(path: &str, query: &[(&str, String)]) -> String` → `"/v1/tmdb/{sha256_hex}"`, same sorting as `DiskCachedApi::cache_key`, but a `language` value reduced to its lowercase primary subtag (`de-DE` → `de`).
  - `pub fn art_path(size: &str, tmdb_path: &str) -> String` → `"/v1/art/{size}/{file name of tmdb_path}"`.
  - `DiskCachedApi::with_home_cache(self, home: Option<Arc<HomeCache>>) -> Self`.
  - `pub struct NoKey;` implementing `TmdbApi`, whose `get_json` always errs `"no TMDB key, and the home cache server does not hold this"`.

Behaviour of `DiskCachedApi::get_json` with a home cache: disk hit → if `!home.has(p)` then `home.put(p, bytes)`, return disk; disk miss → `home.get(p)` parsing as JSON → hit writes disk and returns; otherwise `inner.get_json` → write disk → `home.put` unless it is an empty search page.

- [ ] **Step 1: Write failing tests** in `home_cache_tests.rs`:
  - `a_server_hit_answers_without_asking_tmdb` (inner counts 0 calls; disk file written).
  - `a_server_miss_asks_tmdb_and_uploads_signed` (PUT path == `tmdb_path(..)`, header `Authorization: MGC1 {sign(token,"PUT",path,len,body)}`).
  - `a_disk_hit_uploads_only_what_the_server_lacks` (HEAD 404 → one PUT; HEAD 200 → no PUT).
  - `an_empty_search_page_is_not_uploaded`.
  - `de_and_de_DE_are_one_server_key` (`tmdb_path("/search/movie", [("query","x"),("language","de")]) == tmdb_path(.., "de-DE")`).
  - `invalid_json_from_the_server_is_a_miss` (server 200 `"<html>"` → inner called, disk holds the TMDB answer, not the html).
  - `an_unreachable_server_is_skipped_for_30_s` (closed port: first call reaches inner; a second call inside the window makes no connection attempt; uses an injected clock or `tokio::time::pause`).
  - `without_a_token_nothing_is_uploaded`.
  - `no_key_errs_on_every_request`.
  - `the_tmdb_key_never_reaches_the_server` (a `TmdbClient` with a v3 key behind the cache: no recorded request line, header or body to the fake server contains the key).
- [ ] **Step 2:** `cargo test -p mediagram-tmdb home_cache` → FAIL.
- [ ] **Step 3:** Implement per the interfaces (keep `home_cache.rs` < 200 lines; the disk-cache change stays in `disk_cache.rs`).
- [ ] **Step 4:** `cargo test -p mediagram-tmdb && cargo clippy -p mediagram-tmdb --all-targets -- -D warnings` → PASS.
- [ ] **Step 5:** Commit `feat(tmdb): TMDB answers come from and go to the home cache server`.

### Task 4: Images through the server in `poster_files`

**Files:**
- Modify: `crates/mediagram-tmdb/src/poster_files.rs` (`download_into`, `download_each` take `home: Option<&HomeCache>`), callers: `crates/mediagram-core/src/api/enrich/fetch.rs:76`, `crates/mediagram-core/src/api/credits.rs:66`, `crates/mediagram-core/src/game_posters.rs:84`, `crates/mediagram/src/commands/posters.rs:91`, `crates/mediagram/src/export/stage.rs:72` (passes `None`: an export is built for another house)
- Test: `crates/mediagram-tmdb/src/poster_files_tests.rs`

**Interfaces:**
- Consumes: `HomeCache::{get, has, put}`, `art_path` (Task 3); `PosterRef { key, path, width }` (existing; poster size is `w342`, backdrop size is `w{width}`).
- Produces: `download_into(http, refs, dir, home: Option<&HomeCache>) -> Result<Vec<String>>`; `download_each(http, refs, dir, home: Option<&HomeCache>, step) -> Result<Vec<String>>`.

Per ref: already on disk → upload the file if `!home.has(art)`; else `home.get(art)` → write; else CDN (keyless, as today) → write → `home.put(art, bytes)`.

- [ ] **Step 1: Write failing tests:** `a_held_image_on_the_server_is_written_without_the_cdn`, `a_cdn_image_is_uploaded_to_the_server`, `an_image_already_on_disk_is_uploaded_only_if_the_server_lacks_it`, `a_backdrop_is_keyed_by_its_width` (ref width 780 → path `/v1/art/w780/<name>`), `a_missing_width_on_the_server_is_fetched_from_the_cdn_at_this_width` (server has `w1280` only; ref asks `w780` → CDN request for w780, then PUT `w780`).
- [ ] **Step 2:** `cargo test -p mediagram-tmdb poster_files` → FAIL.
- [ ] **Step 3:** Implement; update the five callers (core/CLI callers pass `None` until Tasks 5 and 6 wire a real server; `stage.rs` keeps `None`).
- [ ] **Step 4:** `cargo test -p mediagram-tmdb -p mediagram-core -p mediagram` → PASS.
- [ ] **Step 5:** Commit `feat(tmdb): posters and backdrops come from and go to the home cache server`.

### Task 5: CLI `mediagram posters` reads the server from its config

**Files:**
- Modify: `crates/mediagram/src/config.rs` (fields `cache_server: Option<String>`, `cache_server_token: Option<String>`; `tmdb_client` adds `.with_home_cache(..)`; new `fn home_cache(&self, http: &reqwest::Client) -> Option<Arc<HomeCache>>`), `crates/mediagram/src/commands/posters.rs` (pass the home cache to `download_each`)
- Test: `crates/mediagram/src/config_tests.rs`

**Interfaces:**
- Consumes: `HomeCache::new`, `DiskCachedApi::with_home_cache` (Task 3), `download_each(.., home, ..)` (Task 4).
- Produces: config keys `cache_server = "http://127.0.0.1:7788"` and `cache_server_token = "<64 hex>"` (used by Task 7's script).

- [ ] **Step 1: Write failing tests:** `the_cache_server_keys_are_read`, `no_cache_server_means_no_home_cache`.
- [ ] **Step 2:** `cargo test -p mediagram config` → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** `cargo test -p mediagram && cargo clippy -p mediagram --all-targets -- -D warnings` → PASS.
- [ ] **Step 5:** Commit `feat(cli): posters read and fill the home cache server named in the config`.
