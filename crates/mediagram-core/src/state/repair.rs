//! Shapes a state file can be in that no single migration history explains.

use rusqlite::Connection;

/// Adds `profiles.kids` to a file that reached version 3 without it.
///
/// A pre-release build numbered its `preferences` table as step 3, where this
/// schema's step 3 is the `kids` column. A file that build migrated reports
/// version 3, so the runner skips the column as already applied, and every
/// profile read would fail on it. Checked on each open, before the
/// migrations — a later one reads `kids` and would roll back on exactly this
/// file, leaving it unopenable. Below version 3 the column is that
/// migration's to add, and adding it here first would make it fail as a
/// duplicate; a file that has it is untouched.
pub(super) fn add_missing_kids_column(conn: &Connection) -> rusqlite::Result<()> {
    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0))?;
    let present = conn
        .prepare("SELECT 1 FROM pragma_table_info('profiles') WHERE name = 'kids'")?
        .exists([])?;
    if at >= 3 && !present {
        conn.execute(
            "ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0",
            [],
        )?;
    }
    Ok(())
}
