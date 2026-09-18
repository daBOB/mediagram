//! The note the uploader leaves about the part it is on.
//!
//! The index records a part when it lands, and a 3.5 GiB part takes minutes
//! to land — so for most of a part's life the index says nothing has moved.
//! This is what fills that gap, and the whole of its contract is: it is a
//! hint, it can be stale, and a reader must cope with both.

use mediagram::upload::progress::{self, Progress, STALE_AFTER_SECONDS};

fn note(set_id: &str, updated_at: i64) -> Progress {
    Progress {
        set_id: set_id.into(),
        part: 1,
        parts: 2,
        bytes_sent: 500_000_000,
        part_bytes: 3_758_096_384,
        bytes_done: 3_758_096_384,
        set_bytes: 4_500_000_000,
        updated_at,
    }
}

#[test]
fn a_note_written_just_now_is_worth_reading() {
    assert!(note("A", 1000).is_fresh(1000));
    assert!(note("A", 1000).is_fresh(1000 + STALE_AFTER_SECONDS));
}

/// The process that wrote it can die without clearing it, so a number that
/// stopped moving says nothing about now.
#[test]
fn a_note_nobody_has_touched_is_not_worth_reading() {
    assert!(!note("A", 1000).is_fresh(1000 + STALE_AFTER_SECONDS + 1));
    assert!(!note("A", 1000).is_fresh(1_000_000));
}

/// A resumed upload reports against the whole set, not against what this run
/// happens to have done.
#[test]
fn bytes_sent_counts_the_parts_that_landed_before_this_run() {
    assert_eq!(note("A", 0).set_bytes_sent(), 3_758_096_384 + 500_000_000);
}

#[test]
fn a_round_trip_through_the_file_survives() {
    let dir = tempfile::tempdir().unwrap();
    let written = note("01ABC", 1700);
    std::fs::write(
        progress::path_in(dir.path()),
        serde_json::to_string(&written).unwrap(),
    )
    .unwrap();

    assert_eq!(progress::read(dir.path()), Some(written));

    progress::clear(dir.path());
    assert_eq!(progress::read(dir.path()), None);
}

/// A half-written file must not stop a reader reporting everything else it
/// knows, so it is treated as absent rather than as an error.
#[test]
fn a_file_that_cannot_be_understood_is_treated_as_absent() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::write(progress::path_in(dir.path()), "{not json").unwrap();

    assert_eq!(progress::read(dir.path()), None);
}

#[test]
fn no_file_at_all_is_no_answer_rather_than_a_failure() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(progress::read(dir.path()), None);
}
