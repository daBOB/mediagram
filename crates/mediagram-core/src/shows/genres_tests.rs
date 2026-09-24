use super::*;
use rusqlite::params;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

#[test]
fn genres_are_split_trimmed_and_keyed_by_poster_key() {
    let conn = index();
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, ' Drama ,Action')",
        [],
    )
    .unwrap();

    let by_key = genres(&conn).unwrap();

    assert_eq!(by_key.get("tmdb-movie-550"), Some(&vec!["Drama".to_string(), "Action".to_string()]));
}

#[test]
fn a_show_with_no_genres_recorded_is_absent_rather_than_an_empty_list() {
    let conn = index();
    conn.execute("INSERT INTO shows(source, kind, id) VALUES ('tmdb', 'movie', 550)", []).unwrap();

    assert_eq!(genres(&conn).unwrap().get("tmdb-movie-550"), None);
}

#[test]
fn a_blank_genres_column_is_also_absent() {
    let conn = index();
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, ?1)",
        params![" , , "],
    )
    .unwrap();

    assert_eq!(genres(&conn).unwrap().get("tmdb-movie-550"), None);
}
