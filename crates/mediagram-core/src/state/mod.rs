//! The player's own database: positions, watched marks, the watchlist,
//! collections and Kids, synced with the web player through the channel
//! (03).
//!
//! Deliberately not the catalog. That one belongs to the uploader, is opened
//! read-only, and is replaced wholesale when a package refresh lands — a
//! watch position written there would be destroyed by the next catalog
//! update and would have had no business being there in the first place. So
//! this owns one file, `state.db`, kept beside `catalog/` rather than inside
//! it, for the same reason the web keeps its state database separate
//! (`web/src/state/schema.ts:1-12`).

pub mod exchange;
pub mod lists;
pub mod merge;
pub mod profiles;
pub mod record;
pub mod rows;
mod schema;

use std::path::{Path, PathBuf};
use std::sync::Mutex;

use rusqlite::Connection;

const STATE_FILE: &str = "state.db";

/// A local store this `Core` owns for the length of the process, opened
/// lazily on the first call that needs it.
///
/// `std::sync::Mutex`, not the async one `Core::state` uses: every write
/// here is a single indexed row, so a critical section is always short, and
/// wrapping rusqlite in async machinery would buy nothing.
pub struct StateDb {
    data_dir: PathBuf,
    conn: Mutex<Option<Connection>>,
}

impl StateDb {
    pub fn new(data_dir: PathBuf) -> Self {
        StateDb { data_dir, conn: Mutex::new(None) }
    }

    /// Runs `f` against the open connection, opening (and migrating) it on
    /// first use. `None` on any failure — the lock poisoned, the file could
    /// not be opened, or `f` itself failed — which is what every write
    /// degrades to and every read reads back as "remembers nothing". Nothing
    /// here may throw to Kotlin: a state directory that cannot be written is
    /// a player that forgets where you were, which is tolerable; one that
    /// refuses to start because of it is not.
    pub(crate) fn with<T>(&self, f: impl FnOnce(&Connection) -> rusqlite::Result<T>) -> Option<T> {
        let mut guard = self.conn.lock().ok()?;
        if guard.is_none() {
            match open(&self.data_dir) {
                Ok(conn) => *guard = Some(conn),
                Err(err) => {
                    eprintln!("state: not remembering anything ({err})");
                    return None;
                }
            }
        }
        let conn = guard.as_ref()?;
        match f(conn) {
            Ok(value) => Some(value),
            Err(err) => {
                eprintln!("state: a read or write did not complete ({err})");
                None
            }
        }
    }
}

/// Opens (creating if needed) `<data_dir>/state.db`, enables WAL and foreign
/// keys, and applies every migration this file has not had.
fn open(data_dir: &Path) -> anyhow::Result<Connection> {
    std::fs::create_dir_all(data_dir)?;
    let conn = Connection::open(data_dir.join(STATE_FILE))?;
    conn.pragma_update(None, "journal_mode", "WAL")?;
    migrate(&conn)?;
    conn.pragma_update(None, "foreign_keys", true)?;
    Ok(conn)
}

/// Applies every statement past the version `PRAGMA user_version` records,
/// then advances it — both in one transaction, so a migration killed half
/// way leaves the version it started at rather than a shape matching no
/// version at all. The same shape `details.rs`'s `migrate_from` uses for the
/// sidecar description store.
fn migrate(conn: &Connection) -> anyhow::Result<()> {
    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0))?;
    if at >= schema::VERSION {
        return Ok(());
    }

    let applied = schema::migrations_up_to(at).len();
    conn.execute_batch("BEGIN")?;
    for statement in schema::migrations_up_to(schema::VERSION).into_iter().skip(applied) {
        if let Err(err) = conn.execute(statement, []) {
            let _ = conn.execute_batch("ROLLBACK");
            return Err(err.into());
        }
    }
    if let Err(err) = conn.pragma_update(None, "user_version", schema::VERSION) {
        let _ = conn.execute_batch("ROLLBACK");
        return Err(err.into());
    }
    conn.execute_batch("COMMIT")?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_fresh_directory_gets_a_readable_store() {
        let dir = tempfile::tempdir().unwrap();
        let db = StateDb::new(dir.path().to_path_buf());
        assert_eq!(db.with(profiles::list).unwrap(), Vec::new());
        assert!(dir.path().join(STATE_FILE).exists());
    }

    /// Opening twice must apply nothing a second time — `CREATE TABLE`
    /// without `IF NOT EXISTS` would fail the replay, and a schema
    /// migration killed half way must not be retried into a broken shape.
    #[test]
    fn reopening_an_already_migrated_store_is_a_no_op() {
        let dir = tempfile::tempdir().unwrap();
        {
            let db = StateDb::new(dir.path().to_path_buf());
            db.with(|conn| profiles::create(conn, "André")).unwrap();
        }
        let db = StateDb::new(dir.path().to_path_buf());
        let names: Vec<String> = db.with(profiles::list).unwrap().into_iter().map(|p| p.name).collect();
        assert_eq!(names, vec!["André".to_string()]);
    }
}
