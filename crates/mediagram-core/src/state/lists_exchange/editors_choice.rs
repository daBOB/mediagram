//! The household's editor's choice on the sync record — the same shape
//! `super::export_kids`/`super::import_kids` give the Kids table, split into
//! its own file only to keep `lists_exchange.rs` under the line limit.

use rusqlite::{Connection, OptionalExtension, params};

use crate::state::record::ListRow;

use super::to_list_row;

/// The household's editor's choice marks, tombstones included — everything
/// the wire needs to say. `editors_choice` is the live-only half of
/// this.
pub fn export_editors_choice(conn: &Connection) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt = conn.prepare("SELECT set_id, marked_at, removed_at FROM editors_choice")?;
    let rows = stmt.query_map([], |row| {
        Ok(to_list_row(row.get(0)?, row.get(1)?, row.get(2)?))
    })?;
    rows.collect()
}

/// Takes in the kept editor's-choice marks. Corrective, like everything
/// `import_merged` calls: a row this device already holds newer news about
/// is left alone.
pub fn import_editors_choice(conn: &Connection, rows: &[ListRow]) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<i64> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END FROM editors_choice WHERE set_id = ?1",
                [&row.set_id],
                |r| r.get(0),
            )
            .optional()?;
        if standing.is_some_and(|at| at as f64 >= row.updated_at) {
            continue;
        }

        if row.removed {
            conn.execute(
                "INSERT INTO editors_choice(set_id, marked_at, removed_at) VALUES (?1, ?2, ?2)
                   ON CONFLICT(set_id) DO UPDATE SET removed_at = excluded.removed_at",
                params![row.set_id, row.updated_at as i64],
            )?;
        } else {
            conn.execute(
                "INSERT INTO editors_choice(set_id, marked_at, removed_at) VALUES (?1, ?2, NULL)
                   ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL",
                params![row.set_id, row.updated_at as i64],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::editors_choice::{editors_choice, set_editors_choice};
    use crate::state::schema;

    fn conn() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        for stmt in schema::migrations_up_to(schema::VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        conn
    }

    /// Not scoped to a profile — see `schema.rs`'s v5 — the same shape
    /// `kids_import_is_not_scoped_to_a_profile` proves for Kids.
    #[test]
    fn import_is_not_scoped_to_a_profile() {
        let conn = conn();
        let mark = ListRow { set_id: "01A".into(), updated_at: 1000.0, removed: false };

        import_editors_choice(&conn, &[mark]).unwrap();

        assert_eq!(editors_choice(&conn).unwrap(), Some("01A".to_string()));
    }

    #[test]
    fn a_tombstone_export_round_trips_through_import() {
        let source = conn();
        set_editors_choice(&source, "01A", true).unwrap();
        set_editors_choice(&source, "01A", false).unwrap();

        let exported = export_editors_choice(&source).unwrap();
        assert_eq!(exported.len(), 1);
        assert!(exported[0].removed);

        let fresh = conn();
        import_editors_choice(&fresh, &exported).unwrap();
        assert_eq!(editors_choice(&fresh).unwrap(), None);
    }

    /// Newer news about the same title already held locally must not be
    /// clobbered by an older import — the corrective rule every import in
    /// this module follows.
    #[test]
    fn an_older_import_for_a_title_already_held_is_ignored() {
        let conn = conn();
        set_editors_choice(&conn, "01A", true).unwrap();
        let local_mark = export_editors_choice(&conn).unwrap()[0].clone();

        let stale = ListRow { set_id: "01A".into(), updated_at: local_mark.updated_at - 1.0, removed: true };
        import_editors_choice(&conn, &[stale]).unwrap();

        assert_eq!(editors_choice(&conn).unwrap(), Some("01A".to_string()));
    }
}
