use std::time::{Duration, SystemTime};

use tempfile::tempdir;

use super::*;

/// A second, in whole seconds since the epoch: an explicit LRU position.
fn at(secs: u64) -> SystemTime {
    SystemTime::UNIX_EPOCH + Duration::from_secs(secs)
}

/// Writes `id/n` with `size` bytes and a `total` marker beside it, and
/// records it in `index` as last used at `used`.
fn hold(root: &Path, index: &mut Index, id: &str, n: u32, size: u64, used: SystemTime) {
    fs::create_dir_all(set_dir(root, id)).unwrap();
    fs::write(set_dir(root, id).join("total"), "999").unwrap();
    fs::write(chunk_path(root, id, n), vec![0u8; size as usize]).unwrap();
    index.insert((id.to_string(), n), size, used);
}

#[test]
fn a_store_within_budget_deletes_nothing() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "set1", 0, 10, at(1));
    hold(dir.path(), &mut index, "set1", 1, 10, at(2));

    evict_over_budget(dir.path(), 20, &mut index);

    assert_eq!(index.chunk_count(), 2);
    assert!(chunk_path(dir.path(), "set1", 0).exists());
    assert!(chunk_path(dir.path(), "set1", 1).exists());
}

#[test]
fn an_over_budget_store_deletes_the_least_recently_used_chunk_file() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "set1", 1, 10, at(2));
    hold(dir.path(), &mut index, "set1", 0, 10, at(1));
    hold(dir.path(), &mut index, "set1", 2, 10, at(3));

    evict_over_budget(dir.path(), 20, &mut index);

    assert!(
        !chunk_path(dir.path(), "set1", 0).exists(),
        "the oldest use goes first, not the first inserted"
    );
    assert!(chunk_path(dir.path(), "set1", 1).exists());
    assert!(chunk_path(dir.path(), "set1", 2).exists());
    assert_eq!(index.held_bytes(), 20);
    assert!(
        set_dir(dir.path(), "set1").join("total").exists(),
        "a set that still holds a chunk keeps its total"
    );
}

/// A stale `total` outliving every chunk of its set would 409 the next PUT
/// of that set with a different total, for a set this store holds nothing of.
#[test]
fn evicting_a_sets_last_chunk_removes_its_total_and_directory() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "old", 0, 10, at(1));
    hold(dir.path(), &mut index, "new", 0, 10, at(2));

    evict_over_budget(dir.path(), 10, &mut index);

    assert!(!set_dir(dir.path(), "old").exists());
    assert!(set_dir(dir.path(), "new").join("total").exists());
    assert!(chunk_path(dir.path(), "new", 0).exists());
}

#[test]
fn one_pass_can_empty_several_sets_at_once() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "a", 0, 10, at(1));
    hold(dir.path(), &mut index, "b", 0, 10, at(2));
    hold(dir.path(), &mut index, "a", 1, 10, at(3));
    hold(dir.path(), &mut index, "c", 0, 10, at(4));

    evict_over_budget(dir.path(), 10, &mut index);

    assert!(!set_dir(dir.path(), "a").exists());
    assert!(!set_dir(dir.path(), "b").exists());
    assert!(chunk_path(dir.path(), "c", 0).exists());
    assert_eq!(index.chunk_count(), 1);
}

/// Whether a set is now empty is read off the disk, not the index: a chunk
/// file the index has no entry for is still a chunk, and its set's `total`
/// must survive alongside it.
#[test]
fn a_chunk_file_the_index_does_not_know_keeps_its_set_directory() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "set1", 0, 10, at(1));
    fs::write(chunk_path(dir.path(), "set1", 7), b"unindexed").unwrap();

    evict_over_budget(dir.path(), 0, &mut index);

    assert!(!chunk_path(dir.path(), "set1", 0).exists());
    assert!(chunk_path(dir.path(), "set1", 7).exists());
    assert!(set_dir(dir.path(), "set1").join("total").exists());
}

/// Only numbered files count as chunks: a directory left holding nothing
/// but `total` and an unrelated name is empty of chunks and goes.
#[test]
fn files_that_are_not_numbered_chunks_do_not_keep_a_set_alive() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "set1", 0, 10, at(1));
    fs::write(set_dir(dir.path(), "set1").join("notes"), b"x").unwrap();

    evict_over_budget(dir.path(), 0, &mut index);

    assert!(!set_dir(dir.path(), "set1").exists());
}

/// A chunk already deleted by hand is still evicted cleanly: the index
/// entry goes, and the set's leftover `total` is cleared with it.
#[test]
fn a_chunk_file_already_gone_from_disk_is_evicted_without_fuss() {
    let dir = tempdir().unwrap();
    let mut index = Index::from_scan(Vec::new());
    hold(dir.path(), &mut index, "set1", 0, 10, at(1));
    fs::remove_file(chunk_path(dir.path(), "set1", 0)).unwrap();

    evict_over_budget(dir.path(), 0, &mut index);

    assert_eq!(index.chunk_count(), 0);
    assert_eq!(index.held_bytes(), 0);
    assert!(!set_dir(dir.path(), "set1").exists());
}
