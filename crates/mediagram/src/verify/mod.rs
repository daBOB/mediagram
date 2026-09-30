//! Set verification: metadata-only by default, or a full re-download and
//! hash comparison with `--full`. [`report`] is the pure decision layer,
//! `source` and [`download_hash`] handle Telegram IO, and this
//! module reads/writes the local index rows `commands::verify` needs
//! (kept here rather than in `index::parts`, which only tracks the upload
//! side of a part, not verification).

use anyhow::{Context, Result, bail};
use rusqlite::{Connection, params_from_iter};

use crate::index::parts::PartRow;
use crate::index::sets;
use crate::index::sets_pending::{self, SkippedKind};

pub mod download_hash;
pub mod render;
pub mod report;
pub mod session;
mod source;

/// Resolves the CLI's `set_id`/`--all` choice into concrete set ids, oldest
/// first, plus every `kind` left out of `--all` because this build cannot
/// decode it. Callers validate up front that exactly one of `set_id`/`all`
/// is set; a named id is never filtered by kind — asking for one by name is
/// an explicit request that still fails if it cannot be read.
pub fn resolve_set_ids(
    conn: &Connection,
    set_id: Option<&str>,
    all: bool,
) -> Result<(Vec<String>, Vec<SkippedKind>)> {
    if let Some(id) = set_id {
        return match sets::get_set(conn, id)? {
            Some(_) => Ok((vec![id.to_string()], Vec::new())),
            None => bail!("no set with id {id} in the local index"),
        };
    }
    debug_assert!(all, "caller must require set_id or --all");
    let (placeholders, kinds) = sets_pending::known_kind_placeholders();

    let mut stmt = conn.prepare(&format!(
        "SELECT set_id FROM sets WHERE kind IN ({placeholders}) ORDER BY created_at"
    ))?;
    let ids = stmt
        .query_map(params_from_iter(kinds.iter()), |row| {
            row.get::<_, String>(0)
        })?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("listing sets for --all")?;

    let mut skip_stmt = conn.prepare(&format!(
        "SELECT kind, COUNT(*) FROM sets WHERE kind NOT IN ({placeholders}) GROUP BY kind ORDER BY kind"
    ))?;
    let skipped = skip_stmt
        .query_map(params_from_iter(kinds.iter()), |row| {
            let count: i64 = row.get(1)?;
            Ok(SkippedKind {
                kind: row.get(0)?,
                count: usize::try_from(count).unwrap_or(0),
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("counting sets of an unknown kind")?;

    Ok((ids, skipped))
}

/// Records a successful `--full` hash match. `verified_at` is the result of
/// the last verification, not a high-water mark: [`clear_verified`] wipes it
/// again as soon as a part fails, so a non-null value always means "this
/// part matched its recorded hash at that time and has not failed since".
pub fn mark_verified(conn: &Connection, set_id: &str, idx: u32, now: i64) -> Result<()> {
    set_verified_at(conn, set_id, idx, Some(now))
}

/// Drops a stale `verified_at` after a part fails, so neither the printed
/// row nor the index snapshot pushed to the channel can present an old
/// success next to a current failure.
pub fn clear_verified(conn: &Connection, set_id: &str, idx: u32) -> Result<()> {
    set_verified_at(conn, set_id, idx, None)
}

fn set_verified_at(conn: &Connection, set_id: &str, idx: u32, at: Option<i64>) -> Result<()> {
    conn.execute(
        "UPDATE parts SET verified_at = ?1 WHERE set_id = ?2 AND idx = ?3",
        rusqlite::params![at, set_id, idx],
    )
    .with_context(|| format!("recording verified_at for set {set_id} part {idx}"))?;
    Ok(())
}

/// Clears a part's old `verified_at` when today's verdict failed.
///
/// A part that fails today must not keep advertising an old success: the row
/// is what `push-index` snapshots to the channel for other clients, and the
/// printed report reads the verdict's copy.
pub fn forget_stale_success(
    conn: &Connection,
    set_id: &str,
    part: &PartRow,
    verdict: &mut report::PartVerdict,
) -> Result<()> {
    if verdict.failed() && part.verified_at.is_some() {
        clear_verified(conn, set_id, part.idx)?;
        verdict.verified_at = None;
    }
    Ok(())
}

/// The chat a part was recorded in, when that is not the one being
/// verified. A part with no recorded `chat_id` predates that column being
/// written and is taken to live in the configured channel.
pub fn other_chat(part: &PartRow, chat_id: i64) -> Option<i64> {
    part.chat_id.filter(|&recorded| recorded != chat_id)
}

/// `--since`: a part already verified at or after `since` is left alone, so
/// an interrupted `--full` sweep can be resumed without re-downloading the
/// parts it already proved.
pub fn verified_since(part: &PartRow, since: Option<i64>) -> bool {
    match (since, part.verified_at) {
        (Some(since), Some(at)) => at >= since,
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::index::db;

    fn seed(conn: &Connection, set_id: &str, kind: &str) {
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, title)
             VALUES (?1, ?2, 'mkv', 10, 1, 'complete', 1700000000, 4, ?1)",
            rusqlite::params![set_id, kind],
        )
        .unwrap();
    }

    /// `--all` lists every set of a decodable kind and counts the rest,
    /// rather than failing on the first row it cannot decode.
    #[test]
    fn all_skips_and_counts_sets_of_an_unknown_kind() {
        let dir = tempfile::tempdir().unwrap();
        let conn = db::open(dir.path()).unwrap();
        seed(&conn, "01JQ8F2K9M4XZ00000000001", "movie");
        seed(&conn, "01JQ8F2K9M4XZ00000000002", "vr");

        let (ids, skipped) = resolve_set_ids(&conn, None, true).unwrap();

        assert_eq!(ids, ["01JQ8F2K9M4XZ00000000001"]);
        assert_eq!(
            skipped,
            vec![SkippedKind {
                kind: "vr".to_string(),
                count: 1
            }]
        );
    }

    /// A set named explicitly is an explicit request: it still fails when
    /// this build cannot decode its kind, rather than being silently
    /// skipped the way `--all` skips it.
    #[test]
    fn a_named_set_of_an_unknown_kind_still_fails() {
        let dir = tempfile::tempdir().unwrap();
        let conn = db::open(dir.path()).unwrap();
        seed(&conn, "01JQ8F2K9M4XZ00000000003", "vr");

        let err = resolve_set_ids(&conn, Some("01JQ8F2K9M4XZ00000000003"), false).unwrap_err();
        assert!(format!("{err:#}").contains("vr"), "{err:#}");
    }
}
