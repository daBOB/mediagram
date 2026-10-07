use std::path::PathBuf;
use std::time::Duration;

use tempfile::tempdir;

use super::*;

const CHUNK: u64 = rules::CHUNK;

fn set(root: &Path, id: &str, total: Option<&str>) -> PathBuf {
    let dir = root.join(id);
    fs::create_dir_all(&dir).unwrap();
    if let Some(total) = total {
        fs::write(dir.join("total"), total).unwrap();
    }
    dir
}

fn chunk(dir: &Path, n: u32, len: u64) {
    fs::write(dir.join(n.to_string()), vec![0u8; len as usize]).unwrap();
}

/// The scan's result as `(id, n, len)`, sorted: the order it returns is
/// unspecified, so only the contents are compared.
fn found(root: &Path) -> Vec<(String, u32, u64)> {
    let mut found: Vec<_> = scan(root)
        .unwrap()
        .into_iter()
        .map(|((id, n), len, _)| (id, n, len))
        .collect();
    found.sort();
    found
}

#[test]
fn every_well_sized_chunk_is_found_with_its_set_and_length() {
    let root = tempdir().unwrap();
    let a = set(root.path(), "setA", Some(&(CHUNK + 5).to_string()));
    chunk(&a, 0, CHUNK);
    chunk(&a, 1, 5);
    let b = set(root.path(), "setB", Some("7"));
    chunk(&b, 0, 7);

    assert_eq!(
        found(root.path()),
        vec![
            ("setA".to_string(), 0, CHUNK),
            ("setA".to_string(), 1, 5),
            ("setB".to_string(), 0, 7),
        ]
    );
}

/// The LRU order has to survive a restart, and on disk it lives only in each
/// chunk's mtime, so the scan must hand back exactly what the file carries.
#[test]
fn a_chunks_mtime_is_reported_as_its_last_use() {
    let root = tempdir().unwrap();
    let dir = set(root.path(), "set1", Some("3"));
    chunk(&dir, 0, 3);
    let used = SystemTime::UNIX_EPOCH + Duration::from_secs(1_700_000_000);
    fs::File::options()
        .write(true)
        .open(dir.join("0"))
        .unwrap()
        .set_modified(used)
        .unwrap();

    let scanned = scan(root.path()).unwrap();
    assert_eq!(scanned.len(), 1);
    assert_eq!(scanned[0].2, used);
}

#[test]
fn an_empty_root_holds_nothing() {
    let root = tempdir().unwrap();
    assert!(scan(root.path()).unwrap().is_empty());
}

/// The one fatal case: without `root` there is no store to open.
#[test]
fn a_missing_root_is_an_error() {
    let root = tempdir().unwrap();
    let err = scan(&root.path().join("absent")).unwrap_err();
    assert_eq!(err.kind(), io::ErrorKind::NotFound);
}

/// `.tmp` holds half-written uploads, a loose file at the root is no set,
/// and a directory named outside the id grammar (a filesystem's own
/// `lost+found`, an over-long name) was never written by this store.
#[test]
fn staging_loose_files_and_misnamed_directories_are_skipped_untouched() {
    let root = tempdir().unwrap();
    let tmp = set(root.path(), ".tmp", None);
    chunk(&tmp, 0, 4);
    fs::write(root.path().join("12"), b"loose").unwrap();
    let lost = set(root.path(), "lost+found", Some("4"));
    chunk(&lost, 0, 4);
    let long = set(root.path(), &"a".repeat(65), Some("4"));
    chunk(&long, 0, 4);
    let good = set(root.path(), "good", Some("4"));
    chunk(&good, 0, 4);

    assert_eq!(found(root.path()), vec![("good".to_string(), 0, 4)]);
    assert!(tmp.join("0").exists());
    assert!(lost.join("0").exists(), "skipped, not treated as damage");
    assert!(long.join("0").exists());
}

#[test]
fn the_total_marker_and_unnumbered_files_are_not_chunks() {
    let root = tempdir().unwrap();
    let dir = set(root.path(), "set1", Some("4"));
    chunk(&dir, 0, 4);
    fs::write(dir.join("0.part"), b"half").unwrap();
    fs::write(dir.join("-1"), b"neg").unwrap();
    fs::write(dir.join("notes"), b"text").unwrap();

    assert_eq!(found(root.path()), vec![("set1".to_string(), 0, 4)]);
    assert!(dir.join("notes").exists(), "ignored, not deleted");
}

/// `01` and `+1` parse to 1; indexed as chunk 1 beside the real one, the
/// held byte count would stay too high for good and the stray file could
/// never be read or evicted.
#[test]
fn a_hand_placed_file_spelling_a_chunk_number_differently_is_not_a_chunk() {
    let root = tempdir().unwrap();
    let dir = set(root.path(), "set1", None);
    chunk(&dir, 1, CHUNK);
    fs::write(dir.join("01"), b"stray").unwrap();
    fs::write(dir.join("+1"), b"stray").unwrap();

    assert_eq!(found(root.path()), vec![("set1".to_string(), 1, CHUNK)]);
}

/// A chunk whose length cannot be part of its set's recorded total would
/// be served as if it were good data, so it is deleted, not just ignored.
#[test]
fn a_chunk_that_does_not_fit_its_recorded_total_is_dropped_and_deleted() {
    let root = tempdir().unwrap();
    let dir = set(root.path(), "set1", Some(&(CHUNK * 2).to_string()));
    chunk(&dir, 0, CHUNK);
    chunk(&dir, 1, CHUNK - 1);

    assert_eq!(found(root.path()), vec![("set1".to_string(), 0, CHUNK)]);
    assert!(dir.join("0").exists());
    assert!(!dir.join("1").exists());
}

#[test]
fn a_chunk_numbered_past_the_end_of_its_set_is_dropped_and_deleted() {
    let root = tempdir().unwrap();
    let dir = set(root.path(), "set1", Some("5"));
    chunk(&dir, 0, 5);
    chunk(&dir, 1, 5);

    assert_eq!(found(root.path()), vec![("set1".to_string(), 0, 5)]);
    assert!(!dir.join("1").exists());
}

/// With no readable total there is nothing to check a length against, so
/// every chunk is trusted as found — and, above all, none is deleted.
#[test]
fn a_set_without_a_readable_total_keeps_every_chunk_unchecked() {
    let root = tempdir().unwrap();
    let missing = set(root.path(), "missing", None);
    chunk(&missing, 0, 3);
    chunk(&missing, 5, 9);
    let garbled = set(root.path(), "garbled", Some("not a number"));
    chunk(&garbled, 0, 3);

    assert_eq!(
        found(root.path()),
        vec![
            ("garbled".to_string(), 0, 3),
            ("missing".to_string(), 0, 3),
            ("missing".to_string(), 5, 9),
        ]
    );
    assert!(garbled.join("0").exists());
}

/// One set the process cannot read must not cost every other set its
/// index entries — or the store its startup.
#[cfg(unix)]
#[test]
fn an_unreadable_set_directory_skips_only_that_set() {
    use std::os::unix::fs::PermissionsExt;

    let root = tempdir().unwrap();
    let locked = set(root.path(), "locked", Some("4"));
    chunk(&locked, 0, 4);
    let open = set(root.path(), "open", Some("4"));
    chunk(&open, 0, 4);
    fs::set_permissions(&locked, fs::Permissions::from_mode(0o000)).unwrap();
    let still_readable = fs::read_dir(&locked).is_ok();

    let result = found(root.path());
    fs::set_permissions(&locked, fs::Permissions::from_mode(0o755)).unwrap();

    if still_readable {
        // Running with permission checks bypassed (root): the case cannot
        // be produced here, and the scan rightly finds both sets.
        assert_eq!(result.len(), 2);
        return;
    }
    assert_eq!(result, vec![("open".to_string(), 0, 4)]);
}
