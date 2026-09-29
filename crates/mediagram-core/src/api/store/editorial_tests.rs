use rusqlite::Connection;

use super::*;

/// `<dir>/catalog/current/library.db`, migrated to this crate's current
/// schema — the same layout `credits_tests.rs`'s own `index_at` builds;
/// `current_dir` reads a plain join, not a symlink, so a test directory
/// serves as well as a real install.
fn index_at(dir: &std::path::Path) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// A playable set with no parts at all: `PLAYABLE_SQL` only asks that
/// `part_count` and `total` match what `parts` actually holds, and an empty
/// part list makes both true of zero. Enough for `list_sets`/`media_set`,
/// neither of which ever reads a part — only `read.rs` does.
fn insert_set(
    conn: &Connection,
    set_id: &str,
    kind: &str,
    show: Option<&str>,
    title: Option<&str>,
    season: Option<u32>,
    tmdb: Option<u64>,
) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, show, title, season, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'mkv', 0, 0, 'complete', 0, 1)",
        rusqlite::params![set_id, kind, show, title, season, tmdb],
    )
    .unwrap();
}

fn write_poster(base: &std::path::Path, key: &str) {
    std::fs::create_dir_all(base).unwrap();
    std::fs::write(base.join(format!("{key}.jpg")), b"fake-poster-bytes").unwrap();
}

fn insert_artwork(conn: &Connection, key: &str, bytes: &[u8]) {
    conn.execute("INSERT INTO artwork(key, mime, bytes) VALUES (?1, 'image/jpeg', ?2)", rusqlite::params![key, bytes])
        .unwrap();
}

#[test]
fn a_listing_carries_a_resolved_poster_path() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    write_poster(&artwork_dir(&core), "tmdb-movie-550");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-movie-550.jpg").display().to_string();
    assert_eq!(set.poster_path, Some(expected));
}

#[test]
fn a_listing_answers_none_for_artwork_nobody_has_fetched() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.poster_path, None);
}

/// Matches `CatalogRepository.kt`'s own note: an episode is filed under its
/// show's poster key, never one of its own.
#[test]
fn an_episode_resolves_its_shows_poster_not_one_of_its_own() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "e1", "ep", Some("Breaking Bad"), None, Some(2), Some(1396));
    write_poster(&artwork_dir(&core), "tmdb-tv-1396");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.poster_key.as_deref(), Some("tmdb-tv-1396"));
    let expected = artwork_dir(&core).join("tmdb-tv-1396.jpg").display().to_string();
    assert_eq!(set.poster_path, Some(expected));
}

#[test]
fn an_episodes_season_poster_resolves_beside_its_shows() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "e1", "ep", Some("Breaking Bad"), None, Some(2), Some(1396));
    write_poster(&artwork_dir(&core), "tmdb-tv-1396");
    write_poster(&artwork_dir(&core), "tmdb-tv-1396-s2");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-tv-1396-s2.jpg").display().to_string();
    assert_eq!(set.season_poster_path, Some(expected));
}

#[test]
fn a_season_poster_missing_on_disk_materialises_from_the_artwork_table() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "e1", "ep", Some("Breaking Bad"), None, Some(2), Some(1396));
    write_poster(&artwork_dir(&core), "tmdb-tv-1396");
    insert_artwork(&conn, "tmdb-tv-1396-s2", &[3, 4]);
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-tv-1396-s2.jpg");
    assert_eq!(set.season_poster_path, Some(expected.display().to_string()));
    assert_eq!(std::fs::read(&expected).unwrap(), vec![3, 4]);
}

#[test]
fn a_season_with_no_poster_of_its_own_still_leaves_the_shows_resolved() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "e1", "ep", Some("Breaking Bad"), None, Some(2), Some(1396));
    write_poster(&artwork_dir(&core), "tmdb-tv-1396");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.season_poster_path, None);
    assert!(set.poster_path.is_some());
}

#[test]
fn a_film_never_carries_a_season_poster() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), Some(2), Some(550));
    write_poster(&artwork_dir(&core), "tmdb-movie-550-s2");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.season_poster_path, None, "only an episode's own key ever asks for a season poster");
}

#[test]
fn a_backdrop_already_on_disk_resolves() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    write_poster(&artwork_dir(&core), "tmdb-movie-550-bg");
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-movie-550-bg.jpg").display().to_string();
    assert_eq!(set.backdrop_path, Some(expected));
}

/// A backdrop missing on disk materialises from the `artwork` table exactly
/// as a poster does (below) — parity with the web player's own `has()`,
/// which counts a backdrop the table alone carries the same way it does a
/// poster.
#[test]
fn a_backdrop_held_only_in_the_artwork_table_materialises() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    insert_artwork(&conn, "tmdb-movie-550-bg", &[1]);
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-movie-550-bg.jpg");
    assert_eq!(set.backdrop_path, Some(expected.display().to_string()));
    assert_eq!(std::fs::read(&expected).unwrap(), vec![1]);
}

#[test]
fn a_poster_missing_on_disk_materialises_from_the_artwork_table() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    insert_artwork(&conn, "tmdb-movie-550", &[1, 2]);
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    let expected = artwork_dir(&core).join("tmdb-movie-550.jpg");
    assert_eq!(set.poster_path, Some(expected.display().to_string()));
    assert_eq!(std::fs::read(&expected).unwrap(), vec![1, 2], "written for every later lookup, not just answered once");
}

/// The precise bug a coarse "does the table exist at all" check would miss:
/// a table that holds rows, just none of them for this set's own key, must
/// still answer `None` without ever asking the table about a key it does
/// not have — see `crate::artwork::keys`.
#[test]
fn a_key_absent_from_the_artwork_table_is_never_queried_even_when_the_table_holds_other_keys() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    insert_artwork(&conn, "tmdb-movie-999", &[1]);
    crate::artwork::reset_get_calls();
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.poster_path, None);
    assert_eq!(crate::artwork::get_calls(), 0, "a key the table does not hold must never be queried");
}

/// The other half of the same fix: many rows sharing the one key the table
/// *does* hold must still query it once, not once per row that names it.
#[test]
fn a_key_shared_by_many_sets_is_queried_from_the_artwork_table_once() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    for i in 0..5 {
        insert_set(&conn, &format!("e{i}"), "ep", Some("Breaking Bad"), None, Some(1), Some(1396));
    }
    insert_artwork(&conn, "tmdb-tv-1396", &[9]);
    crate::artwork::reset_get_calls();
    drop(conn);

    let sets = list_sets(&core).unwrap();
    assert_eq!(sets.len(), 5);
    let expected = artwork_dir(&core).join("tmdb-tv-1396.jpg").display().to_string();
    assert!(sets.iter().all(|s| s.poster_path == Some(expected.clone())));
    assert_eq!(crate::artwork::get_calls(), 1, "materialising one key shared by many rows must query the table once");
}

/// A single-set lookup carries the same resolved record a listing would —
/// genres, subtitles and a materialised backdrop included, not just the
/// bare row, so the comparison actually exercises `enrich`'s own work
/// rather than passing on an all-`None` record.
#[test]
fn one_set_lookup_finds_the_same_resolved_row_a_listing_would() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    insert_set(&conn, "m2", "movie", None, Some("Arrival"), None, Some(329_865));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres, tagline, rating) VALUES ('tmdb', 'movie', 550, 'Sci-Fi,Adventure', 'Beyond fear, destiny awaits.', 8.1)",
        [],
    )
    .unwrap();
    conn.execute("INSERT INTO assets(set_id, kind, lang, body) VALUES ('m1', 'subtitle', 'en', 'body')", []).unwrap();
    write_poster(&artwork_dir(&core), "tmdb-movie-550");
    write_poster(&artwork_dir(&core), "tmdb-movie-550-bg");
    drop(conn);

    let listed = list_sets(&core).unwrap();
    let from_listing = listed.iter().find(|s| s.set_id == "m1").expect("m1 is listed");
    let found = media_set(&core, "m1").unwrap().expect("m1 is playable");

    assert_eq!(&found, from_listing);
    // Not a vacuous comparison of two all-`None` records: real facts and
    // artwork are present on both sides.
    assert_eq!(found.genres, vec!["Sci-Fi", "Adventure"]);
    assert_eq!(found.subtitles, vec!["en"]);
    assert!(found.poster_path.is_some());
    assert!(found.backdrop_path.is_some());
}

#[test]
fn one_set_lookup_answers_none_for_an_unknown_id() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    drop(conn);

    assert_eq!(media_set(&core, "nobody").unwrap(), None);
}

/// The same "nothing installed is an empty answer, not a failure" rule
/// `list_sets` follows before any catalog has ever loaded.
#[test]
fn one_set_lookup_answers_none_before_any_catalog_is_loaded() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());

    assert_eq!(media_set(&core, "m1").unwrap(), None);
}

/// The automatic rule, from the index's own `original_language`: Japanese
/// animation is anime, everything else is not.
#[test]
fn anime_is_decided_from_index_facts() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Spirited Away"), None, Some(129));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres, original_language) VALUES ('tmdb', 'movie', 129, 'Animation,Fantasy', 'ja')",
        [],
    )
    .unwrap();
    insert_set(&conn, "m2", "movie", None, Some("Dune"), None, Some(550));
    drop(conn);

    let sets = list_sets(&core).unwrap();
    assert!(sets.iter().find(|s| s.set_id == "m1").unwrap().anime);
    assert!(!sets.iter().find(|s| s.set_id == "m2").unwrap().anime);
}

/// The web has no device sidecar (`enrich`'s own doc), but Android does: a
/// title the index says nothing about, fetched with `ja` and `Animation` by
/// this device, still shelves as anime.
#[test]
fn anime_is_decided_from_sidecar_facts_when_the_index_lacks_the_language() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Spirited Away"), None, Some(129));
    drop(conn);

    let catalog_dir = crate::api::store::dir(&core);
    std::fs::create_dir_all(&catalog_dir).unwrap();
    let fetched = Connection::open(catalog_dir.join("details.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        fetched.execute(stmt, []).unwrap();
    }
    fetched
        .execute(
            "INSERT INTO shows(source, kind, id, genres, original_language) VALUES ('tmdb', 'movie', 129, 'Animation', 'ja')",
            [],
        )
        .unwrap();
    drop(fetched);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert!(set.anime);
}

/// An override wins over the automatic rule either way.
#[test]
fn an_override_wins_over_the_automatic_rule() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "in", "ep", Some("Donghua"), None, Some(1), Some(1));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres, original_language) VALUES ('tmdb', 'tv', 1, 'Animation', 'zh')",
        [],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO anime_overrides(source, kind, id, anime, set_at) VALUES ('tmdb', 'tv', 1, 1, 1)",
        [],
    )
    .unwrap();
    insert_set(&conn, "out", "movie", None, Some("Not Really Anime"), None, Some(2));
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres, original_language) VALUES ('tmdb', 'movie', 2, 'Animation', 'ja')",
        [],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO anime_overrides(source, kind, id, anime, set_at) VALUES ('tmdb', 'movie', 2, 0, 1)",
        [],
    )
    .unwrap();
    drop(conn);

    let sets = list_sets(&core).unwrap();
    assert!(sets.iter().find(|s| s.set_id == "in").unwrap().anime);
    assert!(!sets.iter().find(|s| s.set_id == "out").unwrap().anime);
}

/// A v10 index has neither `original_language` nor `anime_overrides`: every
/// title lists `anime: false`, never an error.
#[test]
fn a_v10_index_lists_everything_as_not_anime() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let current = dir.path().join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(10) {
        conn.execute(stmt, []).unwrap();
    }
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    conn.execute("INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 550, 'Animation')", [])
        .unwrap();
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert!(!set.anime);
}

/// A course lesson and one of its documents key by the same show name, so a
/// category filed on the course reaches both.
#[test]
fn a_filed_courses_lesson_and_document_both_carry_its_category() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "lesson", "tut", Some("Rust Course"), None, None, None);
    insert_set(&conn, "handout", "doc", Some("Rust Course"), None, None, None);
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('tutorials', 'title-rust-course', 'Programming', 1)",
        [],
    )
    .unwrap();
    drop(conn);

    let sets = list_sets(&core).unwrap();
    assert_eq!(sets.iter().find(|s| s.set_id == "lesson").unwrap().category.as_deref(), Some("Programming"));
    assert_eq!(sets.iter().find(|s| s.set_id == "handout").unwrap().category.as_deref(), Some("Programming"));
}

/// A documentary inside a collection keys by the collection's own name, not
/// its own episode title.
#[test]
fn a_documentary_in_a_filed_collection_carries_its_category() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "ep1", "docu", Some("Terra X"), Some("Volcanoes"), None, None);
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('documentaries', 'title-terra-x', 'Science', 1)",
        [],
    )
    .unwrap();
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.category.as_deref(), Some("Science"));
}

/// A standalone documentary has no show, so it keys by its own title.
#[test]
fn a_standalone_documentary_carries_its_own_category_by_title() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "solo", "docu", None, Some("Free Solo"), None, None);
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('documentaries', 'title-free-solo', 'Sport', 1)",
        [],
    )
    .unwrap();
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.category.as_deref(), Some("Sport"));
}

/// A film never carries a category, filed or not — only a course or a
/// documentary unit has one to carry.
#[test]
fn a_film_never_carries_a_category() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_set(&conn, "m1", "movie", None, Some("Dune"), None, Some(550));
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at) VALUES ('tutorials', 'title-dune', 'Trading', 1)",
        [],
    )
    .unwrap();
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.category, None);
}

/// A v11 index has no `categories` table: every title lists `category:
/// None`, never an error.
#[test]
fn a_v11_index_lists_every_category_as_none() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let current = dir.path().join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(11) {
        conn.execute(stmt, []).unwrap();
    }
    insert_set(&conn, "lesson", "tut", Some("Rust Course"), None, None, None);
    drop(conn);

    let set = list_sets(&core).unwrap().into_iter().next().unwrap();
    assert_eq!(set.category, None);
}
