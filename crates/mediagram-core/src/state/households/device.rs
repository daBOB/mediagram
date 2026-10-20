//! The household this device shows. Local only, in `state_meta`: never
//! exported, never synced, so each device keeps its own.

use rusqlite::{Connection, OptionalExtension, params};

use super::live;
use super::rules::device_household;
use crate::state::profiles::{self, Profile};
use crate::state::sync;

const KEY: &str = "joined_household";

fn stored(conn: &Connection) -> rusqlite::Result<Option<String>> {
    conn.query_row("SELECT value FROM state_meta WHERE key = ?1", [KEY], |row| row.get(0)).optional()
}

/// The household this device shows, remembered when it differs from what is
/// stored. `None` means ask to join one, or wait for the first sync round.
pub fn joined(conn: &Connection) -> rusqlite::Result<Option<String>> {
    joined_over(conn, &profiles::list(conn)?)
}

/// [`joined`] over profiles already listed, so a caller that needs them too
/// lists once.
pub(crate) fn joined_over(conn: &Connection, everyone: &[Profile]) -> rusqlite::Result<Option<String>> {
    let chosen = profiles::chosen(conn)?;
    let chosen_in = chosen.as_ref().and_then(|id| everyone.iter().find(|one| &one.id == id)).map(|one| one.household.as_str());
    let live_ids: Vec<String> = live(conn, everyone)?.into_iter().map(|one| one.id).collect();
    let before = stored(conn)?;
    let answer = device_household(before.as_deref(), chosen_in, &live_ids, sync::round_imported(conn)?);
    if let Some(id) = answer.as_ref().filter(|id| before.as_ref() != Some(id)) {
        remember(conn, id)?;
    }
    Ok(answer)
}

/// Remembers `id` as the household this device shows: one it joined, or
/// the answer [`joined`] worked out.
pub(crate) fn remember(conn: &Connection, id: &str) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2)
           ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![KEY, id],
    )?;
    Ok(())
}

/// The joined household's profiles; none while this device has joined none.
pub fn profiles_here(conn: &Connection) -> rusqlite::Result<Vec<Profile>> {
    let everyone = profiles::list(conn)?;
    let Some(here) = joined_over(conn, &everyone)? else {
        return Ok(Vec::new());
    };
    Ok(everyone.into_iter().filter(|one| one.household == here).collect())
}

/// The remembered "who's watching", only while that profile is in the joined
/// household.
pub fn chosen_here(conn: &Connection) -> rusqlite::Result<Option<String>> {
    let Some(id) = profiles::chosen(conn)? else {
        return Ok(None);
    };
    let everyone = profiles::list(conn)?;
    let Some(here) = joined_over(conn, &everyone)? else {
        return Ok(None);
    };
    Ok(everyone.iter().any(|one| one.id == id && one.household == here).then_some(id))
}

#[cfg(test)]
#[path = "device_tests.rs"]
mod tests;
