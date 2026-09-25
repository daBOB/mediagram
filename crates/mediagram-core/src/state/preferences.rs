//! What a viewer chose for a show, so they do not choose it again — a port
//! of the preference half of `WatchState` in `web/src/state/store.ts`.
//!
//! Opaque throughout. This store does not know what an audio track or a
//! subtitle offset is, and should not: the meaning lives in whatever reads
//! a row back, so a preference the file no longer supports has to be
//! survivable rather than a title that will not open.

use rusqlite::{Connection, params};

use super::profiles::now_ms;

/// How long a preference's three strings may be — the same cap
/// `store.ts`'s `MAX_PREFERENCE` uses. A scope is a show's key or its name,
/// a name is a word this player chose, and a value is a language tag or a
/// number; none of them is prose.
const MAX_PREFERENCE: usize = 200;

/// One remembered choice.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct PreferenceRow {
    pub scope: String,
    pub name: String,
    pub value: String,
}

/// Every choice this profile has made. Sent whole rather than asked for per
/// title: there are a handful of these per show, and a page needs one the
/// instant a title opens — exactly when it has no time to ask for it.
pub fn list_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<PreferenceRow>> {
    let mut stmt = conn.prepare("SELECT scope, name, value FROM preferences WHERE profile_id = ?1")?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(PreferenceRow { scope: row.get(0)?, name: row.get(1)?, value: row.get(2)? })
    })?;
    rows.collect()
}

/// Remembers a choice, or forgets it (`value: None`). An empty value forgets
/// too, rather than storing an empty string that every reader would then
/// have to recognise as meaning nothing.
///
/// `false` when `scope` or `name` has nothing left after trimming — there is
/// nowhere to file the value — never for an absent or over-length value,
/// which trims and caps instead of refusing.
pub fn set(
    conn: &Connection,
    profile_id: &str,
    scope: &str,
    name: &str,
    value: Option<&str>,
) -> rusqlite::Result<bool> {
    let Some(scope) = short(scope) else { return Ok(false) };
    let Some(name) = short(name) else { return Ok(false) };

    match value.and_then(short) {
        None => {
            conn.execute(
                "DELETE FROM preferences WHERE profile_id = ?1 AND scope = ?2 AND name = ?3",
                params![profile_id, scope, name],
            )?;
        }
        Some(value) => {
            conn.execute(
                "INSERT INTO preferences(profile_id, scope, name, value, updated_at)
                   VALUES (?1, ?2, ?3, ?4, ?5)
                   ON CONFLICT(profile_id, scope, name)
                     DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
                params![profile_id, scope, name, value, now_ms()],
            )?;
        }
    }
    Ok(true)
}

/// One of a preference's three strings, trimmed and capped, or `None` when
/// nothing is left. Deliberately not `profiles::clean_name`, which collapses
/// runs of whitespace: right for something a person typed, wrong for a value
/// this player wrote and will parse back.
fn short(value: &str) -> Option<String> {
    let clean: String = value.trim().chars().take(MAX_PREFERENCE).collect();
    (!clean.is_empty()).then_some(clean)
}

#[cfg(test)]
#[path = "preferences_tests.rs"]
mod tests;
