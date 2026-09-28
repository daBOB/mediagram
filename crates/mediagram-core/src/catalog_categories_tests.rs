use super::*;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

#[test]
fn a_v11_index_has_no_categories_table_and_answers_empty() {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(11) {
        conn.execute(stmt, []).unwrap();
    }

    assert_eq!(categories(&conn).unwrap(), HashMap::new());
}

#[test]
fn an_index_with_the_table_but_no_rows_answers_empty() {
    let conn = index();

    assert_eq!(categories(&conn).unwrap(), HashMap::new());
}

#[test]
fn a_null_row_is_cleared_and_skipped() {
    let conn = index();
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('tutorials', 'title-rust-course', NULL, 1)",
        [],
    )
    .unwrap();

    assert_eq!(categories(&conn).unwrap().get(&("tutorials".to_string(), "title-rust-course".to_string())), None);
}

#[test]
fn filed_categories_carry_in_by_department_and_item_key() {
    let conn = index();
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at)
         VALUES ('tutorials', 'title-rust-course', 'Trading', 1), ('documentaries', 'title-terra-x', 'Science', 1)",
        [],
    )
    .unwrap();

    let map = categories(&conn).unwrap();
    assert_eq!(map.get(&("tutorials".to_string(), "title-rust-course".to_string())), Some(&"Trading".to_string()));
    assert_eq!(map.get(&("documentaries".to_string(), "title-terra-x".to_string())), Some(&"Science".to_string()));
}

#[test]
fn category_of_resolves_a_course_lesson_by_its_show() {
    let conn = index();
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('tutorials', 'title-rust-course', 'Trading', 1)",
        [],
    )
    .unwrap();
    let map = categories(&conn).unwrap();

    assert_eq!(category_of(&map, "tut", Some("Rust Course"), None), Some("Trading".to_string()));
}

#[test]
fn category_of_answers_none_for_a_film_or_an_episode() {
    let map = HashMap::new();
    assert_eq!(category_of(&map, "movie", None, Some("Dune")), None);
    assert_eq!(category_of(&map, "ep", Some("Show"), None), None);
}

#[test]
fn category_of_answers_none_for_an_unfiled_unit() {
    let map = HashMap::new();
    assert_eq!(category_of(&map, "tut", Some("Untouched Course"), None), None);
}
