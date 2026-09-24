# Phase 03 — Rust core state, API and Android bindings

**Context:** spec § Data, § Sync. Files: `crates/mediagram-core/src/state/schema.rs` (`GROUPS`, `VERSION = GROUPS.len()` — currently 2), `state/profiles.rs` (`Profile`, `list`, `create`, `profile_named_with_creation`), `state/exchange.rs` (`export_record` `:19-50`, `import_merged` `:62-92`), `api/state.rs` (`create_profile` `:45-52`). Generated Kotlin: `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`, regenerated with `scripts/generate-android-bindings.sh`. Depends on Phase 01 Task 2.

---

### Task 4: Rust `profiles.kids`, create/list, export/import, API, bindings

**Files:**
- Modify: `crates/mediagram-core/src/state/schema.rs`
- Modify: `crates/mediagram-core/src/state/profiles.rs`
- Modify: `crates/mediagram-core/src/state/exchange.rs`
- Modify: `crates/mediagram-core/src/api/state.rs`
- Create: `crates/mediagram-core/src/state/kids_profile_tests.rs` (register with `#[cfg(test)] mod kids_profile_tests;` in `state/mod.rs`, beside the other `*_tests` modules)
- Regenerate: `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Fix callers: every `profiles::create(conn, "…")` in tests → `profiles::create(conn, "…", false)`

**Interfaces:**
- Consumes: `ProfileState.kids: bool`, `MergedProfile.kids: bool` (Task 2).
- Produces:
  - `#[derive(uniffi::Record)] pub struct Profile { pub id: String, pub name: String, #[uniffi(default = false)] pub kids: bool }` — Kotlin `Profile(id, name, kids = false)`
  - `profiles::create(conn: &Connection, name: &str, kids: bool) -> rusqlite::Result<Option<Profile>>`
  - `Core::create_profile(self: Arc<Self>, name: String, kids: bool) -> Option<Profile>` (Kotlin: `core.createProfile(name, kids)`)

- [ ] **Step 1: Write the failing tests**

`kids_profile_tests.rs`:

```rust
use super::StateDb;
use super::exchange::{export_record, import_merged};
use super::merge::{MergedProfile, MergedState};
use super::profiles;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn merged(name: &str, kids: bool) -> MergedState {
    MergedState {
        profiles: vec![MergedProfile {
            name: name.to_lowercase(),
            display_name: name.to_string(),
            kids,
            progress: vec![],
            watched: vec![],
            watchlist: vec![],
            collections: vec![],
        }],
        kids: vec![],
    }
}

#[test]
fn a_profile_is_kids_only_when_created_as_one() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "André", false)).unwrap();
    db.with(|c| profiles::create(c, "Mia", true)).unwrap();
    let listed = db.with(profiles::list).unwrap();
    let flags: Vec<(String, bool)> = listed.into_iter().map(|p| (p.name, p.kids)).collect();
    assert_eq!(flags, vec![("André".into(), false), ("Mia".into(), true)]);
}

#[test]
fn the_record_carries_kids_only_on_the_kids_profile() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "André", false)).unwrap();
    db.with(|c| profiles::create(c, "Mia", true)).unwrap();
    let record = db.with(|c| export_record(c, "phone")).unwrap();
    let written = serde_json::to_value(&record).unwrap();
    let profiles = written["profiles"].as_array().unwrap();
    let mia = profiles.iter().find(|p| p["name"] == "Mia").unwrap();
    let andre = profiles.iter().find(|p| p["name"] == "André").unwrap();
    assert_eq!(mia["kids"], serde_json::Value::Bool(true));
    assert!(andre.get("kids").is_none());
}

#[test]
fn an_import_upgrades_an_existing_profile_and_never_downgrades() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "Mia", false)).unwrap();
    assert_eq!(db.with(|c| import_merged(c, &merged("Mia", true))).unwrap(), 1);
    assert!(db.with(profiles::list).unwrap()[0].kids);

    assert_eq!(db.with(|c| import_merged(c, &merged("Mia", false))).unwrap(), 0);
    assert!(db.with(profiles::list).unwrap()[0].kids);
}

#[test]
fn an_import_creates_an_unmet_viewer_with_the_flag() {
    let (_dir, db) = db();
    db.with(|c| import_merged(c, &merged("Ben", true))).unwrap();
    assert!(db.with(profiles::list).unwrap()[0].kids);
}
```

(`StateDb::with` turns the closure's `rusqlite::Result<T>` into `Option<T>` — `lists_tests.rs:5-10` unwraps `db.with(|conn| profiles::create(…))` twice for that reason — so one `.unwrap()` above yields the closure's value.)

Also extend `migration_tests.rs`'s column check (see `columns(conn, table)` at `:111`): after migrating a populated v1 file, `columns(&conn, "profiles")` contains `"kids"` and every existing profile reads `kids == false`.

- [ ] **Step 2: Run to verify they fail**

Run: `cargo test -p mediagram-core kids_profile`
Expected: FAIL to compile — `profiles::create` takes 2 arguments; `Profile` has no field `kids`.

- [ ] **Step 3: Implement**

`schema.rs` — append a group:

```rust
    // v3: a kids profile sees only titles rated for children. One fact about
    // the profile, set when it is made; every existing profile is ordinary.
    &["ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0"],
```

Update the module doc's version prose if it names the current version.

`profiles.rs`:

```rust
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
    /// Sees only titles rated FSK 12 or under, or marked for Kids by hand.
    /// Defaulted in the generated Kotlin (uniffi 0.32 supports field
    /// defaults), so existing `Profile(id, name)` call sites keep compiling.
    #[uniffi(default = false)]
    pub kids: bool,
}

pub fn list(conn: &Connection) -> rusqlite::Result<Vec<Profile>> {
    let mut stmt = conn.prepare("SELECT id, name, kids FROM profiles ORDER BY created_at")?;
    let rows = stmt.query_map([], |row| {
        Ok(Profile {
            id: row.get(0)?,
            name: row.get(1)?,
            kids: row.get::<_, i64>(2)? != 0,
        })
    })?;
    rows.collect()
}

pub fn create(conn: &Connection, name: &str, kids: bool) -> rusqlite::Result<Option<Profile>> {
    let Some(clean) = clean_name(name) else {
        return Ok(None);
    };
    let profile = Profile {
        id: ulid::Ulid::new().to_string(),
        name: clean,
        kids,
    };
    conn.execute(
        "INSERT INTO profiles(id, name, created_at, kids) VALUES (?1, ?2, ?3, ?4)",
        params![profile.id, profile.name, now_ms(), i64::from(kids)],
    )?;
    Ok(Some(profile))
}
```

`profile_named` / `profile_named_with_creation` gain `kids: bool` and return `(String, bool, bool)` = (id, created, is_kids_now):

```rust
pub(super) fn profile_named_with_creation(
    conn: &Connection,
    name: &str,
    display_name: Option<&str>,
    kids: bool,
) -> rusqlite::Result<Option<(String, bool, bool)>> {
    let Some(wanted) = normal_name(name) else {
        return Ok(None);
    };
    for profile in list(conn)? {
        if normal_name(&profile.name).as_deref() == Some(wanted.as_str()) {
            return Ok(Some((profile.id, false, profile.kids)));
        }
    }
    Ok(create(conn, display_name.unwrap_or(name), kids)?.map(|p| (p.id, true, kids)))
}
```

`profile_named` passes `false` and maps `|(id, _, _)| id`.

`exchange.rs` — `export_record`: `kids: profile.kids,` in the `ProfileState` literal (replacing Task 2's placeholder `false`). `import_merged`:

```rust
        let Some((profile_id, created, already_kids)) = profiles::profile_named_with_creation(
            conn,
            &profile.name,
            Some(&profile.display_name),
            profile.kids,
        )?
        else {
            continue;
        };
        changed += u64::from(created);
        // Another device made this viewer a kids profile. Only ever upgraded:
        // a merge without the flag says nothing, it does not say "not kids".
        if profile.kids && !already_kids {
            conn.execute("UPDATE profiles SET kids = 1 WHERE id = ?1", [&profile_id])?;
            changed += 1;
        }
```

`api/state.rs`:

```rust
    pub async fn create_profile(self: Arc<Self>, name: String, kids: bool) -> Option<profiles::Profile> {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| profiles::create(conn, &name, kids))
                .flatten()
        })
        .await
    }
```

Fix every other caller the compiler reports (`cargo check -p mediagram-core --tests`), passing `false`.

- [ ] **Step 4: Run to verify they pass**

Run: `cargo test -p mediagram-core`
Expected: PASS (all, including `api_surface`, migration and fixture tests).

- [ ] **Step 5: Regenerate the Android bindings**

Run: `scripts/generate-android-bindings.sh`
Expected: `mediagram_core.kt` changes — `Profile` gains `kids: Boolean`, `createProfile(name, kids)`; `git diff --stat android/core/rust` shows only the generated file. (Note: the session's command hook blocks shell lines containing the word "build"; this script name does not contain it.)

- [ ] **Step 6: Commit**

```bash
git add crates/mediagram-core android/core/rust/src/main/kotlin/uniffi
git commit -m "feat(core-state): store, sync and create kids profiles"
```

The Android app does not compile between this commit and Task 8 (which updates `WatchStateRepository.createProfile`). That is acceptable: the repository's hook is **pre-push** (`scripts/check.sh`, enabled by `scripts/install-hooks.sh`), not pre-commit — do not push until Phase 05 is done. Before committing, also run `cargo clippy -p mediagram-core --all-targets -- -D warnings`, which that hook enforces.

## Success criteria

Rust core matches the web: migration keeps profiles ordinary, the flag round-trips through create/list/export/import, upgrades never downgrade, and the Kotlin bindings expose it.
