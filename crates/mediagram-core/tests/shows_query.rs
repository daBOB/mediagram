//! What a title's description reads back as, from the two places one can
//! sit: the index the uploader wrote, and the sidecar this device fetched
//! into. Nothing here reaches TMDB — both rows are already on disk.

use mlib_spec::Kind;
use rusqlite::Connection;

use mediagram_core::api::{Core, details};
use mediagram_tmdb::details::ShowRow;

fn core_at(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// The downloaded index's own database, carrying the shared schema and no
/// rows yet.
fn index_db(dir: &std::path::Path) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

fn catalog_with_show(dir: &std::path::Path, kind: &str, id: i64) {
    index_db(dir)
        .execute(
            "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating)
             VALUES ('tmdb', ?1, ?2, 'en-US', 'Dracula is awakened.', 'The final hunt begins.', 'Action, Horror', 5.9)",
            rusqlite::params![kind, id],
        )
        .unwrap();
}

/// One `shows` row in the index itself, as whoever last ran `mediagram
/// metadata` before pushing it would have left it.
fn index_describes(dir: &std::path::Path, kind: &str, id: i64, overview: &str) {
    index_db(dir)
        .execute(
            "INSERT INTO shows(source, kind, id, lang, overview)
             VALUES ('tmdb', ?1, ?2, 'en-US', ?3)",
            rusqlite::params![kind, id, overview],
        )
        .unwrap();
}

fn index_describes_nothing(dir: &std::path::Path) {
    index_db(dir);
}

/// One row in the sidecar, as a fetch on this device would leave it.
/// `ShowRow` carries no source of its own — `upsert` writes the one the index
/// writes — and names the kind with the provider's own enum.
fn fetched_describes(core: &Core, kind: Kind, id: u64, overview: &str) {
    let row = ShowRow {
        kind,
        id,
        lang: "en-US".into(),
        overview: Some(overview.into()),
        tagline: None,
        genres: None,
        rating: None,
        network: None,
        status: None,
        first_air: None,
        last_air: None,
        total_seasons: None,
        total_episodes: None,
    };
    let conn = details::open_or_create(core).unwrap();
    details::upsert(&conn, &row).unwrap();
}

#[test]
fn a_film_reports_what_the_provider_said_about_it() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = core_at(dir.path());

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
    let core = core_at(dir.path());

    assert!(core.show_info("tmdb-tv-1399".into()).is_some());
}

/// A title the uploader never resolved has no row, and that is ordinary
/// rather than an error: a course has no provider entry at all.
#[test]
fn a_title_with_no_recorded_description_says_nothing_rather_than_failing() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = core_at(dir.path());

    assert!(core.show_info("tmdb-movie-99999".into()).is_none());
}

/// A key that is not a key never reaches SQL.
#[test]
fn a_malformed_key_is_refused_before_it_is_queried() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_show(dir.path(), "movie", 11225);
    let core = core_at(dir.path());

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
    let malicious_kind = "movie'; DROP TABLE shows;";
    index_describes(dir.path(), malicious_kind, 11225, "should never be read");
    let core = core_at(dir.path());

    assert!(core.show_info(format!("tmdb-{malicious_kind}-11225")).is_none());
}

/// The publisher's own description wins. It was written in the library's
/// language by whoever curated it, and a phone that fetched its own copy has
/// no better claim on the same title.
#[test]
fn a_row_in_the_index_is_preferred_to_a_fetched_one() {
    let dir = tempfile::tempdir().unwrap();
    index_describes(dir.path(), "movie", 550, "what the publisher wrote");
    let core = core_at(dir.path());
    fetched_describes(&core, Kind::Movie, 550, "what the phone fetched");

    let info = core.show_info("tmdb-movie-550".into()).expect("a described title");
    assert_eq!(info.overview.as_deref(), Some("what the publisher wrote"));
}

/// A title the index says nothing about is what a fetch is for.
#[test]
fn a_title_the_index_omits_is_answered_from_the_sidecar() {
    let dir = tempfile::tempdir().unwrap();
    index_describes_nothing(dir.path());
    let core = core_at(dir.path());
    fetched_describes(&core, Kind::Movie, 550, "what the phone fetched");

    let info = core.show_info("tmdb-movie-550".into()).expect("a described title");
    assert_eq!(info.overview.as_deref(), Some("what the phone fetched"));
}

/// Neither holding it is not an error. A course has no provider entry, and a
/// library assembled without a key has no rows at all.
#[test]
fn a_title_neither_holds_is_simply_unknown() {
    let dir = tempfile::tempdir().unwrap();
    index_describes_nothing(dir.path());
    let core = core_at(dir.path());

    assert!(core.show_info("tmdb-movie-550".into()).is_none());
}

/// A library nobody has fetched for has no sidecar at all, and a lookup must
/// not bring one into being: a read that wrote would leave a store behind on
/// every device that merely opened a title.
#[test]
fn a_lookup_does_not_create_the_store_it_did_not_find() {
    let dir = tempfile::tempdir().unwrap();
    index_describes_nothing(dir.path());
    let core = core_at(dir.path());

    assert!(core.show_info("tmdb-movie-550".into()).is_none());
    let store = details::details_db(&core);
    assert!(!store.exists(), "a lookup created a store nobody had written to");
}

/// An invalid key reaches neither store. Two lookup locations must not become
/// two ways past one guard.
#[test]
fn an_invalid_key_is_refused_before_either_store_is_opened() {
    let dir = tempfile::tempdir().unwrap();
    index_describes_nothing(dir.path());
    let core = core_at(dir.path());
    fetched_describes(&core, Kind::Movie, 550, "unreachable");

    assert!(core.show_info("not-a-valid-poster-key-42x".into()).is_none());
}
