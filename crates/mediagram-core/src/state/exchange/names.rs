//! A profile's renames between the `profiles` row and the wire: when it took
//! its name and the names it had, and taking a merged person in by all of
//! them. A port of `web/src/state/names-exchange.ts`.
//!
//! Rows are keyed by id and only the wire uses names, so a rename taken in is
//! the row's name updated in place, its history left where it is. Split out
//! of `exchange.rs` for the reason `exchange/roles.rs` is; SQLite failures
//! propagate, so `import_merged` rolls the whole import back.

use rusqlite::{Connection, params};

use super::roles::first_of_each_name;
use crate::state::merge::MergedProfile;
use crate::state::profiles::{self, role_rows};
use crate::state::record::{NameKeys, normal_name};

mod fold;

/// This profile's name keys, as `export_record` writes them. A profile
/// nobody renamed says neither.
pub(super) fn export(conn: &Connection, profile_id: &str) -> rusqlite::Result<NameKeys> {
    conn.query_row("SELECT renamed_at, former_names FROM profiles WHERE id = ?1", [profile_id], |row| {
        Ok(NameKeys { renamed_at: row.get::<_, i64>(0)? as f64, former_names: role_rows::json_list(row, 1)? })
    })
}

/// This device's row for a merged person, made if it has never met them:
/// its id, how many rows taking them in changed, and whether it is a kids
/// profile now.
///
/// The first local row of the merged name and of each former name are all
/// this person. The oldest is kept — the id this device has used longest —
/// and every other is folded into it. The kept row then takes the merged
/// spelling when it answers to another name or the merged rename is newer,
/// and the merged former names when they differ; a spelling of the same name
/// with no newer rename stays this device's own, as it always has.
pub(super) fn take_in(conn: &Connection, profile: &MergedProfile) -> rusqlite::Result<Option<(String, u64, bool)>> {
    // Normalised again for a hand-built merge, as `profile_named_with_creation` does.
    let Some(name) = normal_name(&profile.name) else {
        return Ok(None);
    };
    let local = first_of_each_name(conn)?;
    let mut found: Vec<(i64, String, bool)> = Vec::new();
    for one in std::iter::once(&name).chain(&profile.names.former_names) {
        let Some(id) = local.get(one) else {
            continue;
        };
        if !found.iter().any(|(_, seen, _)| seen == id) {
            let (created_at, kids) =
                conn.query_row("SELECT created_at, kids FROM profiles WHERE id = ?1", [id], |row| {
                    Ok((row.get(0)?, row.get(1)?))
                })?;
            found.push((created_at, id.clone(), kids));
        }
    }
    // Oldest first: the smallest `created_at`, then the smallest id.
    found.sort();
    let mut found = found.into_iter();

    let (id, created, kids) = match found.next() {
        Some((_, id, kids)) => (id, false, kids),
        None => match profiles::profile_named_with_creation(
            conn,
            &profile.name,
            Some(&profile.display_name),
            profile.kids,
        )? {
            Some(made) => made,
            None => return Ok(None),
        },
    };
    let mut changed = 0;
    for (_, other, _) in found {
        fold::fold(conn, &other, &id)?;
        changed += 1;
    }
    if rename(conn, &id, &name, profile)? || created {
        changed += 1;
    }
    Ok(Some((id, changed, kids)))
}

/// The merged name keys written to the kept row; whether anything was.
fn rename(conn: &Connection, id: &str, name: &str, profile: &MergedProfile) -> rusqlite::Result<bool> {
    let (stored, renamed_at, former): (String, i64, String) = conn.query_row(
        "SELECT name, renamed_at, former_names FROM profiles WHERE id = ?1",
        [id],
        |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?)),
    )?;
    // Compared as the column holds it, so a stamp with a fraction is taken in once.
    let merged_at = profile.names.renamed_at as i64;
    let mut wrote = false;
    if normal_name(&stored).as_deref() != Some(name) || merged_at > renamed_at {
        // The merged spelling, trimmed but not cleaned, as the web stores it.
        conn.execute(
            "UPDATE profiles SET name = ?2, renamed_at = ?3 WHERE id = ?1",
            params![id, profile.display_name, merged_at],
        )?;
        wrote = true;
    }
    // Compared as stored: both sides are sorted by bytes, each name once (`sorted_names`).
    let merged = serde_json::to_string(&profile.names.former_names)
        .map_err(|error| rusqlite::Error::ToSqlConversionFailure(Box::new(error)))?;
    if merged != former {
        conn.execute("UPDATE profiles SET former_names = ?2 WHERE id = ?1", params![id, merged])?;
        wrote = true;
    }
    Ok(wrote)
}

#[cfg(test)]
#[path = "names_tests.rs"]
mod tests;
