//! Turning this device's rows into a document for the channel (03), and
//! taking one back in. A port of `web/src/state/store.ts`'s `exportRecord`
//! and `importMerged` (`store.ts:350-440`).

use rusqlite::{Connection, OptionalExtension, params};

use super::lists_exchange;
use super::merge::MergedState;
use super::profiles;
use super::record::{ProfileState, ProgressRow, SYNC_FORMAT, SyncRecord, WatchedRow};
use super::rows;

/// What this device has to say about where things were left off.
///
/// Includes every profile, because a document belongs to a device rather
/// than to whoever happens to be watching on it. A snapshot retains progress
/// and watched timestamps; this record also carries the list timestamps and
/// removal tombstones needed to reconcile changes between devices.
pub fn export_record(conn: &Connection, device: &str) -> rusqlite::Result<SyncRecord> {
    let mut profiles = Vec::new();
    for profile in super::profiles::list(conn)? {
        let progress = rows::progress_for(conn, &profile.id)?
            .into_iter()
            .map(|row| ProgressRow {
                set_id: row.set_id,
                at: row.at,
                duration: row.duration,
                updated_at: row.updated_at as f64,
            })
            .collect();
        let watched = rows::watched_for(conn, &profile.id)?
            .into_iter()
            .map(|row| WatchedRow {
                set_id: row.set_id,
                updated_at: row.finished_at as f64,
            })
            .collect();
        let watchlist = lists_exchange::export_watchlist(conn, &profile.id)?;
        let collections = lists_exchange::export_collections(conn, &profile.id)?;
        profiles.push(ProfileState {
            name: profile.name,
            local_id: Some(profile.id),
            progress,
            watched,
            watchlist,
            collections,
        });
    }
    let kids = lists_exchange::export_kids(conn)?;
    Ok(SyncRecord {
        format: SYNC_FORMAT,
        device: device.to_string(),
        written_at: profiles::now_ms() as f64,
        profiles,
        kids,
    })
}

/// Takes in what the devices agreed on.
///
/// **Corrective, never wholesale.** A row absent from the merge is left
/// alone rather than deleted: a sync that reached only some of the devices
/// would otherwise erase everything the missing ones knew. The only thing
/// that removes a position is a completion that supersedes it, which is the
/// one removal the format can actually express.
///
/// The entire import commits together, including newly created profiles.
/// Returns the number of committed changes; an error leaves every row alone.
pub fn import_merged(conn: &Connection, merged: &MergedState) -> rusqlite::Result<u64> {
    let transaction = conn.unchecked_transaction()?;
    let conn = &transaction;
    let mut changed = lists_exchange::import_kids(conn, &merged.kids)?;
    for profile in &merged.profiles {
        // The identity to match on, and the spelling to create with.
        let Some((profile_id, created)) = profiles::profile_named_with_creation(
            conn,
            &profile.name,
            Some(&profile.display_name),
        )?
        else {
            continue;
        };
        changed += u64::from(created);

        for row in &profile.progress {
            changed += import_progress(conn, &profile_id, row)?;
        }
        for row in &profile.watched {
            changed += import_watched(conn, &profile_id, row)?;
        }
        changed += lists_exchange::import_watchlist(conn, &profile_id, &profile.watchlist)?;
        changed += lists_exchange::import_collections(conn, &profile_id, &profile.collections)?;
    }
    transaction.commit()?;
    Ok(changed)
}

fn import_progress(
    conn: &Connection,
    profile_id: &str,
    row: &ProgressRow,
) -> rusqlite::Result<u64> {
    let standing: Option<i64> = conn
        .query_row(
            "SELECT updated_at FROM progress WHERE profile_id = ?1 AND set_id = ?2",
            params![profile_id, row.set_id],
            |r| r.get(0),
        )
        .optional()?;
    if standing.is_some_and(|at| at as f64 >= row.updated_at) {
        return Ok(0);
    }
    conn.execute(
        "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET
             at_seconds = excluded.at_seconds,
             duration = excluded.duration,
             updated_at = excluded.updated_at",
        params![
            profile_id,
            row.set_id,
            row.at,
            row.duration,
            row.updated_at as i64
        ],
    )?;
    Ok(1)
}

/// The completion's tombstone half. The position goes whether or not the
/// completion itself is news: a device that already knew of it may still be
/// holding a position another device has only now reported.
fn import_watched(conn: &Connection, profile_id: &str, row: &WatchedRow) -> rusqlite::Result<u64> {
    let mut changed = conn.execute(
        "DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3",
        params![profile_id, row.set_id, row.updated_at as i64],
    )? as u64;

    let standing: Option<i64> = conn
        .query_row(
            "SELECT finished_at FROM watched WHERE profile_id = ?1 AND set_id = ?2",
            params![profile_id, row.set_id],
            |r| r.get(0),
        )
        .optional()?;
    if standing.is_some_and(|at| at as f64 >= row.updated_at) {
        return Ok(changed);
    }
    conn.execute(
        "INSERT INTO watched(profile_id, set_id, finished_at) VALUES (?1, ?2, ?3)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at",
        params![profile_id, row.set_id, row.updated_at as i64],
    )?;
    changed += 1;
    Ok(changed)
}

#[cfg(test)]
#[path = "exchange_tests.rs"]
mod tests;
