use super::*;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// Every series kind is keyed `tv`, so a series key reads back as an
/// episode's kind and finds the row any of them would.
#[test]
fn a_provider_key_names_its_kind_and_id() {
    assert_eq!(title_of("tmdb-movie-603"), Some((Kind::Movie, 603)));
    assert_eq!(title_of("tmdb-tv-95396"), Some((Kind::Ep, 95396)));
}

/// Valid keys that name no single title — a season's art, a backdrop, a
/// title with no provider id, another provider — read as nobody.
#[test]
fn a_key_that_is_not_a_titles_poster_names_no_title() {
    for key in [
        "tmdb-tv-1396-s2",
        "tmdb-movie-550-bg",
        "title-terra-x",
        "tvdb-tv-81189",
        "tmdb-person-287",
        "tmdb-movie-99999999999999999999",
    ] {
        assert_eq!(title_of(key), None, "{key}");
    }
}

#[test]
fn a_malformed_key_is_refused() {
    for key in [
        "",
        "tmdb",
        "tmdb-movie-",
        "tmdb-movie-60a",
        "TMDB-movie-603",
        "tmdb-movie-603;--",
        "../tmdb-movie-603",
    ] {
        assert_eq!(title_of(key), None, "{key:?}");
    }
}

/// TMDB numbers films and series independently, so 550 is two titles.
#[test]
fn reading_by_key_finds_that_kinds_row() {
    let conn = index();
    conn.execute_batch(
        "INSERT INTO shows(source, kind, id, overview) VALUES ('tmdb', 'movie', 550, 'the film');
         INSERT INTO shows(source, kind, id, overview) VALUES ('tmdb', 'tv', 550, 'the series');",
    )
    .unwrap();
    let overview = |key: &str| read(&conn, key).unwrap().and_then(|row| row.overview);
    assert_eq!(overview("tmdb-movie-550").as_deref(), Some("the film"));
    assert_eq!(overview("tmdb-tv-550").as_deref(), Some("the series"));
    assert_eq!(read(&conn, "tmdb-movie-551").unwrap(), None);
}

/// Refused before SQL: a connection with no `shows` table at all still
/// answers a malformed key without an error.
#[test]
fn a_key_naming_no_title_never_reaches_the_database() {
    let bare = Connection::open_in_memory().unwrap();
    assert_eq!(read(&bare, "title-terra-x").unwrap(), None);
    assert!(read(&bare, "tmdb-movie-550").is_err());
}
