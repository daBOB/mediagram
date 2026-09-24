//! Who's watching: list, create, and which one is chosen — a port of the
//! profile half of `web/src/state/store.ts`.
//!
//! Rename and delete are deferred (see the plan's decision 4): a sync can
//! only ever add a profile from another device's document, never carry a
//! rename or a deletion, so offering them here would let this device drift
//! from what the others still believe.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::normal_name;

const CHOSEN_KEY: &str = "chosen_profile";
/// How long a name may be. Long enough for a sentence, short enough to show
/// — the same cap `store.ts`'s `MAX_NAME` uses.
const MAX_NAME: usize = 120;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
}

pub fn list(conn: &Connection) -> rusqlite::Result<Vec<Profile>> {
    let mut stmt = conn.prepare("SELECT id, name FROM profiles ORDER BY created_at")?;
    let rows = stmt.query_map([], |row| {
        Ok(Profile {
            id: row.get(0)?,
            name: row.get(1)?,
        })
    })?;
    rows.collect()
}

/// `None` for a name with nothing left after trimming — never a stored
/// profile with no way to show it.
pub fn create(conn: &Connection, name: &str) -> rusqlite::Result<Option<Profile>> {
    let Some(clean) = clean_name(name) else {
        return Ok(None);
    };
    let profile = Profile {
        id: ulid::Ulid::new().to_string(),
        name: clean,
    };
    conn.execute(
        "INSERT INTO profiles(id, name, created_at) VALUES (?1, ?2, ?3)",
        params![profile.id, profile.name, now_ms()],
    )?;
    Ok(Some(profile))
}

pub fn exists(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    conn.query_row("SELECT 1 FROM profiles WHERE id = ?1", [id], |_| Ok(()))
        .optional()
        .map(|r| r.is_some())
}

/// This install's remembered "who's watching" — cleared implicitly if the
/// chosen profile was later deleted, since `exists` is checked on read
/// rather than trusted at write time.
pub fn chosen(conn: &Connection) -> rusqlite::Result<Option<String>> {
    let id: Option<String> = conn
        .query_row(
            "SELECT value FROM state_meta WHERE key = ?1",
            [CHOSEN_KEY],
            |row| row.get(0),
        )
        .optional()?;
    match id {
        Some(id) if exists(conn, &id)? => Ok(Some(id)),
        _ => Ok(None),
    }
}

/// Records the choice. `false` when `id` names no profile — the caller
/// asked to choose someone who is not there, so nothing was remembered.
pub fn choose(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    if !exists(conn, id)? {
        return Ok(false);
    }
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2)
           ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![CHOSEN_KEY, id],
    )?;
    Ok(true)
}

/// This player's id for a viewer, made if it has never seen them.
///
/// Made rather than skipped, because the first thing a second machine knows
/// about a viewer is a document written by the first — refusing to create
/// one would mean sync could only ever flow towards a machine that had
/// already met them.
pub fn profile_named(
    conn: &Connection,
    name: &str,
    display_name: Option<&str>,
) -> rusqlite::Result<Option<String>> {
    Ok(profile_named_with_creation(conn, name, display_name)?.map(|(id, _)| id))
}

/// Resolves an id and reports whether this call created its profile row.
pub(super) fn profile_named_with_creation(
    conn: &Connection,
    name: &str,
    display_name: Option<&str>,
) -> rusqlite::Result<Option<(String, bool)>> {
    let Some(wanted) = normal_name(name) else {
        return Ok(None);
    };
    for profile in list(conn)? {
        if normal_name(&profile.name).as_deref() == Some(wanted.as_str()) {
            return Ok(Some((profile.id, false)));
        }
    }
    // Created from the spelling somebody typed, never from the normalised
    // identity — that would greet a viewer as "andré" on every new machine.
    Ok(create(conn, display_name.unwrap_or(name))?.map(|p| (p.id, true)))
}

/// A name with its edges trimmed and internal whitespace collapsed, or
/// `None` when there is nothing left.
pub(super) fn clean_name(name: &str) -> Option<String> {
    let collapsed = name.split_whitespace().collect::<Vec<_>>().join(" ");
    let clean: String = collapsed.chars().take(MAX_NAME).collect();
    (!clean.is_empty()).then_some(clean)
}

pub(crate) fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::StateDb;

    fn db() -> (tempfile::TempDir, StateDb) {
        let dir = tempfile::tempdir().unwrap();
        let db = StateDb::new(dir.path().to_path_buf());
        (dir, db)
    }

    #[test]
    fn creating_and_listing_round_trips_a_name() {
        let (_dir, db) = db();
        db.with(|conn| create(conn, "André")).unwrap();
        let names: Vec<String> = db.with(list).unwrap().into_iter().map(|p| p.name).collect();
        assert_eq!(names, vec!["André".to_string()]);
    }

    #[test]
    fn a_blank_name_creates_nothing() {
        let (_dir, db) = db();
        assert_eq!(db.with(|conn| create(conn, "   ")).unwrap(), None);
    }

    #[test]
    fn choosing_an_unknown_id_is_reported_false_and_remembers_nothing() {
        let (_dir, db) = db();
        assert!(!db.with(|conn| choose(conn, "nope")).unwrap());
        assert_eq!(db.with(chosen).unwrap(), None);
    }

    #[test]
    fn choosing_a_real_profile_is_remembered() {
        let (_dir, db) = db();
        let id = db.with(|conn| create(conn, "André")).unwrap().unwrap().id;
        db.with(|conn| choose(conn, &id)).unwrap();
        assert_eq!(db.with(chosen).unwrap(), Some(id));
    }

    /// A second machine's document mentions a viewer this one has never
    /// seen: `profile_named` has to create them rather than drop the sync.
    #[test]
    fn profile_named_creates_an_unseen_viewer_and_reuses_them_after() {
        let (_dir, db) = db();
        let first = db
            .with(|conn| profile_named(conn, "andré", Some("André")))
            .unwrap()
            .unwrap();
        let second = db
            .with(|conn| profile_named(conn, "ANDRÉ", Some("ANDRÉ")))
            .unwrap()
            .unwrap();
        assert_eq!(
            first, second,
            "the same viewer, spelled differently, is one profile"
        );
    }
}
