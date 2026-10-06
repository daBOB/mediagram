//! Watchlist, Kids and collections on the sync record.
//!
//! Converts removal tombstones between SQLite and the wire format, matching
//! `web/src/state/lists-exchange.ts`. The editor's choice and collections
//! are the same shape as Kids and the watchlist respectively, but live in
//! their own files here, split out to keep this one under the line limit.

use rusqlite::{Connection, OptionalExtension, params};

use crate::state::record::ListRow;

mod collections;
mod editors_choice;
pub use collections::{export_collections, import_collections};
pub use editors_choice::{export_editors_choice, import_editors_choice};

/// A row's LWW timestamp is whichever of adding/marking or removing it this
/// store last recorded — the later of the two, since only one is ever set.
pub(super) fn to_list_row(set_id: String, added_or_marked_at: i64, removed_at: Option<i64>) -> ListRow {
    ListRow {
        set_id,
        updated_at: removed_at.unwrap_or(added_or_marked_at) as f64,
        removed: removed_at.is_some(),
        age: None,
    }
}

/// "From 6", said by a live mark only. A tombstone's column can still hold
/// the age the mark had while live — a build from before ages leaves it
/// there — and a removal says nothing about an age.
fn live_age(age: Option<i64>, removed: bool) -> Option<u8> {
    (age == Some(6) && !removed).then_some(6)
}

/// The titles marked as a child's, tombstones included — everything the
/// wire needs to say. `rows::kids` is the live-only half of this. From 12 is
/// said by saying nothing, which is all an older reader, dropping `age`,
/// will hear.
pub fn export_kids(conn: &Connection) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt = conn.prepare("SELECT set_id, marked_at, removed_at, age FROM kids")?;
    let rows = stmt.query_map([], |row| {
        let wire = to_list_row(row.get(0)?, row.get(1)?, row.get(2)?);
        Ok(ListRow { age: live_age(row.get(3)?, wire.removed), ..wire })
    })?;
    rows.collect()
}

pub fn export_watchlist(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt =
        conn.prepare("SELECT set_id, added_at, removed_at FROM watchlist WHERE profile_id = ?1")?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(to_list_row(row.get(0)?, row.get(1)?, row.get(2)?))
    })?;
    rows.collect()
}

/// Takes in the kept marks. Corrective, like everything `import_merged`
/// calls: newer news here is left alone, while at the same moment a row
/// that differs — removed where this is live, or another age — is the
/// winner the merge already chose, and is taken. The age is written with
/// the row, a removal's included: "from 6" on a live mark, none on a
/// tombstone.
pub fn import_kids(conn: &Connection, rows: &[ListRow]) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<(i64, bool, Option<i64>)> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END,
                        removed_at IS NOT NULL, age
                   FROM kids WHERE set_id = ?1",
                [&row.set_id],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .optional()?;
        let age = live_age(row.age.map(i64::from), row.removed);
        let ours = |(at, removed, held): (i64, bool, Option<i64>)| {
            let at = at as f64;
            at > row.updated_at
                || (at == row.updated_at && removed == row.removed && live_age(held, removed) == age)
        };
        if standing.is_some_and(ours) {
            continue;
        }

        if row.removed {
            conn.execute(
                "INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, ?2)
                   ON CONFLICT(set_id) DO UPDATE SET removed_at = excluded.removed_at, age = NULL",
                params![row.set_id, row.updated_at as i64],
            )?;
        } else {
            conn.execute(
                "INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES (?1, ?2, NULL, ?3)
                   ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL,
                     age = excluded.age",
                params![row.set_id, row.updated_at as i64, age],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

pub fn import_watchlist(
    conn: &Connection,
    profile_id: &str,
    rows: &[ListRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<i64> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE added_at END
                   FROM watchlist WHERE profile_id = ?1 AND set_id = ?2",
                params![profile_id, row.set_id],
                |r| r.get(0),
            )
            .optional()?;
        if standing.is_some_and(|at| at as f64 >= row.updated_at) {
            continue;
        }

        if row.removed {
            conn.execute(
                "INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, ?3)
                   ON CONFLICT(profile_id, set_id) DO UPDATE SET removed_at = excluded.removed_at",
                params![profile_id, row.set_id, row.updated_at as i64],
            )?;
        } else {
            conn.execute(
                "INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, NULL)
                   ON CONFLICT(profile_id, set_id) DO UPDATE SET added_at = excluded.added_at, removed_at = NULL",
                params![profile_id, row.set_id, row.updated_at as i64],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "lists_tests.rs"]
mod tests;
