# Phase 2: What the core can already answer

**Deliverable:** three new answers from `mediagram-core` — a set's technical
fields, a show's description, and what the installed catalog is — with nothing
on screen yet.

## Context

- Spec §1 (the metadata is already on the device), §4 (the technical line), §5 (the Catalogue block)
- `crates/mlib-spec/src/schema.rs` — `sets` and the `shows` table added in V5
- `crates/mediagram/src/index/shows.rs:93` — the uploader writing the rows this phase reads
- `web/src/shows.ts:41` — the web player reading the same columns

## Key insight

`PlayableSet` in `crates/mediagram-core/src/catalog.rs` **already reads**
`container`, `vcodec`, `acodec` and `tmdb`. The DTO in `dto.rs` drops them on
the way to Kotlin. So two of the five fields the detail screen needs are a
mapping change, not a query change; only `quality` and `hdr` are genuinely
absent from `COLUMNS`.

The `shows` table is the larger find: the uploader has been writing overview,
tagline, genres and rating into every `library.db` this app downloads, and
nothing on Android has ever read them.

---

### Task 1: A set says what it is

**Files:**
- Modify: `crates/mediagram-core/src/catalog.rs` (`COLUMNS`, `PlayableSet`, `read_set`)
- Modify: `crates/mediagram-core/src/dto.rs` (`SetSummary` and its mapping)
- Test: `crates/mediagram-core/tests/dto_mapping.rs`

**Interfaces — Produces:** `SetSummary` gains
`container: String`, `vcodec: Option<String>`, `acodec: Option<String>`,
`quality: Option<String>`, `hdr: Option<String>`. Phase 5 renders all five.

- [ ] **Step 1: Write the failing test**

Append to `crates/mediagram-core/tests/dto_mapping.rs`. Follow the file's
existing fixture style for building a `PlayableSet`:

```rust
/// The detail screen prints what a file is, in the order the web player
/// prints it. Every one of these columns is in the index already; the DTO
/// simply did not carry them.
#[test]
fn a_summary_carries_what_the_file_is() {
    let set = PlayableSet {
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("eac3".into()),
        quality: Some("1080p".into()),
        hdr: Some("HDR10".into()),
        ..playable_set_fixture()
    };

    let summary = summary_from(&set);

    assert_eq!(summary.container, "mkv");
    assert_eq!(summary.vcodec.as_deref(), Some("hevc"));
    assert_eq!(summary.acodec.as_deref(), Some("eac3"));
    assert_eq!(summary.quality.as_deref(), Some("1080p"));
    assert_eq!(summary.hdr.as_deref(), Some("HDR10"));
}

/// `SDR` is the absence of a fact rather than a fact, and the web player
/// drops it rather than printing it on every card. The DTO carries whatever
/// the index holds and lets the surface decide, so this pins that the column
/// survives the trip — deciding is `hdrLabel`'s job, on the other side.
#[test]
fn an_sdr_title_still_reports_its_dynamic_range() {
    let set = PlayableSet { hdr: Some("SDR".into()), ..playable_set_fixture() };

    assert_eq!(summary_from(&set).hdr.as_deref(), Some("SDR"));
}
```

If `playable_set_fixture()` does not exist in that file, add it — a `PlayableSet`
with every field at its empty value and `set_id`/`kind`/`container` filled —
rather than repeating a twenty-field literal in each test.

- [ ] **Step 2: Run the test**

```bash
cd /home/andre/Workspace/mediagram-android
cargo test -p mediagram-core --test dto_mapping
```

Expected: FAIL to compile — `PlayableSet` has no field `quality`, and
`SetSummary` has no field `container`.

- [ ] **Step 3: Implement**

In `catalog.rs`, add the two missing columns to the query:

```rust
const COLUMNS: &str = "set_id, kind, title, show, chap, path, season, episode, tmdb, year, container,
     vcodec, acodec, quality, hdr, duration, total, part_count";
```

Add the two fields to `PlayableSet`, beside `container`:

```rust
    /// What the index recorded about the picture. `quality` is a label such
    /// as `1080p`, not a measurement; `hdr` is `HDR10`, `HLG`, `DV` or `SDR`.
    pub quality: Option<String>,
    pub hdr: Option<String>,
```

And to `read_set`:

```rust
        quality: row.get("quality")?,
        hdr: row.get("hdr")?,
```

In `dto.rs`, add the five fields to `SetSummary` and carry them through its
mapping. Place them after `year` so the DTO reads in the same order as the
detail screen prints.

- [ ] **Step 4: Run the test**

```bash
cargo test -p mediagram-core
```

Expected: PASS, including the existing `api_surface` and `package_reader` tests.

- [ ] **Step 5: Regenerate the Kotlin bindings and build**

```bash
./scripts/generate-android-bindings.sh
cd android && ./gradlew :core:data:testDebugUnitTest
```

Expected: succeeds. The generated `SetSummary` gains five properties; nothing
reads them yet, which is correct for this phase.

- [ ] **Step 6: Commit**

```bash
git add crates/mediagram-core android/
git commit -m "feat(core): let a set say what it is, not only how long it is"
```

---

### Task 2: A show says what it is about

**Files:**
- Create: `crates/mediagram-core/src/shows.rs`
- Modify: `crates/mediagram-core/src/lib.rs`, `crates/mediagram-core/src/dto.rs`, `crates/mediagram-core/src/api/mod.rs`, `crates/mediagram-core/src/api/catalog.rs`
- Test: `crates/mediagram-core/tests/shows_query.rs`

**Interfaces — Consumes:** nothing from Task 1.
**Produces:** `Core::show_info(poster_key: String) -> Option<ShowInfo>`, where
`ShowInfo { overview: Option<String>, tagline: Option<String>, genres: Option<String>, rating: Option<f64>, network: Option<String>, status: Option<String> }`.
Phase 5 renders it.

Keyed by poster key rather than set id because the `shows` table is keyed the
way a poster key is — `(source, kind, id)` — and one row belongs to a whole
series, not to each episode of it.

- [ ] **Step 1: Write the failing test**

`crates/mediagram-core/tests/shows_query.rs`:

```rust
//! The uploader writes a title's description into the index; this reads it
//! back. Nothing here reaches TMDB — the row is already on disk.

use rusqlite::Connection;

fn catalog_with_show(dir: &std::path::Path, kind: &str, id: i64) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating)
         VALUES ('tmdb', ?1, ?2, 'en-US', 'Dracula is awakened.', 'The final hunt begins.', 'Action, Horror', 5.9)",
        rusqlite::params![kind, id],
    )
    .unwrap();
}

#[test]
fn a_film_reports_what_the_provider_said_about_it() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    let info = core.show_info("tmdb-movie-11225".into()).expect("a recorded show");

    assert_eq!(info.overview.as_deref(), Some("Dracula is awakened."));
    assert_eq!(info.tagline.as_deref(), Some("The final hunt begins."));
    assert_eq!(info.genres.as_deref(), Some("Action, Horror"));
    assert_eq!(info.rating, Some(5.9));
}

/// Every episode of a series shares one row, so the key must be the series'.
#[test]
fn a_series_key_finds_the_row_that_belongs_to_the_whole_show() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "tv", 1399);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("tmdb-tv-1399".into()).is_some());
}

/// A title the uploader never resolved has no row, and that is ordinary
/// rather than an error: a course has no provider entry at all.
#[test]
fn a_title_with_no_recorded_description_says_nothing_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("tmdb-movie-99999".into()).is_none());
}

/// A key that is not a key never reaches SQL.
#[test]
fn a_malformed_key_is_refused_before_it_is_queried() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("'; DROP TABLE shows; --".into()).is_none());
}
```

- [ ] **Step 2: Run the test**

```bash
cargo test -p mediagram-core --test shows_query
```

Expected: FAIL to compile — no method `show_info`.

- [ ] **Step 3: Implement**

`crates/mediagram-core/src/shows.rs`:

```rust
//! What a provider said about a title, read back out of the index.
//!
//! The uploader fetched this when the title was added and wrote it here, so
//! reading it costs no request and no API key. Keyed the way a poster key is,
//! because TMDB numbers films and series independently and one row belongs to
//! a whole series rather than to each episode of it.

use anyhow::Result;
use rusqlite::{Connection, OptionalExtension};

/// What a viewer would read about a title.
#[derive(Debug, Clone, PartialEq)]
pub struct ShowRecord {
    pub overview: Option<String>,
    pub tagline: Option<String>,
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
}

/// Splits `tmdb-movie-1234` into the source, kind and id the table is keyed
/// by. Returns `None` for anything that is not a poster key, so a malformed
/// value is refused here rather than reaching SQL.
pub fn key_parts(poster_key: &str) -> Option<(&str, &str, i64)> {
    if !mlib_spec::package::poster_key_is_valid(poster_key) {
        return None;
    }
    let rest = poster_key.strip_prefix("tmdb-")?;
    let (kind, id) = rest.split_once('-')?;
    Some(("tmdb", kind, id.parse().ok()?))
}

pub fn read(conn: &Connection, poster_key: &str) -> Result<Option<ShowRecord>> {
    let Some((source, kind, id)) = key_parts(poster_key) else {
        return Ok(None);
    };
    let row = conn
        .query_row(
            "SELECT overview, tagline, genres, rating, network, status
             FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3",
            rusqlite::params![source, kind, id],
            |row| {
                Ok(ShowRecord {
                    overview: row.get(0)?,
                    tagline: row.get(1)?,
                    genres: row.get(2)?,
                    rating: row.get(3)?,
                    network: row.get(4)?,
                    status: row.get(5)?,
                })
            },
        )
        .optional()?;
    Ok(row)
}
```

Add `mod shows;` to `lib.rs`. Add a `ShowInfo` DTO to `dto.rs` mirroring
`ShowRecord` with `#[derive(uniffi::Record)]`, and a `From<ShowRecord>` for it,
following how `SetSummary` is declared in that file.

In `api/mod.rs`, beside `list_sets`:

```rust
    /// What the index records about a title, or nothing. A course has no
    /// provider entry and a library assembled without a TMDB key has no rows
    /// at all; both are ordinary, so neither is an error.
    pub fn show_info(&self, poster_key: String) -> Option<crate::dto::ShowInfo> {
        let conn = catalog::open(self).ok()?;
        crate::shows::read(&conn, &poster_key).ok().flatten().map(Into::into)
    }
```

- [ ] **Step 4: Run the test**

```bash
cargo test -p mediagram-core
```

Expected: PASS, all four new cases and every existing one.

- [ ] **Step 5: Regenerate bindings, add the Kotlin seam**

```bash
./scripts/generate-android-bindings.sh
```

Add `fun showInfo(posterKey: String): ShowInfo?` to `CoreClient` and
`DefaultCoreClient`, matching how `posterPath` is declared there. Then:

```bash
cd android && ./gradlew :core:data:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add crates/mediagram-core android/
git commit -m "feat(core): read back what a provider said about a title"
```

---

### Task 3: The catalog says what it is

**Files:**
- Modify: `crates/mediagram-core/src/api/catalog.rs`, `crates/mediagram-core/src/api/mod.rs`, `crates/mediagram-core/src/dto.rs`
- Test: `crates/mediagram-core/tests/catalog_facts.rs`

**Interfaces — Produces:** `Core::catalog_facts() -> CatalogFacts`, where
`CatalogFacts { origin: String, sets: u64, posters: u64, schema: u32 }`.
Phase 3's Catalogue block renders it.

`origin` is `"channel"` or `"package"`, read from the installed catalog's
`identity.json` where one exists — the package path writes it and the channel
path does not, which is what tells them apart.

- [ ] **Step 1: Write the failing test**

`crates/mediagram-core/tests/catalog_facts.rs`:

```rust
//! What the System screen says under "Catalogue". Every number here is read
//! from the installed catalog rather than counted as it is displayed, so the
//! screen cannot disagree with what is actually on disk.

use rusqlite::Connection;

fn catalog_with(dir: &std::path::Path, sets: usize, posters: usize) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    for n in 0..sets {
        let set_id = format!("01SET00000000000000000{n:02}");
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, 'movie', 'mkv', 100, 1, 'complete', 0, 1)",
            rusqlite::params![set_id],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
             VALUES (?1, 0, 0, 100, -1001, 100, 'done')",
            rusqlite::params![set_id],
        )
        .unwrap();
    }
    if posters > 0 {
        let art = current.join("posters");
        std::fs::create_dir_all(&art).unwrap();
        for n in 0..posters {
            std::fs::write(art.join(format!("tmdb-movie-{n}.jpg")), b"x").unwrap();
        }
    }
}

#[test]
fn a_channel_catalog_counts_its_sets_and_its_artwork() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with(dir.path(), 3, 2);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "h".into());

    let facts = core.catalog_facts();

    assert_eq!(facts.origin, "channel");
    assert_eq!(facts.sets, 3);
    assert_eq!(facts.posters, 2);
    assert_eq!(facts.schema, mlib_spec::schema::SCHEMA_VERSION);
}

/// No posters directory at all is zero, not a failure — it is the ordinary
/// state of a catalog read from a channel before any artwork is fetched.
#[test]
fn a_catalog_with_no_artwork_says_none_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with(dir.path(), 1, 0);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "h".into());

    assert_eq!(core.catalog_facts().posters, 0);
}

/// Before setup finishes there is no catalog. The screen still has to render.
#[test]
fn no_catalog_at_all_reports_zeroes_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "h".into());

    let facts = core.catalog_facts();

    assert_eq!(facts.sets, 0);
    assert_eq!(facts.posters, 0);
}
```

- [ ] **Step 2: Run the test**

```bash
cargo test -p mediagram-core --test catalog_facts
```

Expected: FAIL to compile — no method `catalog_facts`.

- [ ] **Step 3: Implement**

Add `CatalogFacts` to `dto.rs` as a `uniffi::Record`. In `api/catalog.rs`, add
a function that counts playable sets with the existing `PLAYABLE_SQL`
predicate, counts `*.jpg` entries under `<current>/posters/`, and reads
`identity.json` to decide `origin`. In `api/mod.rs`:

```rust
    /// What the installed catalog is, for the screen that says so.
    ///
    /// Total failure is reported as zeroes rather than an error: this is
    /// read to draw a screen, and a screen that cannot draw because a count
    /// failed is worse than one that says a library is empty.
    pub fn catalog_facts(&self) -> crate::dto::CatalogFacts {
        catalog::facts(self)
    }
```

- [ ] **Step 4: Run the test**

```bash
cargo test -p mediagram-core
```

Expected: PASS.

- [ ] **Step 5: Regenerate bindings and add the Kotlin seam**

```bash
./scripts/generate-android-bindings.sh
```

Add `fun catalogFacts(): CatalogFacts` to `CoreClient` and `DefaultCoreClient`.

```bash
cd android && ./gradlew :core:data:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add crates/mediagram-core android/
git commit -m "feat(core): let the catalog say what it holds"
```

## Todo list

- [ ] `SetSummary` carries container, vcodec, acodec, quality, hdr
- [ ] `show_info` reads the `shows` table and refuses a malformed key before SQL
- [ ] `catalog_facts` counts sets, artwork and schema, and survives no catalog at all
- [ ] Bindings regenerated; `CoreClient` declares all three
- [ ] `cargo test --all` and `:core:data:testDebugUnitTest` pass

## Success criteria

The core can answer what a file is, what a title is about, and what the
catalog holds. No screen has changed.

## Risk assessment

| Risk | Mitigation |
|---|---|
| A poster key from the catalog reaches SQL unchecked | `key_parts` runs `poster_key_is_valid` first and returns `None` otherwise; a test passes a SQL injection string and expects `None`. |
| `quality`/`hdr` are absent from older indexes | Both are nullable columns and the DTO carries `Option`; a set from a pre-V-whatever index simply reports neither. |
| Counting posters walks a large directory on every call | It is one `read_dir` of at most a few hundred JPEGs, called when a screen opens, not per frame. If it ever shows up, memoize as the web's status route does — not before. |
| The `shows` row is in a language nobody asked for | The table records `lang` and the uploader replaces rather than accumulates. Out of scope here; Android reads whatever the library holds. |

## Next steps

Phase 3 renders `catalog_facts`. Phase 5 renders the rest.
