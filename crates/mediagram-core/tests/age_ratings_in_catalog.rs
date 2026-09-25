//! The age rating a catalog row carries, and an index old enough to carry
//! none. The channel's snapshot is written by whichever machine uploads, so
//! a v6 index — no `shows.certification` — is one this build must still read
//! whole.

use std::path::Path;

use rusqlite::{Connection, params};

fn core(dir: &Path) -> std::sync::Arc<mediagram_core::api::Core> {
    mediagram_core::api::Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

/// `<dir>/catalog/current/library.db` at schema `version`.
fn index_at(dir: &Path, version: i64) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(version) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// A playable set of no bytes: complete, with no parts to wait on.
fn add_set(conn: &Connection, set_id: &str, kind: &str, tmdb: Option<i64>) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, ?2, ?3, 'mkv', 0, 0, 'complete', 0, 1)",
        params![set_id, kind, tmdb],
    )
    .unwrap();
}

fn fsk_of(sets: &[mediagram_core::dto::SetSummary], set_id: &str) -> Option<String> {
    sets.iter()
        .find(|s| s.set_id == set_id)
        .expect("listed")
        .fsk
        .clone()
}

#[tokio::test]
async fn a_listed_set_carries_its_titles_rating_and_an_episode_its_shows() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    add_set(&conn, "01FILM0000000000000000000A", "movie", Some(11225));
    add_set(&conn, "01EPISODE00000000000000000", "ep", Some(1399));
    add_set(&conn, "01UNRATED00000000000000000", "movie", Some(550));
    add_set(&conn, "01LESSON000000000000000000", "tut", None);
    conn.execute(
        "INSERT INTO shows(source, kind, id, certification) VALUES
           ('tmdb', 'movie', 11225, '12'), ('tmdb', 'tv', 1399, ' 16 '), ('tmdb', 'movie', 550, '  ')",
        [],
    )
    .unwrap();

    let sets = core(dir.path()).list_sets().await.unwrap();

    assert_eq!(
        fsk_of(&sets, "01FILM0000000000000000000A").as_deref(),
        Some("12")
    );
    assert_eq!(
        fsk_of(&sets, "01EPISODE00000000000000000").as_deref(),
        Some("16")
    );
    // A blank rating is none, as the web player reads it.
    assert_eq!(fsk_of(&sets, "01UNRATED00000000000000000"), None);
    assert_eq!(fsk_of(&sets, "01LESSON000000000000000000"), None);
}

#[tokio::test]
async fn an_index_from_before_ratings_still_lists_and_describes_its_titles() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), 6);
    add_set(&conn, "01FILM0000000000000000000A", "movie", Some(11225));
    conn.execute(
        "INSERT INTO shows(source, kind, id, overview) VALUES ('tmdb', 'movie', 11225, 'Dracula is awakened.')",
        [],
    )
    .unwrap();
    let core = core(dir.path());

    let sets = core.clone().list_sets().await.unwrap();
    assert_eq!(fsk_of(&sets, "01FILM0000000000000000000A"), None);

    let info = core
        .title_info("tmdb-movie-11225".into())
        .await
        .expect("described");
    assert_eq!(info.overview.as_deref(), Some("Dracula is awakened."));
}
