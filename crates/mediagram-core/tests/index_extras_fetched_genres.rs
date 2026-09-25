//! The fetched-sidecar half of a set's genres: what `store::list_sets` falls
//! back to for a title the index has none for, its precedence against the
//! index's own row, and its tolerance of a sidecar that cannot be read.

use std::path::Path;

use rusqlite::{Connection, params};

use mediagram_core::api::Core;
use mediagram_core::api::enrich::details;
use mediagram_core::dto::SetSummary;
use mediagram_tmdb::details::TitleDetailsRow;
use mlib_spec::Kind;

fn core(dir: &Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into(), "test-device".into())
}

/// `<dir>/catalog/current/library.db` at the current schema.
fn index_at(dir: &Path) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// A playable set of no bytes: complete, with no parts to wait on.
fn add_set(conn: &Connection, set_id: &str, tmdb: Option<i64>) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', ?2, 'mkv', 0, 0, 'complete', 0, 1)",
        params![set_id, tmdb],
    )
    .unwrap();
}

/// A row this device fetched for itself, as `enrich::details::upsert` would
/// leave it — every field but `genres` at its harmless default.
fn fetched_row(id: u64, genres: &str) -> TitleDetailsRow {
    TitleDetailsRow {
        kind: Kind::Movie,
        id,
        lang: "en-US".into(),
        overview: None,
        tagline: None,
        genres: Some(genres.into()),
        rating: None,
        network: None,
        status: None,
        first_air: None,
        last_air: None,
        total_seasons: None,
        total_episodes: None,
        certification: None,
    }
}

fn set_of<'a>(sets: &'a [SetSummary], set_id: &str) -> &'a SetSummary {
    sets.iter().find(|s| s.set_id == set_id).expect("listed")
}

/// The index says nothing about this title — no `shows` row at all — but a
/// fetch on this device did. Deliberately different from the web player,
/// which reads the index alone: see `store::list_sets` on why.
#[tokio::test]
async fn a_title_the_index_omits_still_gets_genres_from_a_device_fetch() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_set(&conn, "01FETCHEDONLY00000000000001", Some(550));
    let core = core(dir.path());
    let sidecar = details::open_or_create(&core).unwrap();
    details::upsert(&sidecar, &fetched_row(550, "Drama")).unwrap();

    let sets = core.list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01FETCHEDONLY00000000000001").genres, vec!["Drama"]);
}

/// The index's own row wins over a fetched one for the same title — the
/// publisher curated it, and a phone's own fetch has no better claim, the
/// same rule `enrich::details::title_info` applies to a whole description.
#[tokio::test]
async fn index_genres_are_preferred_to_a_fetched_value_for_the_same_title() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_set(&conn, "01BOTHDESCRIBE00000000000A", Some(550));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, 'Drama')",
        [],
    )
    .unwrap();
    let core = core(dir.path());
    let sidecar = details::open_or_create(&core).unwrap();
    details::upsert(&sidecar, &fetched_row(550, "Comedy")).unwrap();

    let sets = core.list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01BOTHDESCRIBE00000000000A").genres, vec!["Drama"]);
}

/// A fetched sidecar that cannot be read — no `shows` table at all, standing
/// in for one a rolled-back migration left behind — is only ever a genre
/// fallback, so it must not take the rest of the catalog down with it.
#[tokio::test]
async fn a_broken_fetched_sidecar_does_not_take_the_catalog_down() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_set(&conn, "01STILLLISTED0000000000001", Some(550));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, 'Drama')",
        [],
    )
    .unwrap();
    let core = core(dir.path());

    // `open_or_create` never leaves a sidecar in this shape; this stands in
    // for whatever might — a bare file with none of `shows`' tables.
    let sidecar_path = details::details_db(&core);
    std::fs::create_dir_all(sidecar_path.parent().unwrap()).unwrap();
    Connection::open(&sidecar_path).unwrap();

    let sets = core.list_sets().await.unwrap();

    assert_eq!(set_of(&sets, "01STILLLISTED0000000000001").genres, vec!["Drama"]);
}

