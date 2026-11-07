//! Finding what the channel holds that the local index does not, on an
//! already-`ATTACH`ed connection (schema `channel` beside `main`).

use anyhow::{Context, Result};
use rusqlite::Connection;

/// A channel-only, `complete` set: a candidate to add, with the `(chat_id,
/// message_id)` of every part its status claims is in the channel.
///
/// Re-exported as `merge::Candidate`: `merge_from`'s callers (outside
/// `index`) need it to write the existence check it hands to `keep_if_live`.
#[derive(Debug, Clone)]
pub struct Candidate {
    pub set_id: String,
    pub messages: Vec<(i64, i64)>,
    /// How many parts the set claims, so a set whose parts lack messages is
    /// never taken as present.
    pub part_count: i64,
}

/// Channel-only sets, split into `complete` candidates (with their part
/// messages) and `pending` ids, which are skipped outright: an upload left
/// unfinished on another machine is not the union's to adopt.
pub(super) fn discover(conn: &Connection) -> Result<(Vec<Candidate>, Vec<String>)> {
    let mut stmt = conn
        .prepare(
            "SELECT set_id, status, part_count FROM channel.sets
             WHERE set_id NOT IN (SELECT set_id FROM main.sets) ORDER BY set_id",
        )
        .context("listing channel-only sets")?;
    let rows = stmt
        .query_map([], |row| {
            Ok((
                row.get::<_, String>(0)?,
                row.get::<_, String>(1)?,
                row.get::<_, i64>(2)?,
            ))
        })
        .context("reading channel-only sets")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a channel-only set")?;

    let mut complete = Vec::new();
    let mut pending = Vec::new();
    for (set_id, status, part_count) in rows {
        if status == mlib_spec::schema::SET_COMPLETE {
            let messages = candidate_messages(conn, "channel", &set_id)?;
            complete.push(Candidate {
                set_id,
                messages,
                part_count,
            });
        } else {
            pending.push(set_id);
        }
    }
    Ok((complete, pending))
}

/// Local-only `complete` sets, with the part messages the local index
/// records: what this machine holds that the channel's index does not. Each
/// is either uploaded here and not yet published, or removed on another
/// machine since this one last pulled; only the channel can tell which.
pub(super) fn local_only(conn: &Connection) -> Result<Vec<Candidate>> {
    let mut stmt = conn
        .prepare(
            "SELECT set_id, part_count FROM main.sets
             WHERE status = ?1 AND set_id NOT IN (SELECT set_id FROM channel.sets)
             ORDER BY set_id",
        )
        .context("listing local-only sets")?;
    let rows = stmt
        .query_map([mlib_spec::schema::SET_COMPLETE], |row| {
            Ok((row.get::<_, String>(0)?, row.get::<_, i64>(1)?))
        })
        .context("reading local-only sets")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a local-only set")?;
    rows.into_iter()
        .map(|(set_id, part_count)| {
            Ok(Candidate {
                messages: candidate_messages(conn, "main", &set_id)?,
                set_id,
                part_count,
            })
        })
        .collect()
}

/// `schema` is `main` or `channel`, never input from outside.
fn candidate_messages(conn: &Connection, schema: &str, set_id: &str) -> Result<Vec<(i64, i64)>> {
    let mut stmt = conn
        .prepare(&format!(
            "SELECT chat_id, message_id FROM {schema}.parts
             WHERE set_id = ?1 AND chat_id IS NOT NULL AND message_id IS NOT NULL
             ORDER BY idx"
        ))
        .context("preparing the candidate part query")?;
    stmt.query_map([set_id], |row| Ok((row.get(0)?, row.get(1)?)))
        .with_context(|| format!("listing parts of {set_id}"))?
        .collect::<rusqlite::Result<Vec<_>>>()
        .with_context(|| format!("reading a part of {set_id}"))
}
