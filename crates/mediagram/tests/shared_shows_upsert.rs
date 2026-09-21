//! The uploader and the phone write the same `shows` row.
//!
//! There are two writers into that table and they cannot share code: the
//! uploader fills the index it owns, the phone fills a sidecar beside an index
//! it opens read-only, and the two crates cannot depend on each other. So the
//! statement is duplicated on purpose — `mediagram::index::shows::upsert` and
//! `mediagram_core::api::details::upsert`, the same fourteen columns apart
//! from the error type.
//!
//! What is *not* duplicated is the schema: both databases are built from
//! `mlib_spec::schema`. That is the asymmetry this test exists for. Add a
//! column to the shared DDL and to only one of the two statements and
//! everything compiles, every other test passes, and one of the two stores
//! silently stops recording it — until a phone shows a blank where the
//! uploader shows a value, or the reverse, with nothing having failed.
//!
//! The row below names every field explicitly, so a field added to `ShowRow`
//! breaks this file until someone fills it in. That is deliberate: a column
//! nobody sets here would compare NULL to NULL and pass while meaning
//! nothing.

use mediagram_tmdb::details::ShowRow;
use mlib_spec::Kind;
use mlib_spec::schema;
use rusqlite::Connection;
use rusqlite::types::Value;

/// An empty database at the current schema, built from the shared migrations
/// exactly as both real stores build theirs.
fn shared_schema() -> Connection {
    let conn = Connection::open_in_memory().expect("an in-memory database");
    for statement in schema::migrations_up_to(schema::SCHEMA_VERSION) {
        conn.execute(statement, []).expect("a statement from the shared migrations");
    }
    conn
}

/// Every field set to something distinguishable, so a column one side forgets
/// reads back as NULL against a value rather than as one blank against another.
fn a_fully_populated_row() -> ShowRow {
    ShowRow {
        kind: Kind::Ep,
        id: 84_773,
        lang: "de-DE".into(),
        overview: Some("Was das Haus verbirgt.".into()),
        tagline: Some("Zwei ungewöhnliche Jahrgänge.".into()),
        genres: Some("Drama, Mystery".into()),
        rating: Some(7.6),
        network: Some("Apple TV+".into()),
        status: Some("Returning Series".into()),
        first_air: Some("2023-04-21".into()),
        last_air: Some("2023-06-02".into()),
        total_seasons: Some(2),
        total_episodes: Some(18),
    }
}

/// Reads the one row back generically — whatever columns the table has, in
/// the order it declares them. Reading by name would only ever see the columns
/// this test already knew about, which is the blind spot it is here to close.
fn the_only_row(conn: &Connection) -> Vec<(String, Value)> {
    let mut statement = conn.prepare("SELECT * FROM shows").expect("a select");
    let columns: Vec<String> = statement.column_names().into_iter().map(str::to_string).collect();
    let mut rows = statement.query([]).expect("a row set");
    let row = rows.next().expect("a readable row").expect("exactly one row");
    columns
        .iter()
        .enumerate()
        .map(|(index, name)| (name.clone(), row.get::<_, Value>(index).expect("a column")))
        .collect()
}

#[test]
fn both_writers_record_every_column_the_shared_schema_declares() {
    let row = a_fully_populated_row();

    let uploader = shared_schema();
    mediagram::index::shows::upsert(&uploader, &row).expect("the uploader's write");

    let phone = shared_schema();
    mediagram_core::api::details::upsert(&phone, &row).expect("the phone's write");

    let written_by_uploader = the_only_row(&uploader);
    let written_by_phone = the_only_row(&phone);

    assert_eq!(
        written_by_uploader, written_by_phone,
        "the two `shows` writers disagree. One of\n  \
         crates/mediagram/src/index/shows.rs\n  \
         crates/mediagram-core/src/api/details.rs\n\
         was updated and the other was not; they write the same table from the \
         same shared DDL and must name the same columns."
    );

    // Neither side updated is the other way this drifts: a column reaches the
    // shared migrations and no writer at all, and the comparison above passes
    // by comparing two NULLs. Every column the DDL declares carries a value in
    // this fixture, so a new one that nothing writes is caught here instead.
    let unwritten: Vec<&str> = written_by_uploader
        .iter()
        .filter(|(_, value)| matches!(value, Value::Null))
        .map(|(name, _)| name.as_str())
        .collect();
    assert!(
        unwritten.is_empty(),
        "the shared schema declares {unwritten:?}, which neither writer sets. \
         Add the column to both upserts and to `ShowRow`, or say in \
         mlib_spec::schema why one store holds it and the other does not."
    );
}
