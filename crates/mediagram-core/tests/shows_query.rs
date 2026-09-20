//! The uploader writes a title's description into the index; this reads it
//! back. Nothing here reaches TMDB — the row is already on disk.

use rusqlite::Connection;

fn catalog_with_show(dir: &std::path::Path, kind: &str, id: i64) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating)
         VALUES ('tmdb', ?1, ?2, 'en-US', 'Dracula is awakened.', 'The final hunt begins.', 'Action, Horror', 5.9)",
        rusqlite::params![kind, id],
    )
    .unwrap();
}

#[test]
fn a_film_reports_what_the_provider_said_about_it() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    let info = core.show_info("tmdb-movie-11225".into()).expect("a recorded show");

    assert_eq!(info.overview.as_deref(), Some("Dracula is awakened."));
    assert_eq!(info.tagline.as_deref(), Some("The final hunt begins."));
    assert_eq!(info.genres.as_deref(), Some("Action, Horror"));
    assert_eq!(info.rating, Some(5.9));
}

/// Every episode of a series shares one row, so the key must be the series'.
#[test]
fn a_series_key_finds_the_row_that_belongs_to_the_whole_show() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "tv", 1399);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("tmdb-tv-1399".into()).is_some());
}

/// A title the uploader never resolved has no row, and that is ordinary
/// rather than an error: a course has no provider entry at all.
#[test]
fn a_title_with_no_recorded_description_says_nothing_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("tmdb-movie-99999".into()).is_none());
}

/// A key that is not a key never reaches SQL.
#[test]
fn a_malformed_key_is_refused_before_it_is_queried() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info("'; DROP TABLE shows; --".into()).is_none());
}

/// A key that begins with `tmdb-` and still carries an injection payload in
/// its `kind` segment must be refused too, even when a row exists that
/// would otherwise answer the query the parsed pieces describe. A row is
/// planted under the exact malicious `kind` on purpose: if the key were let
/// through unchecked, the query would find it and answer with its text,
/// which is what proves the key never reached the query at all rather than
/// merely matching nothing.
#[test]
fn a_key_carrying_an_injection_payload_is_refused_even_when_a_matching_row_exists() {
    let dir = tempfile::tempdir().unwrap();
    let current = dir.path().join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    let malicious_kind = "movie'; DROP TABLE shows;";
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview)
         VALUES ('tmdb', ?1, 11225, 'en-US', 'should never be read')",
        rusqlite::params![malicious_kind],
    )
    .unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string(), 1, "test-hash".into());

    assert!(core.show_info(format!("tmdb-{malicious_kind}-11225")).is_none());
}
