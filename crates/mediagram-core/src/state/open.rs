//! Opening `state.db`: its pragmas, its migrations and the repairs no
//! migration history explains. Split out of `mod.rs` to keep it under the
//! line limit; `StateDb::with` is the only caller.

use std::path::Path;

use rusqlite::Connection;

use super::{STATE_FILE, repair, schema};

/// Opens (creating if needed) `<data_dir>/state.db`, enables WAL and foreign
/// keys, and applies every migration this file has not had. The repair runs
/// first: a later migration may read the very column it adds.
pub(super) fn open(data_dir: &Path) -> anyhow::Result<Connection> {
    std::fs::create_dir_all(data_dir)?;
    let conn = Connection::open(data_dir.join(STATE_FILE))?;
    conn.pragma_update(None, "journal_mode", "WAL")?;
    repair::add_missing_kids_column(&conn)?;
    migrate(&conn)?;
    conn.pragma_update(None, "foreign_keys", true)?;
    Ok(conn)
}

/// Applies every statement past the version `PRAGMA user_version` records,
/// then advances it — both in one transaction, so a migration killed half
/// way leaves the version it started at rather than a shape matching no
/// version at all. The same shape `details.rs`'s `migrate_from` uses for the
/// sidecar description store.
pub(super) fn migrate(conn: &Connection) -> anyhow::Result<()> {
    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0))?;
    if at >= schema::VERSION {
        return Ok(());
    }

    let applied = schema::migrations_up_to(at).len();
    conn.execute_batch("BEGIN")?;
    for statement in schema::migrations_up_to(schema::VERSION)
        .into_iter()
        .skip(applied)
    {
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
