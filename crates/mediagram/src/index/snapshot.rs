//! Consistent snapshots of `library.db` for pushing to the channel: a WAL
//! checkpoint followed by `VACUUM INTO` a temp path, so a concurrent write
//! to the live database can never land mid-upload.

use std::path::Path;

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::db;

/// Folds the WAL back into the main database file so `VACUUM INTO` captures
/// every committed write. Safe to call on a database with no WAL yet.
pub fn checkpoint(conn: &Connection) -> Result<()> {
    conn.execute_batch("PRAGMA wal_checkpoint(TRUNCATE);")
        .context("checkpointing WAL before snapshot")
}

/// Writes a self-contained copy of `conn`'s database to `dest`, after first
/// recording the push time in `meta.last_push_at` (so the snapshot itself
/// carries the timestamp it was taken at). `dest` is removed first if it
/// already exists, since `VACUUM INTO` refuses to overwrite a file.
///
/// This writes to the live database. Callers that are only reading it — the
/// package export — use [`copy_to`] instead.
pub fn snapshot_to(conn: &Connection, dest: &Path) -> Result<()> {
    let now = crate::clock::now_unix();
    db::set_meta(conn, "last_push_at", &now.to_string())
        .context("recording last_push_at before snapshot")?;
    copy_to(conn, dest)
}

/// A copy with no side effect on the source: `VACUUM INTO` runs in a read
/// transaction, so a concurrent writer cannot be captured mid-write, and
/// nothing is recorded in the live database. The export uses this, because a
/// command that claims to read the index must not leave a mark on it — and
/// because `last_push_at` means "pushed to the channel", which an export has
/// not done.
pub fn copy_to(conn: &Connection, dest: &Path) -> Result<()> {
    if dest.exists() {
        std::fs::remove_file(dest)
            .with_context(|| format!("removing stale snapshot {}", dest.display()))?;
    }
    let dest_str = dest
        .to_str()
        .with_context(|| format!("snapshot path {} is not valid UTF-8", dest.display()))?;
    conn.execute("VACUUM INTO ?1", [dest_str])
        .with_context(|| format!("vacuuming snapshot to {}", dest.display()))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use mlib_spec::caption::{Caption, Kind, Part};
    use mlib_spec::ids::ProviderIds;

    use super::*;

    fn sample_caption() -> Caption {
        Caption {
            cid: None,
            chap: None,
            path: None,
            t: Kind::Movie,
            ids: ProviderIds {
                tmdb: Some(1),
                tvdb: None,
                imdb: None,
            },
            show: None,
            title: Some("Sample".into()),
            year: Some(2024),
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
            set: "01JQ8F2K9M4XZ00000000001".into(),
            part: Part {
                i: 0,
                n: 1,
                off: 0,
                len: 10,
                sha256: String::new(),
            },
            total: 10,
        }
    }

    #[test]
    fn snapshot_contains_committed_data_and_records_push_time() {
        let dir = tempfile::tempdir().unwrap();
        let conn = db::open(dir.path()).unwrap();
        let row = crate::index::set_row::SetRow::from_caption(&sample_caption(), 1_700_000_000);
        crate::index::sets::insert_set(&conn, &row).unwrap();

        checkpoint(&conn).unwrap();
        let dest = dir.path().join("library.push.db");
        snapshot_to(&conn, &dest).unwrap();

        assert!(dest.exists());
        let snapshot_conn = crate::index::sqlite_init::open(&dest).unwrap();
        let count: i64 = snapshot_conn
            .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
            .unwrap();
        assert_eq!(count, 1);
        let pushed_at: String = snapshot_conn
            .query_row(
                "SELECT value FROM meta WHERE key = 'last_push_at'",
                [],
                |r| r.get(0),
            )
            .unwrap();
        assert!(pushed_at.parse::<i64>().unwrap() > 0);
    }

    #[test]
    fn snapshot_overwrites_a_stale_destination() {
        let dir = tempfile::tempdir().unwrap();
        let conn = db::open(dir.path()).unwrap();
        let dest = dir.path().join("library.push.db");
        std::fs::write(&dest, b"stale contents").unwrap();

        snapshot_to(&conn, &dest).unwrap();

        // A valid sqlite file, not the stale placeholder bytes.
        crate::index::sqlite_init::open(&dest).unwrap();
    }
}
