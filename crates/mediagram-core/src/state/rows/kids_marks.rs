//! Kids marks: titles a grown-up has said are fine for children — "from 6"
//! or "from 12" — for everyone on this player. Split out of `rows.rs` to
//! keep it under the line limit; `rows` re-exports all three.

use rusqlite::{Connection, params};

use crate::state::profiles::now_ms;
use crate::state::record::MAX_STAMP;

/// The titles marked as a child's, for everyone on this player, at either
/// age. Not scoped to a profile: see `schema.rs` on why.
pub fn kids(conn: &Connection) -> rusqlite::Result<Vec<String>> {
    let mut stmt =
        conn.prepare("SELECT set_id FROM kids WHERE removed_at IS NULL ORDER BY marked_at DESC")?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// The live marks made "from 6" — the part of `kids` a kid limited to FSK 6
/// may see.
pub fn kids_from_six(conn: &Connection) -> rusqlite::Result<Vec<String>> {
    let mut stmt = conn.prepare(
        "SELECT set_id FROM kids WHERE removed_at IS NULL AND age = 6 ORDER BY marked_at DESC",
    )?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// Marks `set_id` "from 6" (`Some(6)`) or "from 12" (any other age — the
/// stricter reading, as the wire has it), or takes the mark off (`None`).
///
/// A removal is a tombstone, not a delete — the same reason and shape as
/// `set_watchlisted`. Marking again at the age a mark has changes nothing;
/// a new age is a new mark, so another device hears of it. Every change is
/// stamped at least one millisecond past whatever the row carries, as
/// `set_watched`'s are: an imported row can hold a clock running ahead of
/// this one. It matters more here than there — the merge settles a mark
/// "from 6" tied with one without as an older build's echo, so a change of
/// age that kept the old stamp would lose to the very mark it replaced.
/// Never past `MAX_STAMP`, the latest stamp a peer keeps.
pub fn set_kids(conn: &Connection, set_id: &str, age: Option<u8>) -> rusqlite::Result<()> {
    let latest = MAX_STAMP as i64 - 1;
    let Some(age) = age else {
        conn.execute(
            "UPDATE kids SET removed_at = MAX(?2, MIN(marked_at, ?3) + 1)
               WHERE set_id = ?1 AND removed_at IS NULL",
            params![set_id, now_ms(), latest],
        )?;
        return Ok(());
    };
    conn.execute(
        "INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES (?1, ?2, NULL, ?3)
           ON CONFLICT(set_id) DO UPDATE SET
             marked_at = MAX(excluded.marked_at, MIN(COALESCE(removed_at, marked_at), ?4) + 1),
             removed_at = NULL, age = excluded.age
             WHERE removed_at IS NOT NULL OR age IS NOT excluded.age",
        params![set_id, now_ms(), (age == 6).then_some(6), latest],
    )?;
    Ok(())
}

#[cfg(test)]
#[path = "kids_marks_tests.rs"]
mod tests;
