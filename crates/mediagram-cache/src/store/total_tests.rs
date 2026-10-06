use std::fs;
use std::io;
use std::sync::{Arc, Barrier};

use tempfile::tempdir;

use super::*;

/// A store-shaped layout: the staging directory beside the set directory the
/// `total` file is published into.
fn layout() -> (tempfile::TempDir, std::path::PathBuf, std::path::PathBuf) {
    let dir = tempdir().expect("temp dir");
    let tmp = dir.path().join(".tmp");
    fs::create_dir_all(&tmp).unwrap();
    fs::create_dir_all(dir.path().join("set1")).unwrap();
    let total = dir.path().join("set1").join("total");
    (dir, tmp, total)
}

fn staged_files(tmp: &Path) -> usize {
    fs::read_dir(tmp).unwrap().count()
}

#[test]
fn the_first_pairing_records_the_total_and_reports_itself_the_writer() {
    let (_dir, tmp, path) = layout();

    assert_eq!(pair(&tmp, &path, 4096).unwrap(), None);
    assert_eq!(fs::read_to_string(&path).unwrap(), "4096");
    assert_eq!(staged_files(&tmp), 0, "the staged copy is cleaned up");
}

#[test]
fn a_later_pairing_reads_back_the_recorded_total_and_never_overwrites_it() {
    let (_dir, tmp, path) = layout();
    pair(&tmp, &path, 4096).unwrap();

    assert_eq!(pair(&tmp, &path, 4096).unwrap(), Some(4096));
    assert_eq!(
        pair(&tmp, &path, 9999).unwrap(),
        Some(4096),
        "a disagreeing total is reported against, not recorded"
    );
    assert_eq!(fs::read_to_string(&path).unwrap(), "4096");
}

/// Publishing by `hard_link` means a racing reader sees either no file or
/// the whole of the winner's content — so of two first writers with
/// different totals, exactly one is the writer and the other reads back
/// precisely what that one wrote, never an empty or torn value.
#[test]
fn of_two_racing_first_writers_exactly_one_records_and_the_other_reads_it_back() {
    for _ in 0..50 {
        let (_dir, tmp, path) = layout();
        let barrier = Arc::new(Barrier::new(2));
        let racers: Vec<_> = [111u64, 222]
            .into_iter()
            .map(|total| {
                let (tmp, path, barrier) = (tmp.clone(), path.clone(), barrier.clone());
                std::thread::spawn(move || {
                    barrier.wait();
                    (total, pair(&tmp, &path, total).unwrap())
                })
            })
            .collect();
        let results: Vec<_> = racers.into_iter().map(|t| t.join().unwrap()).collect();

        let winners: Vec<u64> = results
            .iter()
            .filter(|(_, held)| held.is_none())
            .map(|(total, _)| *total)
            .collect();
        assert_eq!(winners.len(), 1, "exactly one writer: {results:?}");
        let recorded = winners[0];
        for (total, held) in &results {
            if *total != recorded {
                assert_eq!(*held, Some(recorded), "the loser reads the winner's total");
            }
        }
        assert_eq!(fs::read_to_string(&path).unwrap(), recorded.to_string());
        assert_eq!(staged_files(&tmp), 0);
    }
}

/// An empty `total` file can never be repaired by this call — it cannot be
/// replaced without breaking first-write-wins — so after a brief retry it
/// must surface as an error rather than loop forever or be read as "none".
#[test]
fn an_empty_total_left_on_disk_errors_once_the_retry_budget_is_spent() {
    let (_dir, tmp, path) = layout();
    fs::write(&path, "").unwrap();

    let err = pair(&tmp, &path, 4096).unwrap_err();
    assert_eq!(err.kind(), io::ErrorKind::InvalidData);
    assert_eq!(
        fs::read_to_string(&path).unwrap(),
        "",
        "left as it was found"
    );
    assert_eq!(staged_files(&tmp), 0, "the staged copy is still cleaned up");
}

#[test]
fn reading_a_missing_total_is_none_not_an_error() {
    let (_dir, _tmp, path) = layout();
    assert_eq!(read_valid(&path).unwrap(), None);
}

#[test]
fn reading_a_total_tolerates_surrounding_whitespace() {
    let (_dir, _tmp, path) = layout();
    fs::write(&path, " 1048576\n").unwrap();
    assert_eq!(read_valid(&path).unwrap(), Some(1_048_576));
}

/// Empty or unparseable content is the ambiguous case the caller's retry
/// loop decides about, so a single read reports it as "no valid total yet".
#[test]
fn reading_empty_or_unparseable_content_is_none_not_an_error() {
    let (_dir, _tmp, path) = layout();
    for content in ["", "\n", "12ab", "-5", "18446744073709551616"] {
        fs::write(&path, content).unwrap();
        assert_eq!(read_valid(&path).unwrap(), None, "content {content:?}");
    }
}

/// Only a missing file is "not there yet"; anything else that stops the
/// read — here a directory squatting on the name — is a real error.
#[test]
fn a_total_that_exists_but_cannot_be_read_is_an_error() {
    let (_dir, _tmp, path) = layout();
    fs::create_dir(&path).unwrap();
    let err = read_valid(&path).unwrap_err();
    assert_ne!(err.kind(), io::ErrorKind::NotFound);
}
