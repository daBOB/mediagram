# Phase 01: shared signing and the server's blob store

Spec: `docs/superpowers/specs/2026-11-10-shared-tmdb-cache-design.md`. Global Constraints and Review Focus: `plan.md`.

### Task 1: Shared MGC1 signer in `mlib-spec`

Today the only Rust signer is `crates/mediagram-cache/src/token.rs` (`sign`, `verify`, private `canonical`). The core and the CLI must sign too, and both already depend on `mlib-spec` (via `mediagram-tmdb`), so the wire contract moves there.

**Files:**
- Create: `crates/mlib-spec/src/lan_signature.rs`, `crates/mlib-spec/src/lan_signature_tests.rs`
- Modify: `crates/mlib-spec/src/lib.rs` (`pub mod lan_signature;`), `crates/mlib-spec/Cargo.toml` (add `hmac.workspace = true`), `crates/mediagram-cache/Cargo.toml` (add `mlib-spec = { path = "../mlib-spec" }`), `crates/mediagram-cache/src/token.rs` (`sign` and `verify` call the shared function; `canonical` moves)

**Interfaces:**
- Produces: `mlib_spec::lan_signature::SCHEME: &str = "MGC1"`; `mlib_spec::lan_signature::sign(token: &str, method: &str, path: &str, total: u64, body: &[u8]) -> String` (lowercase hex). `mediagram_cache::token::{sign, verify, SCHEME}` keep their signatures.

- [ ] **Step 1: Write the failing tests** in `lan_signature_tests.rs`:
  - `chunk_vector_signs_to_the_published_signature`: `sign("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", "PUT", "/v1/sets/abc123/chunks/0", 5, b"hello") == "c1ac37f41c76c7c8434460c94458f58a817bf0859f9be493209d23a8ab9a4d85"`.
  - `blob_vector_signs_with_the_body_length`: same token, `"PUT"`, `"/v1/art/w342/abc.jpg"`, `5`, `b"hello"`; assert equals the value printed on first run, then frozen as a literal (it is the blob example Task 7 adds to the docs).
- [ ] **Step 2:** `cargo test -p mlib-spec lan_signature` → FAIL (module missing).
- [ ] **Step 3:** Implement `sign` with the canonical string from Global Constraints (move `canonical` from `token.rs`); make `token::sign` call it and `token::verify` compare against it in constant time as it does now.
- [ ] **Step 4:** `cargo test -p mlib-spec lan_signature && cargo test -p mediagram-cache token` → PASS (the cache crate's existing `the_shared_test_vector_*` tests still pass unchanged).
- [ ] **Step 5:** Commit `refactor: the MGC1 signature lives in mlib-spec so every Rust client can sign`.

### Task 2: Cache server blob store and routes

**Files:**
- Create: `crates/mediagram-cache/src/blobs.rs`, `crates/mediagram-cache/src/blobs_tests.rs`, `crates/mediagram-cache/src/http/blob_routes.rs`, `crates/mediagram-cache/src/http/blob_routes_tests.rs`
- Modify: `crates/mediagram-cache/src/config.rs` (`blob_budget`, env `MEDIAGRAM_CACHE_BLOB_BUDGET`, const `DEFAULT_BLOB_BUDGET_BYTES = 8 * 1024 * 1024 * 1024`), `crates/mediagram-cache/src/main.rs` (open `BlobStore` under `{root}/blobs`), `crates/mediagram-cache/src/http.rs` (`router` takes the blob store; merge `blob_routes`; `/v1/status` adds `blob_bytes`, `blob_budget_bytes`), `crates/mediagram-cache/src/lib.rs`

**Interfaces:**
- Consumes: `mlib_spec::lan_signature::sign` via `token::verify` (Task 1).
- Produces (HTTP, used by Task 3/4 clients):
  - `GET|HEAD|PUT /v1/tmdb/{key}`: `key` = 64 lowercase hex; PUT replaces (201 created / 200 replaced).
  - `GET|HEAD|PUT /v1/art/{size}/{name}`: `size` matches `^(w[0-9]{2,4}|h[0-9]{2,4}|original)$`, `name` matches `^[A-Za-z0-9_-]{1,64}\.(jpg|png|svg)$`; PUT write-once (201 created / 200 already held, body ignored).
  - PUT requires `Authorization: MGC1 <sign(token, "PUT", path, body.len(), body)>`; 401 otherwise. Bad key/size/name → 404. JSON body > 2 MiB or image > 16 MiB → 413. Body larger than the blob budget → 413.
- Produces (Rust): `pub enum BlobKind { Tmdb, Art }`; `pub enum BlobPut { Created, Replaced, AlreadyHeld }`; `BlobStore::open(root: PathBuf, budget: u64) -> io::Result<BlobStore>`; `get(&self, kind: BlobKind, name: &str) -> io::Result<Option<Vec<u8>>>`; `head(..) -> io::Result<Option<u64>>`; `put(&self, kind: BlobKind, name: &str, body: &[u8]) -> io::Result<BlobPut>`; `status(&self) -> (u64 /*held*/, u64 /*budget*/)`. Layout `{root}/blobs/tmdb/{key[0..2]}/{key}`, `{root}/blobs/art/{size}/{name}`. Writes go to a temp file then rename, so a reader never sees half a file. LRU by last read or write, evicting only blobs, rebuilt by a scan at open (follow `store/scan.rs`, `store/evict.rs`).

- [ ] **Step 1: Write failing tests** in `blob_routes_tests.rs` (tower `oneshot`, as the existing http tests do):
  - `unsigned_put_is_refused` (401), `signed_tmdb_put_then_get_returns_it` (201, then same bytes), `a_second_tmdb_put_replaces_the_first` (200, GET returns the new bytes), `a_second_art_put_keeps_the_first` (200, GET returns the original), `bad_names_are_not_found` (`/v1/tmdb/XYZ`, `/v1/art/w342/../x.jpg`, `/v1/art/big/a.jpg` → 404), `head_says_whether_it_is_held` (404 then 200 with `content-length`), `a_blob_larger_than_the_budget_is_refused` (budget 10, PUT 11 bytes → 413, nothing else removed), `status_counts_blob_bytes`.
  - In `blobs_tests.rs`: `blobs_evict_least_recent_within_their_own_budget` (budget fits 2 of 3; the unread one goes), `concurrent_art_puts_leave_one_whole_file` (two tasks `put` the same name with the same body; both Ok; file length == body length), `scan_at_open_restores_held_bytes`.
- [ ] **Step 2:** `cargo test -p mediagram-cache blob` → FAIL.
- [ ] **Step 3:** Implement `BlobStore`, `blob_routes`, config and wiring per the interfaces above.
- [ ] **Step 4:** `cargo test -p mediagram-cache && cargo clippy -p mediagram-cache --all-targets -- -D warnings` → PASS.
- [ ] **Step 5:** Commit `feat(cache): the home cache server also keeps TMDB answers and images`.
