//! What the System screen says under "Catalogue". Every number here is read
//! from the installed catalog rather than counted as it is displayed, so the
//! screen cannot disagree with what is actually on disk.

use rusqlite::Connection;

fn core_at(dir: &std::path::Path) -> std::sync::Arc<mediagram_core::api::Core> {
    mediagram_core::api::Core::new(dir.display().to_string(), 1, "h".into(), "test-device".into())
}

fn catalog_with(dir: &std::path::Path, sets: usize, posters: usize) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    for n in 0..sets {
        let set_id = format!("01SET00000000000000000{n:02}");
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, 'movie', 'mkv', 100, 1, 'complete', 0, 1)",
            rusqlite::params![set_id],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, status)
             VALUES (?1, 0, 0, 100, -1001, 100, 'done')",
            rusqlite::params![set_id],
        )
        .unwrap();
    }
    if posters > 0 {
        let art = current.join("posters");
        std::fs::create_dir_all(&art).unwrap();
        for n in 0..posters {
            std::fs::write(art.join(format!("tmdb-movie-{n}.jpg")), b"x").unwrap();
        }
    }
}

#[tokio::test]
async fn a_channel_catalog_counts_its_sets_and_its_artwork() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with(dir.path(), 3, 2);
    let core = core_at(dir.path());

    let facts = core.clone().catalog_facts().await;

    assert_eq!(facts.origin, "channel");
    assert_eq!(facts.sets, 3);
    assert_eq!(facts.posters, 2);
    assert_eq!(facts.schema, mlib_spec::schema::SCHEMA_VERSION as u32);
}

/// No posters directory at all is zero, not a failure — it is the ordinary
/// state of a catalog read from a channel before any artwork is fetched.
#[tokio::test]
async fn a_catalog_with_no_artwork_says_none_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with(dir.path(), 1, 0);
    let core = core_at(dir.path());

    assert_eq!(core.clone().catalog_facts().await.posters, 0);
}

/// Before setup finishes there is no catalog. The screen still has to render.
#[tokio::test]
async fn no_catalog_at_all_reports_zeroes_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    let core = core_at(dir.path());

    let facts = core.clone().catalog_facts().await;

    assert_eq!(facts.sets, 0);
    assert_eq!(facts.posters, 0);
}
