//! Own writes of households, and of a grown-up moving between them — the
//! web's `households-write.ts`; the decisions are `manage`'s. Every field is
//! dated like a PIN or a limit (`role_writes`), so a household made or
//! changed here reaches the other devices and beats what it replaced there.

use rusqlite::{Connection, params};

use crate::state::profiles::role_writes::LAST_STAMP;

/// A new household, its name and admin dated now.
pub(super) fn insert(conn: &Connection, id: &str, name: &str, admin_id: &str, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES (?1, ?2, ?3, ?4, ?3)",
        params![id, name, now, admin_id],
    )?;
    Ok(())
}

pub(super) fn name(conn: &Connection, id: &str, name: &str, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE households SET name = ?2, name_updated_at = MAX(?3, MIN(name_updated_at, ?4) + 1) WHERE id = ?1",
        params![id, name, now, LAST_STAMP],
    )?;
    Ok(())
}

/// Only onto a household row that is there: one that lands nowhere fails,
/// and the caller's transaction rolls back.
pub(super) fn admin(conn: &Connection, household_id: &str, profile_id: &str, now: i64) -> rusqlite::Result<()> {
    let written = conn.execute(
        "UPDATE households SET admin_id = ?2, admin_updated_at = MAX(?3, MIN(admin_updated_at, ?4) + 1) WHERE id = ?1",
        params![household_id, profile_id, now, LAST_STAMP],
    )?;
    if written == 0 {
        let none = rusqlite::ffi::Error::new(rusqlite::ffi::SQLITE_NOTFOUND);
        return Err(rusqlite::Error::SqliteFailure(none, Some(format!("no household {household_id} to give an admin"))));
    }
    Ok(())
}

/// A grown-up and each kid it made, into `household_id`, every one dated.
pub(super) fn move_grown_up(conn: &Connection, profile_id: &str, household_id: &str, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE profiles SET household_id = ?2, household_updated_at = MAX(?3, MIN(household_updated_at, ?4) + 1)
          WHERE id = ?1 OR (parent_id = ?1 AND kids = 1)",
        params![profile_id, household_id, now, LAST_STAMP],
    )?;
    Ok(())
}

/// Final: removal is never cleared, here or by sync.
pub(super) fn remove(conn: &Connection, id: &str) -> rusqlite::Result<()> {
    conn.execute("UPDATE households SET removed = 1 WHERE id = ?1", [id])?;
    Ok(())
}
