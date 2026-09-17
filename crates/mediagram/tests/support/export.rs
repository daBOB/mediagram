//! An index holding just enough of a set for the export to read it.
//!
//! The export only ever looks at the `sets` table, so a fixture is a caption
//! varying the three fields a title is distinguished by, and a database with
//! those rows in it.

use mediagram::index::db;
use mediagram::index::set_row::SetRow;
use mediagram::index::sets;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

/// A caption for a set row, varying only the fields these tests care about.
pub fn caption(set: &str, kind: Kind, tmdb: Option<u64>) -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: kind,
        ids: ProviderIds {
            tmdb,
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("Title".into()),
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
        set: set.into(),
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

/// A temporary index holding the given sets. The directory is returned so the
/// caller keeps it alive for the duration of the test.
pub fn db_with(rows: &[(&str, Kind, Option<u64>)]) -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    for (set, kind, tmdb) in rows {
        let row = SetRow::from_caption(&caption(set, *kind, *tmdb), 1_700_000_000).unwrap();
        sets::insert_set(&conn, &row).unwrap();
    }
    (dir, conn)
}
