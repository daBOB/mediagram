# Phase 1: `mediagram-core` and the UniFFI surface

**Context:** [plan.md](plan.md) · [phase 0](phase-00-grammers-android-spike.md) · [spec §3](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** High. Phase 3 and 5 consume this.
- **Status:** Blocked by phase 0 reporting PASS.
- **Deliverable:** a Rust library exposing one narrow UniFFI surface, building for `aarch64-linux-android`, with `mediagram serve` still green on top of the same code.

## Key insights

**This phase is mostly extraction, not new logic.** The byte path already
exists and is covered by five integration test files:

| Existing | Lines | Does | Covered by |
|---|---|---|---|
| `crates/mediagram/src/serve/range.rs` | 130 | `plan_reads`, `total_size`, `parse_range` | `tests/serve_range.rs` |
| `crates/mediagram/src/serve/catalog.rs` | 126 | `list_playable`, `playable_set`, `part_locations` | `tests/serve_catalog.rs` |
| `crates/mediagram/src/serve/stream.rs` | 122 | `pump_step`, `part_document` | `tests/serve_stream.rs` |
| `crates/mediagram/src/serve/telegram.rs` | 85 | the `ByteSource` impl | `tests/serve_http.rs` |
| `crates/mediagram/src/export/encrypt.rs:75` | — | `open()`: AES-256-GCM decrypt | `tests/export_encrypt.rs` |
| `crates/mlib-spec/src/package/mod.rs` | — | `associated_data`, `pointer_is_readable` | `tests/package_spec_examples.rs` |

Those existing tests are the harness that proves each extraction. Do not
write new tests for moved code — run the old ones and require them green
and unchanged.

`serve/response.rs` and `serve/routes.rs` stay in `mediagram`: they are axum.

Decryption happens in Rust, which sidesteps the hazard
`docs/mlib-package-v1.md:129` warns about — Android's `CipherInputStream`
drops GCM tag failures silently. Kotlin never touches the cipher.

## Architecture

```
mlib-spec  ◀── mediagram-core ◀── mediagram (CLI, serve)
                     ▲
                     └── UniFFI ──▶ Kotlin (core:rust)
```

Dependency direction is new but one-way: `mediagram` gains a dependency on
`mediagram-core`. Nothing depends on `mediagram`.

## Related code files

- Create: `crates/mediagram-core/src/{lib.rs,range.rs,catalog.rs,stream.rs,telegram.rs,dto.rs,api.rs}`, `crates/mediagram-core/src/package/{mod.rs,cipher.rs,reader.rs}`
- Modify: `crates/mediagram/src/serve/mod.rs`, `crates/mediagram/src/export/mod.rs`, `crates/mediagram/Cargo.toml`
- Delete: `crates/mediagram/src/serve/{range.rs,catalog.rs,stream.rs,telegram.rs}`, `crates/mediagram/src/export/encrypt.rs`

---

### Task 1: Wire the crate into the workspace

**Files:** Modify `crates/mediagram-core/Cargo.toml` (exists from phase 0), `crates/mediagram/Cargo.toml`, create `crates/mediagram-core/src/lib.rs`

**Produces:** the `mediagram_core` crate, resolvable from `mediagram`.

- [ ] **Step 1:** Add to `crates/mediagram/Cargo.toml` dependencies: `mediagram-core = { path = "../mediagram-core" }`.
- [ ] **Step 2:** Create `crates/mediagram-core/src/lib.rs` with module declarations only.
- [ ] **Step 3:** Run `cargo build --workspace`. Expected: clean.
- [ ] **Step 4:** Commit — `chore(core): add the mediagram-core crate to the workspace`.

---

### Task 2: Extract the range planner

**Files:** Create `crates/mediagram-core/src/range.rs` · Delete `crates/mediagram/src/serve/range.rs` · Modify `crates/mediagram/src/serve/mod.rs` · Test `crates/mediagram/tests/serve_range.rs`

**Interfaces — Produces:** `plan_reads(parts: &[PartSpan], range: &ByteRange) -> Vec<Step>`, `total_size(parts: &[PartSpan]) -> u64`, `parse_range(header: &str, total: u64) -> Result<ByteRange, RangeError>`, and the types `PartSpan`, `ByteRange`, `Step`, `RangeError`.

- [ ] **Step 1: Run the existing test and record the baseline**

Run: `cargo test --test serve_range`
Expected: PASS. Note the test count.

- [ ] **Step 2: Move the file verbatim**

```bash
git mv crates/mediagram/src/serve/range.rs crates/mediagram-core/src/range.rs
```

Add `pub mod range;` to `crates/mediagram-core/src/lib.rs`.

- [ ] **Step 3: Re-export so existing callers keep compiling**

In `crates/mediagram/src/serve/mod.rs`:

```rust
pub use mediagram_core::range;
```

- [ ] **Step 4: Run the same test unchanged**

Run: `cargo test --test serve_range`
Expected: PASS, same count as step 1. **Do not edit the test file.** If it
does not compile, the re-export is wrong, not the test.

- [ ] **Step 5: Commit**

```bash
git commit -am "refactor(core): move the range planner into mediagram-core"
```

---

### Task 3: Extract the catalog queries

**Files:** Create `crates/mediagram-core/src/catalog.rs` · Delete `crates/mediagram/src/serve/catalog.rs` · Test `crates/mediagram/tests/serve_catalog.rs`

**Interfaces — Consumes:** Task 2's `PartSpan`. **Produces:** `list_playable(&Connection) -> Result<Vec<PlayableSet>>`, `playable_set(&Connection, &str) -> Result<Option<PlayableSet>>`, `part_locations(&Connection, &str) -> Result<Vec<PartLocation>>`, types `PlayableSet`, `PartLocation`.

- [ ] **Step 1:** Run `cargo test --test serve_catalog`. Expected: PASS. Record the count.
- [ ] **Step 2:** `git mv crates/mediagram/src/serve/catalog.rs crates/mediagram-core/src/catalog.rs`; add `pub mod catalog;` to the core's `lib.rs`.
- [ ] **Step 3:** Add `pub use mediagram_core::catalog;` to `crates/mediagram/src/serve/mod.rs`.
- [ ] **Step 4:** Run `cargo test --test serve_catalog` unchanged. Expected: PASS, same count.
- [ ] **Step 5:** Confirm the portability rule still holds:

```bash
grep -rl grammers crates/mediagram/src/{index,media,metadata} crates/mlib-spec/
```

Expected: no output.

- [ ] **Step 6:** Commit — `refactor(core): move the catalog queries into mediagram-core`.

---

### Task 4: Extract the Telegram byte source

**Files:** Create `crates/mediagram-core/src/{stream.rs,telegram.rs}` · Delete the `serve/` originals · Test `crates/mediagram/tests/serve_stream.rs`, `crates/mediagram/tests/serve_http.rs`

**Interfaces — Produces:** `pump_step(...)`, `part_document(&Client, PeerRef, i64) -> Result<Document>`, and the `ByteSource` trait implementation.

- [ ] **Step 1:** Run `cargo test --test serve_stream --test serve_http`. Expected: PASS. Record counts.
- [ ] **Step 2:** `git mv` both files into `crates/mediagram-core/src/`; declare both modules in the core's `lib.rs`.
- [ ] **Step 3:** Add the two `pub use` lines to `crates/mediagram/src/serve/mod.rs`.
- [ ] **Step 4:** Run `cargo test --test serve_stream --test serve_http` unchanged. Expected: PASS, same counts.
- [ ] **Step 5:** Run the whole suite: `cargo test --workspace`. Expected: green.
- [ ] **Step 6:** Commit — `refactor(core): move the Telegram byte source into mediagram-core`.

---

### Task 5: Move the cipher and build the package reader

**Files:** Create `crates/mediagram-core/src/package/{mod.rs,cipher.rs,reader.rs}` · Delete `crates/mediagram/src/export/encrypt.rs` · Modify `crates/mediagram/src/export/mod.rs` · Test `crates/mediagram/tests/export_encrypt.rs`, create `crates/mediagram-core/tests/package_reader.rs`

**Interfaces — Consumes:** `mlib_spec::package::{associated_data, pointer_is_readable, LatestPointer}`. **Produces:** `open(key: &[u8; 32], sealed: &[u8], aad: &[u8]) -> Result<Vec<u8>, EncryptError>`, `parse_key(&str) -> Result<[u8; 32], EncryptError>`, and `read_package(pointer: &LatestPointer, sealed: &[u8], key: &[u8; 32], into: &Path) -> Result<PathBuf, PackageError>` returning the path of the extracted `library.db`.

- [ ] **Step 1:** Run `cargo test --test export_encrypt`. Expected: PASS. Record the count.
- [ ] **Step 2:** `git mv crates/mediagram/src/export/encrypt.rs crates/mediagram-core/src/package/cipher.rs`; add `pub mod package;` to the core's `lib.rs` and `pub mod cipher;` to `package/mod.rs`. Point `crates/mediagram/src/export/mod.rs` at `mediagram_core::package::cipher`.
- [ ] **Step 3:** Run `cargo test --test export_encrypt`. Expected: PASS, same count. Fix import paths in the test only if the compiler demands it; no assertion changes.
- [ ] **Step 4: Write the failing test for the reader**

`crates/mediagram-core/tests/package_reader.rs`:

```rust
use mediagram_core::package::{read_package, PackageError};

#[test]
fn rejects_an_archive_whose_tag_does_not_match_the_pointer() {
    let dir = tempfile::tempdir().unwrap();
    let (pointer, sealed, key) = fixture_package();
    let mut tampered = pointer.clone();
    tampered.bytes += 1; // a pointer field that is associated data

    let err = read_package(&tampered, &sealed, &key, dir.path()).unwrap_err();
    assert!(matches!(err, PackageError::Cipher(_)));
}

#[test]
fn extracts_the_index_from_a_well_formed_package() {
    let dir = tempfile::tempdir().unwrap();
    let (pointer, sealed, key) = fixture_package();

    let db = read_package(&pointer, &sealed, &key, dir.path()).unwrap();
    assert!(db.ends_with("library.db"));
    assert!(db.exists());
}
```

`fixture_package()` builds a one-row `library.db`, tars and gzips it, seals it
with `cipher::seal` and returns the matching pointer. Put it in the same file;
`crates/mediagram/tests/export_archive.rs` shows how an archive is assembled.

- [ ] **Step 5:** Run `cargo test -p mediagram-core --test package_reader`. Expected: FAIL, `read_package` not found.
- [ ] **Step 6: Implement `package/reader.rs`**

Verify the pointer with `mlib_spec::package::pointer_is_readable`, check the
SHA-256 of the sealed bytes against `pointer.sha256` before running the
cipher, `open()` with `associated_data(pointer)` as AAD, then gunzip and untar
into `into`. Return the path of `library.db`. Keep the file under 200 lines.

Decrypt whole-file, never streamed — `open()` already enforces this, and
`docs/mlib-package-v1.md` §4 says why.

- [ ] **Step 7:** Run `cargo test -p mediagram-core --test package_reader`. Expected: PASS, both tests.
- [ ] **Step 8:** Commit — `feat(core): read a published package into an index`.

---

### Task 6: Flatten the DTOs across the FFI boundary

**Files:** Create `crates/mediagram-core/src/dto.rs` · Test `crates/mediagram-core/tests/dto_mapping.rs`

**Interfaces — Consumes:** `mlib_spec::caption::Episode`, Task 3's `PlayableSet`. **Produces:** `SetSummary`, and `summary_from(set: &PlayableSet) -> SetSummary`.

This is the task that makes the roadmap's UniFFI blocker irrelevant:
`Episode` never crosses the boundary.

- [ ] **Step 1: Write the failing test**

```rust
use mediagram_core::dto::summary_from;
use mlib_spec::caption::Episode;

#[test]
fn a_single_episode_flattens_to_one_number_twice() {
    let s = summary_from(&playable_with(Some(Episode::Single(4))));
    assert_eq!(s.episode_first, Some(4));
    assert_eq!(s.episode_last, Some(4));
}

#[test]
fn a_range_flattens_to_its_bounds() {
    let s = summary_from(&playable_with(Some(Episode::Range([11, 12]))));
    assert_eq!(s.episode_first, Some(11));
    assert_eq!(s.episode_last, Some(12));
}

#[test]
fn a_film_has_no_episode_at_all() {
    let s = summary_from(&playable_with(None));
    assert_eq!(s.episode_first, None);
    assert_eq!(s.episode_last, None);
}
```

- [ ] **Step 2:** Run `cargo test -p mediagram-core --test dto_mapping`. Expected: FAIL, `summary_from` not found.
- [ ] **Step 3: Implement**

```rust
#[derive(Debug, Clone, uniffi::Record)]
pub struct SetSummary {
    pub set_id: String,
    pub kind: String,
    pub title: String,
    pub show: Option<String>,
    pub season: Option<u32>,
    pub episode_first: Option<u32>,
    pub episode_last: Option<u32>,
    pub year: Option<u32>,
    pub duration_secs: Option<u32>,
    pub poster_key: Option<String>,
    pub total_bytes: u64,
}

pub fn summary_from(set: &PlayableSet) -> SetSummary {
    let (first, last) = match set.episode {
        Some(e) => (Some(e.first()), Some(e.last())),
        None => (None, None),
    };
    // …remaining fields copied straight across
}
```

`Episode::first()` and `Episode::last()` already exist at
`crates/mlib-spec/src/caption.rs:29`.

- [ ] **Step 4:** Run the test. Expected: PASS, three tests.
- [ ] **Step 5:** Commit — `feat(core): flatten set metadata for the binding surface`.

---

### Task 7: The UniFFI surface

**Files:** Create `crates/mediagram-core/src/api.rs` · Modify `crates/mediagram-core/Cargo.toml`, `lib.rs` · Create `crates/mediagram-core/uniffi.toml`

**Interfaces — Produces:** the `Core` object. Phase 3 and phase 5 code against exactly these names:

```rust
#[uniffi::export(async_runtime = "tokio")]
impl Core {
    #[uniffi::constructor]
    pub fn new(data_dir: String) -> Arc<Self>;

    pub fn is_authorized(&self) -> bool;
    pub async fn request_code(&self, phone: String) -> Result<String, CoreError>;
    pub async fn sign_in(&self, token: String, code: String) -> Result<AuthOutcome, CoreError>;
    pub async fn check_password(&self, password: String) -> Result<(), CoreError>;

    pub async fn refresh_catalog(&self, pointer_url: String, key_b64: String) -> Result<u64, CoreError>;
    pub fn list_sets(&self) -> Result<Vec<SetSummary>, CoreError>;
    pub fn poster_path(&self, poster_key: String) -> Option<String>;

    pub fn total_size(&self, set_id: String) -> Result<u64, CoreError>;
    pub async fn read(&self, set_id: String, offset: u64, len: u32) -> Result<Vec<u8>, CoreError>;
}
```

`AuthOutcome` is `enum { Done, PasswordNeeded }`. `CoreError` is a
`#[derive(uniffi::Error)] thiserror` enum with variants `Network`,
`NotAuthorized`, `NotFound`, `Cipher`, `Io` — each carrying a `String`.

- [ ] **Step 1:** Add `uniffi` to `crates/mediagram-core/Cargo.toml` at the current stable release, with the `build`, `cli` and `tokio` features; add `[lib] crate-type = ["cdylib", "staticlib", "rlib"]`; record the version chosen in the commit message. Add a `build.rs` calling `uniffi::generate_scaffolding`.
- [ ] **Step 2: Write the failing test**

`crates/mediagram-core/tests/api_surface.rs`:

```rust
#[test]
fn a_fresh_core_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    assert!(!core.is_authorized());
}

#[test]
fn reading_an_unknown_set_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    let err = core.total_size("nosuchset".into()).unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
}
```

- [ ] **Step 3:** Run `cargo test -p mediagram-core --test api_surface`. Expected: FAIL, `Core` not found.
- [ ] **Step 4:** Implement `api.rs`. It owns a tokio runtime, the grammers client, the session store chosen in phase 0, and the extracted modules. `read` is `plan_reads` over `part_locations` then `pump_step`, collected into a `Vec<u8>`. Keep it under 200 lines; move the session handling into its own module if it grows.
- [ ] **Step 5:** Run the test. Expected: PASS, both tests.
- [ ] **Step 6:** Generate the Kotlin bindings and eyeball them:

```bash
cargo run -p mediagram-core --features cli --bin uniffi-bindgen -- \
  generate --library target/debug/libmediagram_core.so --language kotlin --out-dir /tmp/bindings
```

Expected: a Kotlin file with `suspend fun read(...)` and no `Episode` type anywhere in it.

- [ ] **Step 7:** Commit — `feat(core): expose the player surface over UniFFI`.

---

### Task 8: Build the Android library

**Files:** Create `scripts/build-android-core.sh`, `.cargo/config.toml`

- [ ] **Step 1:** Write `scripts/build-android-core.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
ABIS="arm64-v8a x86_64"   # x86_64 is the emulator
OUT="${1:-android/core/rust/src/main/jniLibs}"
for abi in $ABIS; do
  cargo ndk -t "$abi" -o "$OUT" build -p mediagram-core --release
done
```

- [ ] **Step 2:** Run it. Expected: `libmediagram_core.so` under `arm64-v8a/` and `x86_64/`.
- [ ] **Step 3: Verify 16 KB alignment** for both ABIs:

```bash
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" \
  -lW android/core/rust/src/main/jniLibs/arm64-v8a/libmediagram_core.so | grep LOAD
```

Expected: `Align 0x4000`. If not, add the `max-page-size` rustflags from phase 0 step 7.

- [ ] **Step 4:** Run `cargo test --workspace`. Expected: green, no regressions from any extraction.
- [ ] **Step 5:** Commit — `build(core): cross-compile the core for Android ABIs`.

## Todo list

- [ ] Task 1 crate wired into the workspace
- [ ] Task 2 range planner extracted, `serve_range` green
- [ ] Task 3 catalog extracted, `serve_catalog` green, portability grep clean
- [ ] Task 4 byte source extracted, `serve_stream` and `serve_http` green
- [ ] Task 5 cipher moved, package reader written and tested
- [ ] Task 6 DTOs flattened, `Episode` absent from the boundary
- [ ] Task 7 UniFFI surface generating Kotlin bindings
- [ ] Task 8 `.so` built for both ABIs, 16 KB aligned
- [ ] `cargo test --workspace` green

## Success criteria

`libmediagram_core.so` exists for `arm64-v8a` and `x86_64`, is 16 KB aligned,
and generates Kotlin bindings carrying `read`, `list_sets` and the auth calls.
The full Rust suite is green, and `mediagram serve` answers Range requests
through the extracted code rather than a copy of it.

## Risk assessment

| Risk | Mitigation |
|---|---|
| An extraction breaks `serve` | Each task runs the pre-existing test file for the code it moved, unchanged, before and after. A test that needs editing means the move was wrong. |
| UniFFI async over tokio misbehaves | Task 7 step 6 generates bindings and inspects them before any Kotlin exists. |
| `read` returning `Vec<u8>` copies on every call | Accepted for the walking skeleton. Measure in phase 5 before optimising; a zero-copy surface is a later change and the API name does not move. |
| The 200-line rule pushes back on `api.rs` | Split session handling out, as `media/mp4_atoms.rs` was split out of `media/remux.rs`. |

## Security considerations

`CoreError` variants carry strings that reach Kotlin. Never format an
`api_hash`, a session, a package key, a `chat_id` or a `message_id` into one —
`docs/system-architecture.md` §7 holds for this client too: the UI is told
what it may play, never where the bytes live.

## Next steps

[Phase 3](phase-03-login-and-catalog-mobile.md) consumes this surface.
[Phase 2](phase-02-gradle-skeleton-and-ci.md) can proceed in parallel.
