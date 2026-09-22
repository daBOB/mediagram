use super::*;

/// An index carrying one `shows` row per entry, in the language named.
///
/// Built from the shared migrations rather than from a hand-written `CREATE
/// TABLE`, so a column added to them reaches this fixture too and the query
/// under test is run against the table it will actually meet. In memory,
/// because nothing here needs a file.
fn index_describing(rows: &[(&str, u32)]) -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for statement in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(statement, []).unwrap();
    }
    let mut id = 0i64;
    for (lang, count) in rows {
        for _ in 0..*count {
            id += 1;
            conn.execute(
                "INSERT INTO shows(source, kind, id, lang) VALUES ('tmdb', 'movie', ?1, ?2)",
                rusqlite::params![id, lang],
            )
            .unwrap();
        }
    }
    conn
}

/// The library says what language it was described in. Asking a provider in
/// a different one produces a shelf where some titles read in German and the
/// rest in English.
#[test]
fn the_language_is_read_from_the_rows_the_index_already_carries() {
    let conn = index_describing(&[("de-DE", 4)]);

    assert_eq!(language_of(&conn, "en-US"), "de-DE");
}

/// A library nobody has described yet has nothing to read, so the caller's
/// own locale is the best guess available.
#[test]
fn a_library_with_no_descriptions_falls_back_to_the_caller() {
    let conn = index_describing(&[]);

    assert_eq!(language_of(&conn, "en-US"), "en-US");
}

/// Rows in more than one language mean the library was described twice; the
/// one it mostly speaks is the one to keep asking in.
///
/// Asserted in both directions on purpose. A query that merely takes the
/// first group SQLite hands back gets the one whose language sorts first,
/// so a single majority that also sorts first would pass without the
/// counting being done at all.
#[test]
fn a_mixed_library_keeps_the_language_most_of_it_uses() {
    assert_eq!(language_of(&index_describing(&[("de-DE", 9), ("en-US", 2)]), "en-US"), "de-DE");
    assert_eq!(language_of(&index_describing(&[("de-DE", 2), ("en-US", 9)]), "de-DE"), "en-US");
}

/// `lang` is `''` for a row that names no language — the column's own
/// default, and what an index written before it existed carries. Read back
/// as an answer it would be sent to the provider as `language=`, which asks
/// for nothing rather than for the caller's locale.
#[test]
fn rows_that_name_no_language_are_not_an_answer() {
    let conn = index_describing(&[("", 9), ("de-DE", 2)]);

    assert_eq!(language_of(&conn, "en-US"), "de-DE");
}
