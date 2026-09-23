//! The `shows` table has one writer, `mediagram_core::shows::upsert`, and its
//! columns come from the shared DDL in `mlib_spec::schema`.
//!
//! Those two can drift: add a column to the DDL and not to the statement and
//! everything compiles, every other test passes, and the table silently stops
//! recording it. This test writes a row with every field set and reads the
//! table back generically, so a column nothing writes shows up as NULL.
//!
//! The row below names every field explicitly, so a field added to `TitleDetailsRow`
//! breaks this file until someone fills it in. That is deliberate: a column
//! nobody sets here would read back NULL while meaning nothing.

use mediagram_tmdb::details::TitleDetailsRow;
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
fn a_fully_populated_row() -> TitleDetailsRow {
    TitleDetailsRow {
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
        certification: Some("16".into()),
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
fn the_writer_records_every_column_the_shared_schema_declares() {
    let conn = shared_schema();
    mediagram_core::shows::upsert(&conn, &a_fully_populated_row()).expect("the write");

    let unwritten: Vec<String> = the_only_row(&conn)
        .into_iter()
        .filter(|(_, value)| matches!(value, Value::Null))
        .map(|(name, _)| name)
        .collect();
    assert!(
        unwritten.is_empty(),
        "the shared schema declares {unwritten:?}, which `shows::upsert` does not \
         set. Add the column to the statement and to `TitleDetailsRow`, or say in \
         mlib_spec::schema why the table holds it unwritten."
    );
}
