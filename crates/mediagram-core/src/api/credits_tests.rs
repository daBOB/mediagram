use super::*;
use rusqlite::Connection;

/// `<dir>/catalog/current/library.db` at `version`, matching the layout
/// `Core` reads through `current` — see `versions::current`, a plain path
/// join rather than a symlink read, so a test directory serves as well as a
/// real install.
fn index_at(dir: &std::path::Path, version: i64) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(version) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

#[allow(clippy::too_many_arguments)] // a test fixture builder, not a public API
fn insert_credit(conn: &Connection, kind: &str, id: u64, ord: u32, person_id: u64, name: &str, role: Option<&str>, dept: &str) {
    conn.execute(
        "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept)
         VALUES ('tmdb', ?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        rusqlite::params![kind, id, ord, person_id, name, role, dept],
    )
    .unwrap();
}

#[tokio::test]
async fn a_titles_credits_come_back_by_poster_key() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    insert_credit(&conn, "movie", 550, 0, 1, "Lead", Some("The Narrator"), "cast");
    insert_credit(&conn, "movie", 550, 1, 9, "A Director", Some("Director"), "crew");
    drop(conn);

    let credits = Core::at(dir.path()).title_credits("tmdb-movie-550".into()).await;

    assert_eq!(credits.cast.len(), 1);
    assert_eq!(credits.cast[0].name, "Lead");
    assert_eq!(credits.crew.len(), 1);
    assert_eq!(credits.crew[0].name, "A Director");
}

#[tokio::test]
async fn a_key_of_the_wrong_shape_has_no_credits() {
    let dir = tempfile::tempdir().unwrap();
    let credits = Core::at(dir.path()).title_credits("../escape".into()).await;
    assert_eq!(credits, TitleCreditsRecord::default());
}

#[tokio::test]
async fn a_person_carries_their_own_title_keys() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    insert_credit(&conn, "movie", 550, 0, 1, "Recurring", Some("Role"), "cast");
    insert_credit(&conn, "tv", 1399, 0, 1, "Recurring", Some("Role"), "cast");
    drop(conn);

    let found = Core::at(dir.path()).person(1).await.expect("credited");
    assert_eq!(found.name, "Recurring");
    assert_eq!(found.title_keys, vec!["tmdb-movie-550".to_string(), "tmdb-tv-1399".to_string()]);
    assert_eq!(found.portrait_key, None, "nothing fetched this device's copy of the portrait");
}

#[tokio::test]
async fn nobody_credited_is_no_person() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(Core::at(dir.path()).person(1).await, None);
}

#[tokio::test]
async fn franchises_are_listed_alphabetically() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    conn.execute("INSERT INTO franchises(source, id, name) VALUES ('tmdb', 2, 'Zeta')", [])
        .unwrap();
    conn.execute("INSERT INTO franchises(source, id, name) VALUES ('tmdb', 1, 'Alpha')", [])
        .unwrap();
    drop(conn);

    let names: Vec<String> = Core::at(dir.path()).franchises().await.into_iter().map(|f| f.name).collect();
    assert_eq!(names, vec!["Alpha", "Zeta"]);
}

#[tokio::test]
async fn a_v8_index_has_no_franchises_credits_or_people() {
    let dir = tempfile::tempdir().unwrap();
    index_at(dir.path(), 8);

    let core = Core::at(dir.path());
    assert_eq!(core.clone().title_credits("tmdb-movie-550".into()).await, TitleCreditsRecord::default());
    assert_eq!(core.clone().person(1).await, None);
    assert!(core.clone().franchises().await.is_empty());
    assert!(core.search_people("anna".into()).await.is_empty());
}

#[tokio::test]
async fn search_people_matches_every_word_most_credited_first() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    insert_credit(&conn, "movie", 1, 0, 1, "Anna Busy", None, "cast");
    insert_credit(&conn, "movie", 2, 0, 1, "Anna Busy", None, "cast");
    insert_credit(&conn, "movie", 3, 0, 2, "Anna Quiet", None, "cast");
    drop(conn);

    let hits = Core::at(dir.path()).search_people("anna".into()).await;
    assert_eq!(hits.iter().map(|h| h.name.as_str()).collect::<Vec<_>>(), ["Anna Busy", "Anna Quiet"]);
}

#[tokio::test]
async fn a_person_with_no_recorded_profile_fetches_no_portrait() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path(), mlib_spec::schema::SCHEMA_VERSION);
    insert_credit(&conn, "movie", 550, 0, 1, "Lead", None, "cast");
    drop(conn);

    assert_eq!(Core::at(dir.path()).fetch_portrait(1).await, None);
}

#[tokio::test]
async fn a_portrait_already_on_disk_is_returned_without_a_request() {
    let dir = tempfile::tempdir().unwrap();
    let artwork = dir.path().join("catalog").join("artwork");
    std::fs::create_dir_all(&artwork).unwrap();
    std::fs::write(artwork.join("tmdb-person-7.jpg"), b"already-held").unwrap();

    let path = Core::at(dir.path()).fetch_portrait(7).await.expect("already on disk");
    assert!(path.ends_with("tmdb-person-7.jpg"));
}
