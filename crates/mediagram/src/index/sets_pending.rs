//! `list_pending`, filtered to sets whose `kind` this build can decode.
//!
//! A pending set a newer uploader wrote under a `kind` this build has never
//! heard of is not this build's to resume — it is left out and counted
//! rather than failing `resume` (or any other caller listing sets) outright.
//! The same known-kind filter is shared by `verify --all` and merge-conflict
//! resolution, which face the same rows.

use anyhow::Result;
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
pub(crate) fn known_kind_placeholders() -> (String, Vec<&'static str>) {
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

    let mut skip_stmt = conn.prepare(&format!(
        "SELECT kind, COUNT(*) FROM sets WHERE status = ? AND kind NOT IN ({placeholders})
         GROUP BY kind ORDER BY kind"
    ))?;
    let skipped = skip_stmt
        .query_map(params_from_iter(bind.iter()), |row| {
            let count: i64 = row.get(1)?;
            Ok(SkippedKind {
                kind: row.get(0)?,
                count: usize::try_from(count).unwrap_or(0),
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;

    Ok((rows, skipped))
}

#[cfg(test)]
mod tests {
    use super::*;
    use mlib_spec::caption::{Caption, Kind as CaptionKind, Part};
    use mlib_spec::ids::ProviderIds;

    fn caption_text(set_id: &str) -> String {
        let caption = Caption {
            cid: None,
            chap: None,
            path: None,
            t: CaptionKind::Movie,
            ids: ProviderIds {
                tmdb: Some(1),
                tvdb: None,
                imdb: None,
            },
            show: None,
            title: Some("A Movie".into()),
            year: Some(2020),
            s: None,
            e: None,
            abs: None,
            q: None,
            hdr: None,
            container: "mkv".into(),
            vcodec: None,
            acodec: None,
            alang: vec![],
            slang: vec![],
            dur: None,
            variant: None,
            set: set_id.to_string(),
            part: Part {
                i: 0,
                n: 1,
                off: 0,
                len: 10,
                sha256: "a".repeat(64),
            },
            total: 10,
        };
        mlib_spec::to_text(&caption, "").unwrap()
    }

    fn seed_pending_movie(conn: &Connection, set_id: &str) {
        let caption = mlib_spec::parse(&caption_text(set_id)).unwrap();
        let row = SetRow::from_caption(&caption, 1_700_000_000);
        crate::index::sets::insert_set(conn, &row).unwrap();
    }

    /// A pending row of an unknown `kind` — as a newer uploader's own
    /// upload would leave it, before this build is reinstalled — is
    /// counted and left out, not a decode failure that aborts the list.
    #[test]
    fn a_pending_set_of_an_unknown_kind_is_skipped_and_counted() {
        let dir = tempfile::tempdir().unwrap();
        let conn = crate::index::db::open(dir.path()).unwrap();
        seed_pending_movie(&conn, "01JQ8F2K9M4XZ00000000001");
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
             VALUES ('01JQ8F2K9M4XZ00000000002', 'vr', 'mkv', 10, 1, 'pending', 1700000001, 4)",
            [],
        )
        .unwrap();

        let (pending, skipped) = list_pending(&conn).unwrap();

        assert_eq!(
            pending
                .iter()
                .map(|s| s.set_id.as_str())
                .collect::<Vec<_>>(),
            ["01JQ8F2K9M4XZ00000000001"]
        );
        assert_eq!(
            skipped,
            vec![SkippedKind {
                kind: "vr".to_string(),
                count: 1
            }]
        );
    }

    /// Today's index — every kind known — lists exactly as before, with
    /// nothing skipped.
    #[test]
    fn every_pending_set_of_a_known_kind_is_listed_with_nothing_skipped() {
        let dir = tempfile::tempdir().unwrap();
        let conn = crate::index::db::open(dir.path()).unwrap();
        seed_pending_movie(&conn, "01JQ8F2K9M4XZ00000000003");

        let (pending, skipped) = list_pending(&conn).unwrap();

        assert_eq!(pending.len(), 1);
        assert!(skipped.is_empty());
    }
}
