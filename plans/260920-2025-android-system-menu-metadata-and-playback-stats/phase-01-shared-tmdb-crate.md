# Phase 1: One TMDB client, in a crate both sides share

**Deliverable:** `crates/mediagram-tmdb`, holding the TMDB client and the
poster resolution and download the uploader already uses, with the uploader
depending on it and behaving exactly as before.

Nothing a viewer can see changes. No new behaviour, no new tests of its own —
the existing suite is the safety net, and it must come through unchanged.

## Context

- Spec §2 — why this lives in Rust rather than Kotlin
- `crates/mediagram/src/metadata/details.rs` — its doc comment already argues for one copy
- `crates/mediagram/src/export/posters.rs` — resolution, download, and the size and shape limits

## Key insight

The three files that make up the TMDB client contain **zero `crate::`
references** — they depend on nothing in the uploader but `mlib_spec::Kind`.
The move is therefore mechanical. `posters.rs` is nearly as clean: it reaches
for exactly three uploader-internal items, all of them four to six lines long,
and all of them belonging with the poster logic rather than with the uploader.

`details.rs` already carries the argument for doing this:

> Three callers want this payload — resolving a title, finding its artwork,
> and recording what it says — and every one of them depends on hitting the
> cache `add` wrote rather than the network… One copy is the only way that
> stays true.

A fourth caller is about to arrive on a phone.

## A note on TDD for this phase

This is a move, so there is no failing test to write first: the red step is the
compiler, and the green step is the suite that already exists passing
unchanged. Record the test count before and after — a move that silently drops
a test file is exactly the failure this ordering is meant to catch.

---

### Task 1: The crate, and the client inside it

**Files:**
- Create: `crates/mediagram-tmdb/Cargo.toml`, `crates/mediagram-tmdb/src/lib.rs`
- Move: `crates/mediagram/src/metadata/{tmdb_client,tmdb_types,details}.rs` → `crates/mediagram-tmdb/src/`
- Modify: `Cargo.toml` (workspace members), `crates/mediagram/Cargo.toml`, `crates/mediagram/src/metadata/mod.rs`
- Modify (imports only): `crates/mediagram/src/commands/{metadata,posters,add,edit,export_package}.rs`, `crates/mediagram/src/index/shows.rs`, `crates/mediagram/src/export/posters.rs`, `crates/mediagram/src/metadata/show_details.rs`, `crates/mediagram/tests/index_shows.rs`

**Interfaces — Produces:** crate `mediagram-tmdb`, lib `mediagram_tmdb`, exporting
`TmdbApi`, `TmdbClient`, `DetailsResponse`, `NamedRef`, and `details(api, kind, id)`.
Task 2 and phase 4 both depend on these exact paths.

- [ ] **Step 1: Record the baseline**

```bash
cd /home/andre/Workspace/mediagram-android
cargo test --all 2>&1 | grep -c "^test result: ok"
```

Write the number down. It must be identical at step 6.

- [ ] **Step 2: Create the crate**

`crates/mediagram-tmdb/Cargo.toml`:

```toml
[package]
name = "mediagram-tmdb"
description = "TMDB lookups and poster artwork, shared by the uploader and the mobile core"
version.workspace = true
edition.workspace = true
license.workspace = true
rust-version.workspace = true

[dependencies]
anyhow = "1.0.104"
mlib-spec = { path = "../mlib-spec" }
serde = { workspace = true }
serde_json = { workspace = true }
sha2 = { workspace = true }
tracing = "0.1"
# No TLS provider is named here on purpose. The uploader builds with `rustls`
# and the mobile core with `rustls-no-provider`, and Cargo unifies features
# per target — a provider chosen here would be chosen for both.
reqwest = { version = "0.13.5", default-features = false, features = ["json"] }
```

Add `"crates/mediagram-tmdb"` to the workspace `members` list in the root
`Cargo.toml`, keeping the list in its existing order.

`crates/mediagram-tmdb/src/lib.rs`:

```rust
//! Everything that talks to TMDB, in one place.
//!
//! The uploader resolves titles and publishes packages with this; the mobile
//! core fetches artwork with it. A second copy would be a second set of
//! answers to how a credential is presented, how a rate limit is obeyed, and
//! what a poster path is allowed to look like — and the two would drift
//! apart the first time only one of them was fixed.

pub mod details;
pub mod tmdb_client;
pub mod tmdb_types;

pub use details::details;
```

- [ ] **Step 3: Move the three files**

```bash
git mv crates/mediagram/src/metadata/tmdb_client.rs crates/mediagram-tmdb/src/
git mv crates/mediagram/src/metadata/tmdb_types.rs crates/mediagram-tmdb/src/
git mv crates/mediagram/src/metadata/details.rs crates/mediagram-tmdb/src/
```

In the moved `details.rs`, change its two imports from `super::` to `crate::`:

```rust
use crate::tmdb_client::TmdbApi;
use crate::tmdb_types::DetailsResponse;
```

`tmdb_client.rs` and `tmdb_types.rs` need no edits — neither references
`crate::` or `super::`.

- [ ] **Step 4: Point the uploader at the crate**

Add to `crates/mediagram/Cargo.toml` `[dependencies]`:

```toml
mediagram-tmdb = { path = "../mediagram-tmdb" }
```

`crates/mediagram/src/metadata/mod.rs` becomes:

```rust
//! Interactive resolution of provider ids, over the shared TMDB client.

mod search;

pub mod prompt;
pub mod resolve;
pub mod show_details;
```

Then repoint every import listed under **Files** above:

| Was | Becomes |
|---|---|
| `crate::metadata::tmdb_client::TmdbClient` | `mediagram_tmdb::tmdb_client::TmdbClient` |
| `crate::metadata::tmdb_client::{TmdbApi, TmdbClient}` | `mediagram_tmdb::tmdb_client::{TmdbApi, TmdbClient}` |
| `crate::metadata::tmdb_types::DetailsResponse` | `mediagram_tmdb::tmdb_types::DetailsResponse` |
| `crate::metadata::tmdb_types::NamedRef` | `mediagram_tmdb::tmdb_types::NamedRef` |
| `crate::metadata::details::details` | `mediagram_tmdb::details` |
| `mediagram::metadata::tmdb_types::{DetailsResponse, NamedRef}` (in `tests/index_shows.rs`) | `mediagram_tmdb::tmdb_types::{DetailsResponse, NamedRef}` |

`tests/index_shows.rs` also needs `mediagram-tmdb` as a dev-dependency of the
uploader, or it cannot name the crate. Add it under `[dev-dependencies]`.

- [ ] **Step 5: Build**

```bash
cd /home/andre/Workspace/mediagram-android
cargo clippy --all-targets --all-features -- -D warnings
```

Expected: clean. Any `unresolved import` means a call site in the table above
was missed — fix it rather than re-exporting from `metadata::` to paper over it.
A re-export would leave two names for one thing, which is the state this phase
exists to end.

- [ ] **Step 6: Test**

```bash
cargo test --all 2>&1 | grep -c "^test result: ok"
```

Expected: PASS, and the same count as step 1.

- [ ] **Step 7: Commit**

```bash
git add -A crates/ Cargo.toml Cargo.lock
git commit -m "refactor: put the TMDB client where both callers can reach it"
```

---

### Task 2: The posters move with it

**Files:**
- Create: `crates/mediagram-tmdb/src/posters.rs`
- Move: `crates/mediagram/src/export/posters.rs` → `crates/mediagram-tmdb/src/posters.rs`
- Modify: `crates/mediagram-tmdb/src/lib.rs`, `crates/mediagram/src/export/mod.rs`, `crates/mediagram/src/index/shows.rs`, `crates/mediagram/src/commands/{posters,export_package}.rs`, `crates/mediagram/tests/export_posters.rs`

**Interfaces — Consumes:** `TmdbApi`, `details` (Task 1).
**Produces:** `mediagram_tmdb::posters::{PosterRef, resolve_posters, download_into, already_held, poster_url, kind_key}`.
Phase 4 calls `resolve_posters` and `download_into` from `mediagram-core`.

- [ ] **Step 1: Move the file**

```bash
git mv crates/mediagram/src/export/posters.rs crates/mediagram-tmdb/src/posters.rs
```

Add `pub mod posters;` to `crates/mediagram-tmdb/src/lib.rs`, after `pub mod details;`.

- [ ] **Step 2: Bring its three helpers with it**

`posters.rs` reaches for three uploader-internal items. All three belong with
the poster logic rather than with the uploader, so they move rather than being
re-exported.

Replace the `use crate::export::{restrict, restrict_dir};` line with these two
functions at the bottom of `posters.rs` (they were `pub(crate)` in
`crates/mediagram/src/export/mod.rs:25-34`, keeping their doc comments):

```rust
use std::os::unix::fs::PermissionsExt;

/// Artwork is written for one account's library and nobody else's.
fn restrict(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))
}

/// The same, for a directory, which needs the execute bit to be enterable.
fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}
```

Move `kind_key` out of `crates/mediagram/src/index/shows.rs:44-50` into
`posters.rs`, made public, keeping its comment:

```rust
/// Which half of TMDB's numbering a kind belongs to.
pub fn kind_key(kind: Kind) -> &'static str {
    match kind {
        Kind::Movie => "movie",
        // A course has no provider entry; it never reaches this table.
        Kind::Ep | Kind::Tut | Kind::Doc => "tv",
    }
}
```

Then in `posters.rs` change `crate::index::shows::kind_key(kind)` to
`kind_key(kind)` and `crate::metadata::details::details(...)` to
`crate::details::details(...)`.

In `crates/mediagram/src/index/shows.rs`, delete `kind_key` and import it:
`use mediagram_tmdb::posters::kind_key;`.

In `crates/mediagram/src/export/mod.rs`, delete `restrict` and `restrict_dir`
**only if nothing else in the uploader uses them** — check first:

```bash
grep -rn "restrict(\|restrict_dir(" crates/mediagram/src/
```

If other callers remain, leave the originals in place; the copies in the new
crate are private to it and the duplication is two four-line permission calls
in two crates that cannot share a private helper.

- [ ] **Step 3: Repoint the uploader**

`crates/mediagram/src/export/mod.rs` loses `pub mod posters;`. Every
`crate::export::posters::X` becomes `mediagram_tmdb::posters::X` — in
`commands/posters.rs`, `commands/export_package.rs`, and
`tests/export_posters.rs`.

- [ ] **Step 4: Build and test**

```bash
cargo clippy --all-targets --all-features -- -D warnings
cargo test --all 2>&1 | grep -c "^test result: ok"
```

Expected: clean, PASS, and the same count as Task 1 step 1. `export_posters.rs`
is the test that proves the download rules survived the move — if its count
changed, the file did not come with it.

- [ ] **Step 5: Commit**

```bash
git add -A crates/
git commit -m "refactor: artwork belongs with the client that finds it"
```

## Todo list

- [ ] `crates/mediagram-tmdb` exists and is a workspace member
- [ ] Client, types and details moved, no `metadata::tmdb_*` path remains
- [ ] Posters moved, with its three helpers
- [ ] No TLS provider named in the new crate's `reqwest` dependency
- [ ] `cargo clippy --all-targets -- -D warnings` clean
- [ ] `cargo test --all` passes with the same test count as before the phase

## Success criteria

The uploader behaves exactly as it did, one TMDB client exists in the
repository, and `mediagram-core` is able to depend on it without dragging in
grammers, ffmpeg or the CLI.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The new crate names a TLS provider and breaks the Android build | Its `reqwest` dependency sets `default-features = false, features = ["json"]` and no provider, with a comment saying why. The uploader keeps `rustls`; the core keeps `rustls-no-provider`. |
| A test file is silently dropped by the move | Step 1 records the passing count and step 6 compares it. `export_posters.rs` is named explicitly because it is the one that covers the moved download rules. |
| A missed import gets papered over with a re-export | Step 5 says not to. Two names for one item is the state this phase removes. |
| `restrict`/`restrict_dir` still used elsewhere in the uploader | Task 2 step 2 greps before deleting and says what to do in both cases. |

## Next steps

Phase 4 calls `resolve_posters` and `download_into` from `mediagram-core`.
Phase 2 is independent of this and can run in either order.
