//! Two rows of one person made one: what an older build leaves when it took
//! a renamed viewer in under the new name beside the row it had under the
//! old. A port of `web/src/state/profiles-fold.ts`.

use rusqlite::{Connection, params};

/// Every table whose rows belong to one profile.
const OWN_ROWS: [&str; 7] =
    ["progress", "watched", "watchlist", "collections", "preferences", "stats_titles", "stats_days"];

/// Moves `from`'s rows to `into`, points whatever named it at `into`, and
/// deletes it.
///
/// A key `into` already has stays its own: this device's export was part of
/// the merge, so the rows import that follows writes the newer of the two.
/// Collections move whole, ids and all, so the merge's copy of one finds it
/// under `into` rather than clashing with the id. Role columns are not
/// copied: the role import that follows applies whichever value the merge
/// chose.
pub(super) fn fold(conn: &Connection, from: &str, into: &str) -> rusqlite::Result<()> {
    for table in OWN_ROWS {
        conn.execute(&format!("UPDATE OR IGNORE {table} SET profile_id = ?2 WHERE profile_id = ?1"), params![from, into])?;
    }
    conn.execute("UPDATE profiles SET parent_id = ?2 WHERE parent_id = ?1", params![from, into])?;
    conn.execute("UPDATE households SET admin_id = ?2 WHERE admin_id = ?1", params![from, into])?;
    // The web's browser remembers its own choice; this device's is here.
    conn.execute(
        "UPDATE state_meta SET value = ?2 WHERE key = 'chosen_profile' AND value = ?1",
        params![from, into],
    )?;
    // Its leftovers cascade: each one a key `into` already has.
    conn.execute("DELETE FROM profiles WHERE id = ?1", [from])?;
    Ok(())
}
