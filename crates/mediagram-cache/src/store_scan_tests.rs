use tempfile::tempdir;

use super::*;

#[test]
fn a_startup_scan_drops_a_chunk_whose_length_does_not_fit_the_recorded_total() {
    let dir = tempdir().expect("temp dir");
    let set_dir = dir.path().join("set1");
    std::fs::create_dir_all(&set_dir).unwrap();
    let total = rules::CHUNK * 2;
    std::fs::write(set_dir.join("total"), total.to_string()).unwrap();
    // A full CHUNK: fits.
    std::fs::write(set_dir.join("0"), vec![0u8; rules::CHUNK as usize]).unwrap();
    // The final chunk should be exactly CHUNK too (total is a clean
    // multiple), but this one is one byte short.
    std::fs::write(set_dir.join("1"), vec![0u8; (rules::CHUNK - 1) as usize]).unwrap();

    let store = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).unwrap();

    assert_eq!(
        store.status().chunks,
        1,
        "only the well-sized chunk is indexed"
    );
    assert!(set_dir.join("0").exists());
    assert!(
        !set_dir.join("1").exists(),
        "the wrong-length chunk should be deleted, not just left unindexed"
    );
}

#[test]
fn a_startup_scan_skips_a_directory_whose_name_is_not_a_valid_set_id() {
    let dir = tempdir().expect("temp dir");
    // The kind of stray directory a filesystem itself can create.
    let bad_dir = dir.path().join("lost+found");
    std::fs::create_dir_all(&bad_dir).unwrap();
    std::fs::write(bad_dir.join("total"), "4").unwrap();
    std::fs::write(bad_dir.join("0"), b"junk").unwrap();

    let store = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).unwrap();

    assert_eq!(
        store.status().chunks,
        0,
        "an invalid-id directory is not scanned"
    );
    // Skipped, not treated as damage: nothing under it is touched.
    assert!(bad_dir.join("0").exists());
}
