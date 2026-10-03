use rusqlite::{Connection, params};

use super::*;

/// An in-memory index at this crate's own schema.
fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// A set with no parts, which `PLAYABLE_SQL` plays: zero parts done of zero, zero bytes of zero.
fn add(
    conn: &Connection,
    set_id: &str,
    kind: &str,
    show: Option<&str>,
    tmdb: Option<u64>,
    status: &str,
) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, title, show, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, ?2, ?1, ?3, ?4, 'mkv', 0, 0, ?5, 1, 3)",
        params![set_id, kind, show, tmdb, status],
    )
    .unwrap();
}

fn described(conn: &Connection, kind: &str, id: u64, genres: &str) {
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', ?1, ?2, ?3)",
        params![kind, id, genres],
    )
    .unwrap();
}

fn sorted(
    (mut library, mut collections): (Vec<LibraryTitle>, Vec<LibraryCollection>),
) -> (Vec<LibraryTitle>, Vec<LibraryCollection>) {
    library.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    for collection in &mut collections {
        collection.set_ids.sort();
    }
    collections.sort_by(|a, b| a.id.cmp(&b.id));
    (library, collections)
}

fn title(set_id: &str, kind: &str, genres: &[&str], collection: Option<&str>) -> LibraryTitle {
    LibraryTitle {
        set_id: set_id.into(),
        kind: kind.into(),
        genres: genres.iter().map(|genre| genre.to_string()).collect(),
        collection: collection.map(str::to_string),
    }
}

#[test]
fn a_film_carries_its_own_genres_and_an_episode_its_shows() {
    let conn = index();
    add(&conn, "heat", "movie", None, Some(949), "complete");
    add(
        &conn,
        "bb1",
        "ep",
        Some("Breaking Bad"),
        Some(1396),
        "complete",
    );
    described(&conn, "movie", 949, "Crime, Drama");
    described(&conn, "tv", 1396, "Drama");
    let (library, _) = sorted(library_facts(&conn).unwrap());
    assert_eq!(
        library,
        vec![
            title("bb1", "ep", &["Drama"], Some("ep:Breaking Bad")),
            title("heat", "movie", &["Crime", "Drama"], None),
        ]
    );
}

#[test]
fn episodes_group_by_show_and_lessons_by_course_and_nothing_else_does() {
    let conn = index();
    for (set_id, kind, show) in [
        ("e1", "ep", Some("Dark")),
        ("e2", "ep", Some("Dark")),
        ("lone", "ep", None),
        ("l1", "tut", Some("Rust")),
        ("handout", "doc", Some("Rust")),
        ("terra", "docu", Some("Terra X")),
    ] {
        add(&conn, set_id, kind, show, None, "complete");
    }
    let (library, collections) = sorted(library_facts(&conn).unwrap());
    assert_eq!(
        collections,
        vec![
            LibraryCollection {
                id: "ep:Dark".into(),
                set_ids: vec!["e1".into(), "e2".into()]
            },
            LibraryCollection {
                id: "tut:Rust".into(),
                set_ids: vec!["l1".into()]
            },
        ]
    );
    let alone: Vec<&str> = library
        .iter()
        .filter(|title| title.collection.is_none())
        .map(|title| title.set_id.as_str())
        .collect();
    assert_eq!(alone, vec!["handout", "lone", "terra"]);
}

#[test]
fn a_set_the_catalog_will_not_play_is_not_in_the_library() {
    let conn = index();
    add(&conn, "ready", "movie", None, None, "complete");
    add(&conn, "uploading", "movie", None, None, "pending");
    let (library, _) = library_facts(&conn).unwrap();
    assert_eq!(
        library
            .iter()
            .map(|title| title.set_id.as_str())
            .collect::<Vec<_>>(),
        vec!["ready"]
    );
}

#[test]
fn the_shelves_and_the_rules_name_a_collection_alike() {
    assert_eq!(collection_of("ep", Some("Dark")), Some("ep:Dark".into()));
    assert_eq!(collection_of("tut", Some("Rust")), Some("tut:Rust".into()));
    assert_eq!(collection_of("ep", Some("")), None);
    assert_eq!(collection_of("doc", Some("Rust")), None);
    assert_eq!(collection_of("movie", None), None);
}
