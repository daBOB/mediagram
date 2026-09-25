//! The household's editor's choice: the title the magazine home page leads
//! its features with. Not scoped to a profile — see `schema.rs`'s v5 on
//! why — and shaped exactly like `rows::kids`/`rows::set_kids`, split into
//! its own file only to keep `rows.rs` under the line limit.
//!
//! A port of `web/src/state/store.ts`'s `editorsChoice`/`setEditorsChoice`.

use rusqlite::{Connection, OptionalExtension, params};

use super::profiles::now_ms;

/// The newest live mark, or `None`.
///
/// One pick, but not one row. Marking a title here retires the last pick,
/// yet a merge can still bring two live marks from two devices; the newest
/// wins, which is what either viewer last meant.
pub fn editors_choice(conn: &Connection) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT set_id FROM editors_choice WHERE removed_at IS NULL ORDER BY marked_at DESC, set_id LIMIT 1",
        [],
        |row| row.get(0),
    )
    .optional()
}

/// Pins `set_id` as the editor's choice, retiring every other pick, or
/// unpins — which means "no pick", so it retires every live mark, including
/// any a merge brought in that this device never showed. Retired picks are
/// tombstones, so another device learns of the change instead of
/// resurrecting the old pick.
pub fn set_editors_choice(conn: &Connection, set_id: &str, marked: bool) -> rusqlite::Result<()> {
    let now = now_ms();
    if marked {
        conn.execute(
            "UPDATE editors_choice SET removed_at = ?2 WHERE set_id <> ?1 AND removed_at IS NULL",
            params![set_id, now],
        )?;
        conn.execute(
            "INSERT INTO editors_choice(set_id, marked_at, removed_at) VALUES (?1, ?2, NULL)
               ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL",
            params![set_id, now],
        )?;
    } else {
        conn.execute(
            "UPDATE editors_choice SET removed_at = ?1 WHERE removed_at IS NULL",
            params![now],
        )?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn conn() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        for stmt in super::super::schema::migrations_up_to(super::super::schema::VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        conn
    }

    #[test]
    fn nothing_pinned_reads_as_no_pick() {
        assert_eq!(editors_choice(&conn()).unwrap(), None);
    }

    #[test]
    fn pinning_one_title_retires_the_last_pick() {
        let conn = conn();
        set_editors_choice(&conn, "a", true).unwrap();
        set_editors_choice(&conn, "b", true).unwrap();

        assert_eq!(editors_choice(&conn).unwrap(), Some("b".to_string()));

        let live: i64 = conn
            .query_row("SELECT COUNT(*) FROM editors_choice WHERE removed_at IS NULL", [], |r| r.get(0))
            .unwrap();
        assert_eq!(live, 1, "only the newest pick stays live");
    }

    #[test]
    fn unpinning_retires_every_live_mark() {
        let conn = conn();
        set_editors_choice(&conn, "a", true).unwrap();
        // A second live row a merge could have brought in, that this device
        // never showed — `set_editors_choice(false)` must retire it too.
        conn.execute(
            "INSERT INTO editors_choice(set_id, marked_at, removed_at) VALUES ('b', 500, NULL)",
            [],
        )
        .unwrap();

        set_editors_choice(&conn, "a", false).unwrap();

        assert_eq!(editors_choice(&conn).unwrap(), None);
        let live: i64 = conn
            .query_row("SELECT COUNT(*) FROM editors_choice WHERE removed_at IS NULL", [], |r| r.get(0))
            .unwrap();
        assert_eq!(live, 0);
    }

    #[test]
    fn re_pinning_a_retired_title_clears_its_tombstone() {
        let conn = conn();
        set_editors_choice(&conn, "a", true).unwrap();
        set_editors_choice(&conn, "a", false).unwrap();
        set_editors_choice(&conn, "a", true).unwrap();

        assert_eq!(editors_choice(&conn).unwrap(), Some("a".to_string()));
    }
}
