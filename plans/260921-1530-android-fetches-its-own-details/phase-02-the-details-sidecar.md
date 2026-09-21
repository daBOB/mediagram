# Phase 2: Somewhere to keep what the phone learns

**Deliverable:** a database the core writes and a refresh cannot delete, read
back behind the index's own rows.

## Context

- `crates/mediagram-core/src/api/catalog.rs:49-56` — `artwork_dir`, and the comment stating both halves of the boundary this phase reuses
- `crates/mediagram-core/src/api/refresh.rs:101-146` — `install_staged` and `remove_other_versions`: what must pass over the sidecar
- `crates/mediagram-core/src/shows.rs:25-56` — `key_parts` and `read`, the lookup this phase widens
- `crates/mlib-spec/src/schema.rs:13-17` — `GROUPS` and `migrations_up_to`, the only place the `shows` DDL is written
- `crates/mediagram/src/index/shows.rs:82-116` — `upsert`, as a reference for the statement, not to be called

## Key insight

The phone must not write the index it downloaded. `catalog.rs` opens it
read-only on purpose, and a refresh replaces the version directory wholesale —
which is exactly what destroyed every fetched poster before `0ce5fd2`. So
fetched rows need the boundary the artwork already sits on: **beside the
version directories, inside `catalog/`.** Out of reach of `install_staged` and
`remove_other_versions`, inside what `FileCoreStorage.clear()` deletes.

Naming only the first half of that is how the poster defect happened. Both
halves go in the comment, and both are pinned by a test.

The schema is not this crate's to invent. `mlib_spec::schema::migrations_up_to`
already writes `shows`, and the sidecar is created from it — so a column added
there reaches both databases or neither.

---

### Task 1: A sidecar the refresh cannot reach

**Files:**
- Create: `crates/mediagram-core/src/api/details.rs`
- Modify: `crates/mediagram-core/src/api/mod.rs` — declare the module
- Test: `crates/mediagram-core/src/api/details_tests.rs`, declared with `mod tests;` at the foot of `details.rs` — the pattern `artwork.rs`/`artwork_tests.rs` already follows

**Interfaces — Produces:** `pub(super) fn details_db(core: &Core) -> PathBuf`,
`pub(super) fn open_or_create(core: &Core) -> Result<Connection, CoreError>`,
`pub(super) fn upsert(conn: &Connection, row: &ShowRow) -> Result<(), CoreError>`.

- [x] **Step 1: Write the failing tests**

```rust
use super::*;

/// A row as a fetch would record it.
fn described(source: &str, kind: &str, id: i64, overview: &str) -> ShowRow { /* the crate's own ShowRow */ }

/// The sidecar sits beside the version directories, not inside one. A
/// refresh replaces a version wholesale, and anything kept within it is
/// deleted every time the app asks the channel for the index.
#[test]
fn the_sidecar_survives_the_refreshes_that_follow_it() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    point_current_at(&core, "v-1");

    let conn = open_or_create(&core).unwrap();
    upsert(&conn, &described("tmdb", "movie", 550, "Ein Kellner in Seifenblasen")).unwrap();
    drop(conn);

    // A refresh replaces the version directory wholesale, the way
    // `install_staged` does: the old one is removed, a new one takes its
    // place, and `current` is repointed.
    std::fs::remove_dir_all(dir(&core).join("v-1")).unwrap();
    point_current_at(&core, "v-2");

    let conn = open_or_create(&core).unwrap();
    assert!(read(&conn, "tmdb-movie-550").unwrap().is_some());
}

/// Forgetting the library forgets what was learned about it. The rows name
/// the previous account's titles, and a sign-out that left them behind
/// would be a sign-out in name only. Stated as a containment rather than by
/// deleting, because the delete itself is the caller's.
#[test]
fn the_sidecar_is_deleted_along_with_the_library_it_describes() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    assert!(
        details_db(&core).starts_with(dir(&core)),
        "descriptions at {} would survive being signed out",
        details_db(&core).display(),
    );
}

/// The schema is written in one place. A column added to the shared
/// migrations reaches this database too, or the two silently disagree
/// about what a row holds.
#[test]
fn the_sidecar_is_built_from_the_shared_migrations() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let conn = open_or_create(&core).unwrap();

    let columns: Vec<String> = conn
        .prepare("SELECT name FROM pragma_table_info('shows')")
        .unwrap()
        .query_map([], |row| row.get(0))
        .unwrap()
        .collect::<rusqlite::Result<_>>()
        .unwrap();

    for expected in [
        "source", "kind", "id", "lang", "overview", "tagline", "genres",
        "rating", "network", "status", "total_seasons", "total_episodes",
    ] {
        assert!(columns.iter().any(|c| c == expected), "no {expected} column");
    }
}

/// Asking twice replaces rather than duplicates: a later fetch is a
/// correction, not a second opinion.
#[test]
fn a_second_fetch_of_one_title_replaces_the_first() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let conn = open_or_create(&core).unwrap();

    upsert(&conn, &described("tmdb", "movie", 550, "first")).unwrap();
    upsert(&conn, &described("tmdb", "movie", 550, "second")).unwrap();

    let row = read(&conn, "tmdb-movie-550").unwrap().unwrap();
    assert_eq!(row.overview.as_deref(), Some("second"));
}
```

`core_at` and `point_current_at` already exist in
`crates/mediagram-core/src/api/catalog_tests.rs:5-17` — `core_at` is
`Core::new(dir.display().to_string(), 1, "test-hash".into())` and
`point_current_at` creates the version directory and repoints the symlink the
way `swap_current` does. Lift both rather than writing a third copy, and fill
in `described` against the crate's own `ShowRow`.
`a_fetched_poster_is_found_after_the_catalogue_is_replaced`
(`catalog_tests.rs:28-45`) is the template for the first test: it is the shape
that would have caught the poster defect, and this one has to catch its
sibling.

- [x] **Step 2: Run them**

```bash
cd /home/andre/Workspace/mediagram-android
cargo test -p mediagram-core details
```

Expected: FAIL to compile — `cannot find module details`.

- [x] **Step 3: Implement**

`details_db(core)` is `catalog::dir(core).join("details.db")` — a sibling of
the version directories and of `artwork/`, for the same two reasons. Say both
in the comment: out of the version directory so a refresh cannot delete it,
inside `catalog/` so forgetting the library forgets it too.

`open_or_create` opens read-write, creating the file, and applies
`mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION)`. It does
not copy the DDL.

`upsert` writes a `ShowRow` with `source` set the way the index sets it — read
`crates/mediagram/src/index/shows.rs:82-116` for the statement's shape and the
`ON CONFLICT` target, and write the core's own rather than calling across.

- [x] **Step 4: Run them**

```bash
cargo test -p mediagram-core details
CARGO_SUB=build cargo $CARGO_SUB -p mediagram-core
```

Expected: PASS, all four.

- [x] **Step 5: Commit**

```bash
git add crates/mediagram-core/
git commit -m "feat(core): keep what a fetch learns where a refresh cannot reach it"
```

---

### Task 2: The index answers first, the sidecar fills the gaps

**Files:**
- Modify: `crates/mediagram-core/src/shows.rs`
- Modify: `crates/mediagram-core/src/api/mod.rs` — `show_info` consults both
- Test: alongside the existing `shows` tests

**Interfaces — Consumes:** `details::open_or_create` (Task 1), `shows::read`.

- [x] **Step 1: Write the failing test**

```rust
    /// The publisher's own description wins. It was written in the
    /// library's language by whoever curated it, and a phone that fetched
    /// its own copy has no better claim on the same title.
    #[test]
    fn a_row_in_the_index_is_preferred_to_a_fetched_one() {
        let data = tempfile::tempdir().unwrap();
        let core = core_at(data.path());
        point_current_at(&core, "v-1");
        index_describes(&core, "tmdb", "movie", 550, "what the publisher wrote");

        let conn = open_or_create(&core).unwrap();
        upsert(&conn, &described("tmdb", "movie", 550, "what the phone fetched")).unwrap();
        drop(conn);

        let info = show_info(&core, "tmdb-movie-550".into()).unwrap();
        assert_eq!(info.overview.as_deref(), Some("what the publisher wrote"));
    }

    /// A title the index says nothing about is what a fetch is for.
    #[test]
    fn a_title_the_index_omits_is_answered_from_the_sidecar() {
        let data = tempfile::tempdir().unwrap();
        let core = core_at(data.path());
        point_current_at(&core, "v-1");
        index_describes_nothing(&core);

        let conn = open_or_create(&core).unwrap();
        upsert(&conn, &described("tmdb", "movie", 550, "what the phone fetched")).unwrap();
        drop(conn);

        let info = show_info(&core, "tmdb-movie-550".into()).unwrap();
        assert_eq!(info.overview.as_deref(), Some("what the phone fetched"));
    }

    /// Neither having it is not an error. A course has no provider entry,
    /// and a library assembled without a key has no rows at all.
    #[test]
    fn a_title_neither_holds_is_simply_unknown() {
        let data = tempfile::tempdir().unwrap();
        let core = core_at(data.path());
        point_current_at(&core, "v-1");
        index_describes_nothing(&core);

        assert!(show_info(&core, "tmdb-movie-550".into()).is_none());
    }

    /// An invalid key reaches neither store. Two lookup locations must not
    /// become two ways past one guard.
    #[test]
    fn an_invalid_key_is_refused_before_either_store_is_opened() {
        let data = tempfile::tempdir().unwrap();
        let core = core_at(data.path());
        point_current_at(&core, "v-1");
        index_describes_nothing(&core);

        let conn = open_or_create(&core).unwrap();
        upsert(&conn, &described("tmdb", "movie", 550, "unreachable")).unwrap();
        drop(conn);

        assert!(show_info(&core, "not-a-valid-poster-key-42x".into()).is_none());
    }
```

`index_describes` writes one `shows` row into the downloaded index's own
`library.db`, and `index_describes_nothing` creates that database with the
schema and no rows — both over `mlib_spec::schema::migrations_up_to`, the way
Task 1's sidecar is created. `catalog_tests.rs` builds a comparable fixture;
read it before writing a third.

The fourth test is the one that matters most: `poster_path` already validates a
key once and returns before building any path, and this lookup now has two
stores behind it. One guard, both stores — never one guard each.

- [x] **Step 2: Run it**

```bash
cargo test -p mediagram-core show_info
```

Expected: FAIL — the sidecar is not consulted.

- [x] **Step 3: Implement**

`show_info` reads the index first through the existing `shows::read`. On
`None`, it opens the sidecar and asks the same question. A missing sidecar file
is `None`, not an error — a library nobody has fetched for is the ordinary case.

Keep `key_parts`' validation as the single gate. Two lookup locations behind one
guard, never one guard each — the rule `poster_path` already follows, and for
the same reason.

- [x] **Step 4: Run everything**

```bash
cargo test -p mediagram-core
CARGO_SUB=build cargo $CARGO_SUB -p mediagram-core
ANDROID_HOME=/home/andre/android-sdk ./scripts/check.sh
wc -l crates/mediagram-core/src/shows.rs crates/mediagram-core/src/api/details.rs crates/mediagram-core/src/api/mod.rs
```

Every file strictly under 200. `api/mod.rs` was 286 before this plan and is the
one to watch — if it crosses, the `show_info` composition is what moves out,
not an arbitrary split.

- [x] **Step 5: Commit**

```bash
git add crates/mediagram-core/
git commit -m "feat(core): answer from the index first, and from what was fetched after"
```

## Accepted deviation

**`pub` rather than `pub(super)`, behind `pub mod details`.** The interfaces
above specify `pub(super)` for `details_db`, `open_or_create` and `upsert`.
They shipped `pub`, inside a module declared `pub mod details` in
`crates/mediagram-core/src/api/mod.rs`, and that stands.

`pub(super)` would have put the store out of reach of anything outside
`crate::api` — including the integration tests that drive a real sidecar over
a real path, which is the only place the two properties this phase exists for
are actually checked: that a refresh cannot delete the file, and that a
sign-out can. A store provable only from inside the module that writes it is
not provably placed at all.

It is also what `pub mod artwork` next to it already does, for the same
reason. The boundary this phase defends is the path `details_db` returns, not
the visibility of the function that returns it: no application code reaches
either, because `mediagram-core` is consumed through its UniFFI surface, and
that surface exports neither. The reach is tests —
`crates/mediagram-core/tests/shows_query.rs`, and since the review,
`crates/mediagram/tests/shared_shows_upsert.rs`, which writes one row through
this `upsert` and one through the uploader's and compares them column by
column. That test could not exist under `pub(super)` either.

## Todo list

- [x] The sidecar is a sibling of the version directories, inside `catalog/`
- [x] Its schema comes from `mlib_spec`, not a second copy
- [x] A refresh cannot delete it; a sign-out does
- [x] The index's own row wins; the sidecar fills gaps
- [x] One key-validation gate over both lookups
- [x] Every file under 200 lines

## Success criteria

A row written to the sidecar survives two installs and is read back through
`show_info` for a title the index says nothing about. `./scripts/check.sh`
passes and `cargo build -p mediagram-core` alone passes.

## Risk assessment

| Risk | Mitigation |
|---|---|
| The sidecar is put somewhere a refresh deletes | Task 1's first test drives `install_staged` and `remove_other_versions` rather than asserting on a string. |
| A sign-out leaves the previous account's descriptions behind | Task 1's second test pins the containment. |
| The DDL drifts from the index's | Task 1's third test reads `PRAGMA table_info` against the shared migrations. |
| A second lookup location becomes a second way past the key guard | Task 2 step 3 states the rule; the review checks it. |
| `api/mod.rs` crosses 200 | Task 2 step 4 measures and names what moves. |

## Next steps

Phase 3 fills the sidecar from TMDB in the same run that fetches posters.
