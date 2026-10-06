use super::*;
use crate::api::test_support::index_at;

const FILM: &str = "tmdb-movie-550";
const SHOW: &str = "tmdb-tv-1396";

fn insert_artwork(conn: &Connection, key: &str, bytes: &[u8]) {
    conn.execute(
        "INSERT INTO artwork(key, mime, bytes) VALUES (?1, 'image/jpeg', ?2)",
        rusqlite::params![key, bytes],
    )
    .unwrap();
}

fn write_poster(dir: &Path, key: &str) {
    std::fs::create_dir_all(dir).unwrap();
    std::fs::write(dir.join(format!("{key}.jpg")), b"fetched").unwrap();
}

fn names_in(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir)
        .unwrap()
        .map(|entry| entry.unwrap().file_name().into_string().unwrap())
        .collect();
    names.sort();
    names
}

/// A key only the index's own table holds is written out once, where a
/// fetch would have left it, and found there from then on without the table.
#[test]
fn a_poster_only_the_index_holds_is_written_beside_the_fetched_ones() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_artwork(&conn, FILM, &[1, 2, 3]);
    drop(conn);

    let path = poster_path(&core, FILM.into());

    let expected = artwork_dir(&core).join(format!("{FILM}.jpg"));
    assert_eq!(path, Some(expected.display().to_string()));
    assert_eq!(std::fs::read(&expected).unwrap(), [1, 2, 3]);
    assert_eq!(
        names_in(&artwork_dir(&core)),
        [format!("{FILM}.jpg")],
        "no temporary file left"
    );
    std::fs::remove_dir_all(current_dir(&core)).unwrap();
    assert_eq!(poster_path(&core, FILM.into()), path);
}

#[test]
fn a_key_nobody_holds_resolves_to_nothing_and_writes_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());

    let before_any_install = poster_path(&core, FILM.into());
    index_at(dir.path());
    let not_in_the_index = poster_path(&core, FILM.into());

    assert_eq!(before_any_install, None);
    assert_eq!(not_in_the_index, None);
    assert!(!artwork_dir(&core).exists());
}

/// A listing reads which keys the table holds once, up front; a key outside
/// that set is never asked for, even when the table would have had it.
#[test]
fn a_listing_only_materialises_keys_it_was_told_the_table_holds() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    insert_artwork(&conn, FILM, &[1]);
    let (version, artwork) = (current_dir(&core), artwork_dir(&core));
    let held = HashSet::from([FILM.to_string()]);
    crate::artwork::reset_get_calls();

    let unlisted = resolve_with(&version, &artwork, &conn, FILM, &HashSet::new());
    let queried_for_unlisted = crate::artwork::get_calls();
    let listed = resolve_with(&version, &artwork, &conn, FILM, &held);

    assert_eq!((unlisted, queried_for_unlisted), (None, 0));
    assert!(listed.is_some());
}

/// The key decides the file name, so a key that fails validation must not
/// reach a path at all — not even one where a file happens to sit.
#[test]
fn a_listing_never_resolves_an_invalid_key() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    let key = "not-a-valid-poster-key-42x";
    insert_artwork(&conn, key, &[1]);
    write_poster(&artwork_dir(&core), key);
    let held = HashSet::from([key.to_string()]);

    let resolved = resolve_with(&current_dir(&core), &artwork_dir(&core), &conn, key, &held);

    assert_eq!(resolved, None);
}

/// Many rows name the same show's poster, so each key is looked up once per
/// listing and that answer — found or not — stands for the rest of it.
#[test]
fn a_listing_looks_each_key_up_once() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let conn = index_at(dir.path());
    let (version, artwork) = (current_dir(&core), artwork_dir(&core));
    let held = HashSet::new();
    let mut cache = HashMap::new();
    write_poster(&artwork, FILM);
    let mut resolve = |key| resolve_cached(&mut cache, &version, &artwork, &conn, key, &held);

    let film = resolve(FILM);
    let show = resolve(SHOW);
    std::fs::remove_file(artwork_dir(&core).join(format!("{FILM}.jpg"))).unwrap();
    write_poster(&artwork_dir(&core), SHOW);

    assert!(film.is_some());
    assert_eq!(show, None);
    assert_eq!(resolve(FILM), film);
    assert_eq!(resolve(SHOW), None);
}
