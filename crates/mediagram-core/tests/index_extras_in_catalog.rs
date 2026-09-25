//! What `list_sets` attaches beyond the row itself: subtitle languages and a
//! summary flag from `assets`, and genres from `shows` — by poster key, so
//! an episode inherits its show's. The fetched-sidecar half of genres (the
//! part `store::list_sets` falls back to) is `index_extras_fetched_genres.rs`.

use std::path::Path;

use rusqlite::{Connection, params};

use mediagram_core::api::Core;
use mediagram_core::dto::SetSummary;

fn core(dir: &Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into(), "test-device".into())
}

/// `<dir>/catalog/current/library.db` at `version`.
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

fn add_asset(conn: &Connection, set_id: &str, kind: &str, lang: &str, body: &str) {
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, ?2, ?3, ?4)",
        params![set_id, kind, lang, body],
    )
    .unwrap();
}

fn set_of<'a>(sets: &'a [SetSummary], set_id: &str) -> &'a SetSummary {
    sets.iter().find(|s| s.set_id == set_id).expect("listed")
}

#[tokio::test]
async fn a_sets_subtitle_languages_come_back_sorted() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    add_set(&conn, "01SUBTITLED0000000000000001", "movie", None);
    add_asset(&conn, "01SUBTITLED0000000000000001", "subtitle", "en", "WEBVTT english");
    add_asset(&conn, "01SUBTITLED0000000000000001", "subtitle", "de", "WEBVTT deutsch");

    let sets = core(dir.path()).list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01SUBTITLED0000000000000001").subtitles, vec!["de", "en"]);
}

#[tokio::test]
async fn a_summary_row_marks_has_summary_and_its_absence_does_not() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    add_set(&conn, "01SUMMARISED000000000000001", "movie", None);
    add_set(&conn, "01BARE0000000000000000001A", "movie", None);
    add_asset(&conn, "01SUMMARISED000000000000001", "summary", "", "What happens.");

    let sets = core(dir.path()).list_sets().await.unwrap();

    assert!(set_of(&sets, "01SUMMARISED000000000000001").has_summary);
    assert!(!set_of(&sets, "01BARE0000000000000000001A").has_summary);
}

#[tokio::test]
async fn genres_are_read_from_the_index_by_poster_key() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    add_set(&conn, "01FILM0000000000000000000A", "movie", Some(11225));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 11225, 'Action, Horror')",
        [],
    )
    .unwrap();

    let sets = core(dir.path()).list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01FILM0000000000000000000A").genres, vec!["Action", "Horror"]);
}

/// Every series kind is keyed `tv`, so an episode's poster key
/// (`tmdb-tv-1399`) finds the row the whole show shares.
#[tokio::test]
async fn an_episode_inherits_its_shows_genres_through_the_series_poster_key() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    add_set(&conn, "01EPISODE00000000000000000A", "ep", Some(1399));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'tv', 1399, 'Drama, Horror')",
        [],
    )
    .unwrap();

    let sets = core(dir.path()).list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01EPISODE00000000000000000A").genres, vec!["Drama", "Horror"]);
}

/// A v6 index — a channel whose uploader is not upgraded yet — has no
/// `shows.certification`, but `shows.genres` (v5) and `assets` (v4) are
/// older still, so a title still lists with genres, subtitles and a summary
/// flag; only its age rating is unavailable.
#[tokio::test]
async fn a_v6_index_still_fills_genres_subtitles_and_has_summary() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), 6);
    add_set(&conn, "01OLDINDEX0000000000000001", "movie", Some(550));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, 'Drama')",
        [],
    )
    .unwrap();
    add_asset(&conn, "01OLDINDEX0000000000000001", "subtitle", "en", "en text");
    add_asset(&conn, "01OLDINDEX0000000000000001", "summary", "", "What happens.");

    let sets = core(dir.path()).list_sets().await.unwrap();

    let set = set_of(&sets, "01OLDINDEX0000000000000001");
    assert_eq!(set.genres, vec!["Drama"]);
    assert_eq!(set.subtitles, vec!["en"]);
    assert!(set.has_summary);
    assert_eq!(set.fsk, None, "v6 has no certification column at all");
}
