//! `watched` on the sync record: exporting both a live mark and its
//! removal, and taking either back in. Matches
//! `web/src/state/watched-exchange.ts`.
//!
//! A live mark and its removal are exclusive by the time they reach here:
//! `merge::watched::reconcile` has already decided, per title, which one a
//! merge agreed on. `import_watched`/`import_unwatched` therefore stay
//! single-kind writers, the same shape `import_progress` already is.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::{UnwatchedRow, WatchedRow};

struct Standing {
    updated_at: f64,
    removed: bool,
}

fn standing_for(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
) -> rusqlite::Result<Option<Standing>> {
    let row: Option<(i64, Option<i64>)> = conn
        .query_row(
            "SELECT finished_at, removed_at FROM watched WHERE profile_id = ?1 AND set_id = ?2",
            params![profile_id, set_id],
            |r| Ok((r.get(0)?, r.get(1)?)),
        )
        .optional()?;
    Ok(row.map(|(finished_at, removed_at)| Standing {
        updated_at: removed_at.unwrap_or(finished_at) as f64,
        removed: removed_at.is_some(),
    }))
}

/// Every `watched` row this profile has, live and removed, for the wire.
pub fn export_watched(
    conn: &Connection,
    profile_id: &str,
) -> rusqlite::Result<(Vec<WatchedRow>, Vec<UnwatchedRow>)> {
    let mut stmt = conn.prepare(
        "SELECT set_id, finished_at, removed_at FROM watched WHERE profile_id = ?1",
    )?;
    let rows: Vec<(String, i64, Option<i64>)> = stmt
        .query_map([profile_id], |row| {
            Ok((row.get(0)?, row.get(1)?, row.get(2)?))
        })?
        .collect::<rusqlite::Result<_>>()?;

    let mut watched = Vec::new();
    let mut unwatched = Vec::new();
    for (set_id, finished_at, removed_at) in rows {
        match removed_at {
            Some(removed_at) => unwatched.push(UnwatchedRow {
                set_id,
                updated_at: removed_at as f64,
                last_finished_at: finished_at as f64,
            }),
            None => watched.push(WatchedRow {
                set_id,
                updated_at: finished_at as f64,
            }),
        }
    }
    Ok((watched, unwatched))
}

/// A completion supersedes a stale local position whether or not it is
/// news: a device that already knew of it may still hold a position
/// another device has only now reported. Applied unconditionally, before
/// the standing check decides whether the mark itself is written.
pub fn import_watched(conn: &Connection, profile_id: &str, rows: &[WatchedRow]) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        changed += conn.execute(
            "DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3",
            params![profile_id, row.set_id, row.updated_at as i64],
        )? as u64;

        let standing = standing_for(conn, profile_id, &row.set_id)?;
        if standing.is_some_and(|s| s.updated_at >= row.updated_at) {
            continue;
        }
        conn.execute(
            "INSERT INTO watched(profile_id, set_id, finished_at, removed_at) VALUES (?1, ?2, ?3, NULL)
               ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at, removed_at = NULL",
            params![profile_id, row.set_id, row.updated_at as i64],
        )?;
        changed += 1;
    }
    Ok(changed)
}

/// `last_finished_at` is the completion this removal took the mark from,
/// so it supersedes a stale local position the same way a live
/// completion's own time would — `merge.rs` already decided a rewatch made
/// since survives.
pub fn import_unwatched(
    conn: &Connection,
    profile_id: &str,
    rows: &[UnwatchedRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        changed += conn.execute(
            "DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3",
            params![profile_id, row.set_id, row.last_finished_at as i64],
        )? as u64;

        let standing = standing_for(conn, profile_id, &row.set_id)?;
        // `merge::watched::reconcile` gives an exact tie to the removal, not
        // a device-id tie-break, whenever the standing fact is a *live*
        // mark — so this must skip one only when that live mark is strictly
        // newer. Standing already a removal is the ordinary same-kind case,
        // where a tie changes nothing either way.
        let outrun = standing.is_some_and(|s| {
            if s.removed {
                s.updated_at >= row.updated_at
            } else {
                s.updated_at > row.updated_at
            }
        });
        if outrun {
            continue;
        }
        conn.execute(
            "INSERT INTO watched(profile_id, set_id, finished_at, removed_at) VALUES (?1, ?2, ?3, ?4)
               ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at, removed_at = excluded.removed_at",
            params![
                profile_id,
                row.set_id,
                row.last_finished_at as i64,
                row.updated_at as i64
            ],
        )?;
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "watched_exchange_tests.rs"]
mod tests;
