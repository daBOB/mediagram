//! `list_pending` and `list_known`, filtered to sets whose `kind` this build
//! can decode.
//!
//! A pending set a newer uploader wrote under a `kind` this build has never
//! heard of is not this build's to resume — it is left out and counted
//! rather than failing `resume` (or any other caller listing sets) outright.
//! `list_known` is the same filter over every set, the listing `verify --all`
//! walks; merge-conflict resolution counts its own rows and shares only
//! [`SkippedKind`].

use anyhow::{Context, Result};
use rusqlite::{Connection, params_from_iter};

use mlib_spec::Kind;

use crate::index::set_row::SetRow;
use crate::index::sets::COLUMNS;
use crate::index::status::SetStatus;

/// One `kind` a caller could not decode, and how many sets carried it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SkippedKind {
    pub kind: String,
    pub count: usize,
}

/// `?` placeholders for every kind this build knows, and their spellings in
/// the same order — built once so every query filtering or counting by
/// decodable kind binds the same list.
fn known_kind_placeholders() -> (String, Vec<&'static str>) {
    let kinds: Vec<&'static str> = Kind::ALL.iter().map(|k| k.as_str()).collect();
    let placeholders = kinds.iter().map(|_| "?").collect::<Vec<_>>().join(", ");
    (placeholders, kinds)
}

/// The one line per run an operator needs about sets skipped for a `kind`
/// this build cannot decode: what happened, and what to do about it.
pub fn print_skipped(skipped: &[SkippedKind]) {
    for line in skipped_lines(skipped) {
        println!("{line}");
    }
}

/// [`print_skipped`]'s lines, for a report that prints them among its own.
pub fn skipped_lines(skipped: &[SkippedKind]) -> Vec<String> {
    skipped
        .iter()
        .map(|s| {
            format!(
                "{} set(s) of kind '{}' from a newer uploader skipped — reinstall mediagram",
                s.count, s.kind
            )
        })
        .collect()
}

/// Every set still `pending` whose `kind` this build knows, oldest first (so
/// `resume` finishes older sets before newer ones), plus every unknown
/// `kind` left out among the pending sets.
pub fn list_pending(conn: &Connection) -> Result<(Vec<SetRow>, Vec<SkippedKind>)> {
    let (placeholders, kinds) = known_kind_placeholders();
    let mut bind: Vec<&str> = vec![SetStatus::Pending.as_str()];
    bind.extend(kinds.iter().copied());

    let mut stmt = conn.prepare(&format!(
        "SELECT {COLUMNS} FROM sets WHERE status = ? AND kind IN ({placeholders}) ORDER BY created_at"
    ))?;
    let rows = stmt
        .query_map(params_from_iter(bind.iter()), SetRow::from_row)?
        .collect::<rusqlite::Result<Vec<_>>>()?;

    Ok((rows, skipped_kinds(conn, Some(SetStatus::Pending))?))
}

/// Every set whose `kind` this build knows, oldest first, plus every unknown
/// `kind` left out — the listing `verify --all` walks.
pub fn list_known(conn: &Connection) -> Result<(Vec<String>, Vec<SkippedKind>)> {
    let (placeholders, kinds) = known_kind_placeholders();
    let mut stmt = conn.prepare(&format!(
        "SELECT set_id FROM sets WHERE kind IN ({placeholders}) ORDER BY created_at"
    ))?;
    let ids = stmt
        .query_map(params_from_iter(kinds.iter()), |row| {
            row.get::<_, String>(0)
        })?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("listing sets for --all")?;
    let skipped = skipped_kinds(conn, None).context("counting sets of an unknown kind")?;
    Ok((ids, skipped))
}

/// How many sets carry each `kind` this build cannot decode, by kind;
/// `status` narrows the count to sets in that state.
pub(crate) fn skipped_kinds(
    conn: &Connection,
    status: Option<SetStatus>,
) -> Result<Vec<SkippedKind>> {
    let (placeholders, kinds) = known_kind_placeholders();
    let status_filter = status.map_or("", |_| "status = ? AND ");
    let bind: Vec<&str> = status
        .map(SetStatus::as_str)
        .into_iter()
        .chain(kinds)
        .collect();
    let mut stmt = conn.prepare(&format!(
        "SELECT kind, COUNT(*) FROM sets WHERE {status_filter}kind NOT IN ({placeholders})
         GROUP BY kind ORDER BY kind"
    ))?;
    let skipped = stmt
        .query_map(params_from_iter(bind.iter()), |row| {
            let count: i64 = row.get(1)?;
            Ok(SkippedKind {
                kind: row.get(0)?,
                count: usize::try_from(count).unwrap_or(0),
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(skipped)
}

#[cfg(test)]
#[path = "sets_pending_tests.rs"]
mod tests;
