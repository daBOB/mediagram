use std::fs;
use std::path::Path;
use std::time::{Duration, SystemTime};

use tempfile::tempdir;

use super::*;
use crate::rules::CHUNK;

fn open(root: &Path) -> ChunkStore {
    ChunkStore::open(root.to_path_buf(), 1 << 30).expect("open")
}

fn full_chunk() -> Vec<u8> {
    vec![0u8; CHUNK as usize]
}

#[test]
fn an_unknown_set_holds_nothing_and_has_no_total() {
    let dir = tempdir().unwrap();
    let status = open(dir.path()).set_status("set1").unwrap();
    assert_eq!(status.total, None);
    assert_eq!(status.chunks_held, 0);
    assert_eq!(status.bytes_held, 0);
}

#[test]
fn a_partly_held_set_reports_its_recorded_total_and_what_is_held_of_it() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    let total = CHUNK * 2 + 10;
    store.put("set1", 0, total, &full_chunk()).unwrap();
    store.put("set1", 2, total, &[0u8; 10]).unwrap();

    let status = store.set_status("set1").unwrap();
    assert_eq!(status.total, Some(total));
    assert_eq!(status.chunks_held, 2);
    assert_eq!(status.bytes_held, CHUNK + 10);
}

#[test]
fn a_completely_held_set_reports_every_byte_of_its_total() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    let total = CHUNK * 2;
    store.put("set1", 0, total, &full_chunk()).unwrap();
    store.put("set1", 1, total, &full_chunk()).unwrap();

    let status = store.set_status("set1").unwrap();
    assert_eq!(status.total, Some(total));
    assert_eq!(status.chunks_held, 2);
    assert_eq!(status.bytes_held, total);
}

#[test]
fn another_sets_chunks_are_never_counted() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    store.put("set1", 0, 5, b"hello").unwrap();
    store.put("set2", 0, CHUNK * 3, &full_chunk()).unwrap();
    store.put("set2", 1, CHUNK * 3, &full_chunk()).unwrap();

    let status = store.set_status("set1").unwrap();
    assert_eq!(status.total, Some(5));
    assert_eq!(status.chunks_held, 1);
    assert_eq!(status.bytes_held, 5);
}

/// What a previous run held is answered from the startup scan, so a device
/// polling across a server restart sees the same progress it saw before.
#[test]
fn a_reopened_store_reports_the_set_as_it_was_left() {
    let dir = tempdir().unwrap();
    let total = CHUNK * 3;
    open(dir.path())
        .put("set1", 0, total, &full_chunk())
        .unwrap();

    let status = open(dir.path()).set_status("set1").unwrap();
    assert_eq!(status.total, Some(total));
    assert_eq!(status.chunks_held, 1);
    assert_eq!(status.bytes_held, CHUNK);
}

/// Answering is a question, not a use: a chunk's mtime is what carries its
/// LRU position across a restart, so polling must leave it exactly as it
/// was — the in-memory order alone is not the whole promise.
#[test]
fn polling_a_set_leaves_its_chunks_mtimes_untouched() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    store.put("set1", 0, 5, b"hello").unwrap();
    let chunk = dir.path().join("set1").join("0");
    let long_ago = SystemTime::UNIX_EPOCH + Duration::from_secs(1_000_000_000);
    fs::File::options()
        .write(true)
        .open(&chunk)
        .unwrap()
        .set_modified(long_ago)
        .unwrap();

    for _ in 0..3 {
        store.set_status("set1").unwrap();
    }

    assert_eq!(fs::metadata(&chunk).unwrap().modified().unwrap(), long_ago);
}

/// A garbled `total` is "no recorded total", the same leniency a PUT gets —
/// it must not hide the chunks that are genuinely held.
#[test]
fn a_garbled_total_reads_as_none_while_held_chunks_still_count() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    store.put("set1", 0, 5, b"hello").unwrap();
    fs::write(dir.path().join("set1").join("total"), "garbage").unwrap();

    let status = store.set_status("set1").unwrap();
    assert_eq!(status.total, None);
    assert_eq!(status.chunks_held, 1);
    assert_eq!(status.bytes_held, 5);
}

/// A `total` that exists but cannot be read is a fault on the server, not
/// an answer about the set: it must surface as an error (a 500 over HTTP),
/// never be passed off as "nothing recorded".
#[test]
fn a_total_that_cannot_be_read_is_an_error() {
    let dir = tempdir().unwrap();
    let store = open(dir.path());
    fs::create_dir_all(dir.path().join("set1").join("total")).unwrap();

    assert!(store.set_status("set1").is_err());
}
