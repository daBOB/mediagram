use super::*;
use crate::index::db;

fn open() -> (tempfile::TempDir, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

#[test]
fn put_get_and_clear_round_trip() {
    let (_dir, conn) = open();
    put(&conn, "title-terra-x", "image/jpeg", b"bytes").unwrap();
    let (mime, bytes) = get(&conn, "title-terra-x").unwrap().unwrap();
    assert_eq!(mime, "image/jpeg");
    assert_eq!(bytes, b"bytes");

    // Re-storing under the same key replaces rather than duplicates.
    put(&conn, "title-terra-x", "image/png", b"other").unwrap();
    let (mime, bytes) = get(&conn, "title-terra-x").unwrap().unwrap();
    assert_eq!(mime, "image/png");
    assert_eq!(bytes, b"other");

    assert!(clear(&conn, "title-terra-x").unwrap());
    assert!(get(&conn, "title-terra-x").unwrap().is_none());
    assert!(!clear(&conn, "title-terra-x").unwrap(), "nothing left to clear");
}

#[test]
fn an_invalid_key_is_refused() {
    let (_dir, conn) = open();
    let err = put(&conn, "not a key", "image/jpeg", b"x").unwrap_err();
    assert!(err.to_string().contains("not a valid artwork key"));
}

#[test]
fn an_image_over_the_cap_is_refused() {
    let (_dir, conn) = open();
    let big = vec![0u8; MAX_ARTWORK_BYTES + 1];
    let err = put(&conn, "title-terra-x", "image/jpeg", &big).unwrap_err();
    assert!(err.to_string().contains("resize the image"));
}

#[test]
fn put_file_guesses_mime_from_extension() {
    let (_dir, conn) = open();
    let src = tempfile::tempdir().unwrap();
    let path = src.path().join("cover.png");
    std::fs::write(&path, b"png bytes").unwrap();
    put_file(&conn, "title-terra-x", &path).unwrap();
    let (mime, bytes) = get(&conn, "title-terra-x").unwrap().unwrap();
    assert_eq!(mime, "image/png");
    assert_eq!(bytes, b"png bytes");
}

#[test]
fn an_unrecognised_extension_is_refused() {
    let (_dir, conn) = open();
    let src = tempfile::tempdir().unwrap();
    let path = src.path().join("cover.gif");
    std::fs::write(&path, b"x").unwrap();
    let err = put_file(&conn, "title-terra-x", &path).unwrap_err();
    assert!(err.to_string().contains("unrecognised image extension"));
}

#[test]
fn adopt_folder_picks_up_poster_and_backdrop_and_ignores_a_folder_with_neither() {
    let (_dir, conn) = open();
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("poster.jpg"), b"poster").unwrap();
    std::fs::write(src.path().join("backdrop.webp"), b"backdrop").unwrap();
    let stored = adopt_folder(&conn, src.path(), "title-terra-x").unwrap();
    assert_eq!(stored, 2);
    assert!(get(&conn, "title-terra-x").unwrap().is_some());
    assert!(get(&conn, "title-terra-x-bg").unwrap().is_some());

    let empty = tempfile::tempdir().unwrap();
    assert_eq!(adopt_folder(&conn, empty.path(), "title-other").unwrap(), 0);
}

#[test]
fn adopt_folder_matches_the_file_name_case_insensitively() {
    let (_dir, conn) = open();
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("Poster.JPG"), b"poster").unwrap();
    let stored = adopt_folder(&conn, src.path(), "title-terra-x").unwrap();
    assert_eq!(stored, 1);
    assert!(get(&conn, "title-terra-x").unwrap().is_some());
}
