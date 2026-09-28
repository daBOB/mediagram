use super::*;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

#[test]
fn a_v10_index_has_no_overrides_table_and_answers_empty() {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(10) {
        conn.execute(stmt, []).unwrap();
    }

    assert_eq!(anime_overrides(&conn).unwrap(), HashMap::new());
}

#[test]
fn an_index_with_the_table_but_no_rows_answers_empty() {
    let conn = index();

    assert_eq!(anime_overrides(&conn).unwrap(), HashMap::new());
}

#[test]
fn a_null_row_is_back_to_automatic_and_skipped() {
    let conn = index();
    conn.execute(
        "INSERT INTO anime_overrides(source, kind, id, anime, set_at) VALUES ('tmdb', 'tv', 1396, NULL, 1)",
        [],
    )
    .unwrap();

    assert_eq!(anime_overrides(&conn).unwrap().get("tmdb-tv-1396"), None);
}

#[test]
fn overrides_are_keyed_by_poster_key_and_carry_in_and_out() {
    let conn = index();
    conn.execute(
        "INSERT INTO anime_overrides(source, kind, id, anime, set_at) VALUES ('tmdb', 'tv', 1, 1, 1), ('tmdb', 'movie', 2, 0, 1)",
        [],
    )
    .unwrap();

    let overrides = anime_overrides(&conn).unwrap();
    assert_eq!(overrides.get("tmdb-tv-1"), Some(&true));
    assert_eq!(overrides.get("tmdb-movie-2"), Some(&false));
}

#[test]
fn only_a_film_or_an_episode_can_ever_be_anime() {
    assert!(!is_anime("docu", &["Animation".to_string()], Some("ja"), Some(true)));
    assert!(!is_anime("tut", &["Animation".to_string()], Some("ja"), None));
}

#[test]
fn an_override_wins_over_the_automatic_rule() {
    assert!(is_anime("ep", &["Animation".to_string()], Some("zh"), Some(true)));
    assert!(!is_anime("movie", &["Animation".to_string()], Some("ja"), Some(false)));
}

#[test]
fn automatic_needs_japanese_and_the_animation_genre_together() {
    assert!(is_anime("movie", &["Animation".to_string()], Some("ja"), None));
    assert!(!is_anime("movie", &["Animation".to_string()], Some("en"), None));
    assert!(!is_anime("movie", &["Drama".to_string()], Some("ja"), None));
    assert!(!is_anime("movie", &["Animation".to_string()], None, None));
}
