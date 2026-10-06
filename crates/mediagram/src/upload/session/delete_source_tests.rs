use super::*;

#[test]
fn a_source_whose_set_is_complete_is_deleted() {
    let dir = tempfile::tempdir().unwrap();
    let source = dir.path().join("film.mkv");
    std::fs::write(&source, b"bytes").unwrap();

    report_deletion(&source, true, 5);

    assert!(!source.exists());
}

/// Some part never reached the channel, so the local copy may be the only
/// whole one: it stays.
#[test]
fn a_source_whose_set_is_incomplete_is_kept() {
    let dir = tempfile::tempdir().unwrap();
    let source = dir.path().join("film.mkv");
    std::fs::write(&source, b"bytes").unwrap();

    report_deletion(&source, false, 5);

    assert!(source.exists());
}

/// A source that cannot be removed is reported kept; the upload it
/// belongs to has already succeeded, so this is no reason to fail it.
#[test]
fn a_source_that_cannot_be_removed_is_kept_without_failing() {
    let dir = tempfile::tempdir().unwrap();
    let not_a_file = dir.path().join("film.mkv");
    std::fs::create_dir(&not_a_file).unwrap();

    report_deletion(&not_a_file, true, 5);
    report_deletion(&dir.path().join("already-gone.mkv"), true, 5);

    assert!(not_a_file.is_dir());
}
