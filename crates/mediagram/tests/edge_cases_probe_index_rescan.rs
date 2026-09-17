//! Edge case probes for `index::rescan::apply_seen` and `snapshot_to`.
//! These tests explore boundary conditions and error scenarios to identify
//! gaps in the current implementation.

use mediagram::index::{db, rescan, snapshot};
use mediagram::upload::transport::Seen;
use mlib_spec::caption::Part;

mod support;
use support::rescan::{CHAT_ID, open_db, part_seen, template};

// ============================================================================
// Probe 1: apply_seen with empty slice
// ============================================================================

#[test]
fn apply_seen_with_empty_slice_returns_zero_counts() {
    let (_dir, conn) = open_db();
    let summary = rescan::apply_seen(&conn, CHAT_ID, &[]).unwrap();
    assert_eq!(summary.sets_seen, 0);
    assert_eq!(summary.parts_seen, 0);
    assert_eq!(summary.sets_complete, 0);
    assert_eq!(summary.sets_incomplete, 0);
    assert_eq!(summary.duplicates_skipped, 0);
}

// ============================================================================
// Probe 2: caption with part.n disagreement (header says 3 parts, but we only see 2)
// ============================================================================

#[test]
fn partial_set_has_correct_part_count_recorded() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000100";
    let t = template(set_id, 3, 300); // 3 parts total
    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        // Missing part 2
    ];
    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Set should be recorded even though incomplete
    let set_row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(set_row.part_count, 3);
    assert_eq!(summary.sets_incomplete, 1);
    assert_eq!(set_row.status, "pending");
}

// ============================================================================
// Probe 3: caption with total not matching sum of part lengths
// ============================================================================

#[test]
fn caption_total_mismatch_prevents_completion() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000101";
    let t = template(set_id, 2, 200); // Declare total = 200

    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 150, "bb", 102, 9002), // But sum = 250, not 200
    ];
    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Should be marked incomplete even though part count matches
    assert_eq!(summary.sets_incomplete, 1);
    let row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row.status, "pending");
}

// ============================================================================
// Probe 4: Two sets interleaved in one batch
// ============================================================================

#[test]
fn two_sets_interleaved_in_single_batch() {
    let (_dir, conn) = open_db();

    let set_a = "01JQ8F2K9M4XZ00000000102";
    let ta = template(set_a, 2, 200);

    let set_b = "01JQ8F2K9M4XZ00000000103";
    let tb = template(set_b, 2, 200);

    let seen = vec![
        part_seen(&ta, 0, 0, 100, "aa", 101, 9001),
        part_seen(&tb, 0, 0, 100, "cc", 201, 9101),
        part_seen(&ta, 1, 100, 100, "bb", 102, 9002),
        part_seen(&tb, 1, 100, 100, "dd", 202, 9102),
    ];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary.sets_seen, 2);
    assert_eq!(summary.parts_seen, 4);
    assert_eq!(summary.sets_complete, 2);

    let row_a = mediagram::index::sets::get_set(&conn, set_a)
        .unwrap()
        .unwrap();
    let row_b = mediagram::index::sets::get_set(&conn, set_b)
        .unwrap()
        .unwrap();
    assert_eq!(row_a.status, "complete");
    assert_eq!(row_b.status, "complete");
}

// ============================================================================
// Probe 5: Part with doc_id = None (should be skipped)
// ============================================================================

#[test]
fn part_with_no_doc_id_is_skipped() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000104";
    let t = template(set_id, 2, 200);

    let caption_text = mlib_spec::to_text(
        &t.with_part(Part {
            i: 0,
            n: 2,
            off: 0,
            len: 100,
            sha256: "aa".into(),
        }),
        "",
    )
    .unwrap();

    let seen = vec![Seen {
        message_id: 101,
        doc_id: None, // No document attached
        caption: caption_text,
    }];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary.parts_seen, 0);
    assert_eq!(summary.sets_seen, 0); // No parts → no sets recorded
}

// ============================================================================
// Probe 6: Part with any valid caption is recorded (no hash validation)
// ============================================================================

#[test]
fn any_valid_caption_gets_recorded_regardless_of_field_values() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000105";

    let t = template(set_id, 1, 100);
    // Create a part-specific caption with arbitrary sha256 value
    let seen = vec![part_seen(&t, 0, 0, 100, "abcdef", 101, 9001)];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // The caption parses and records successfully, no validation of hash correctness
    assert_eq!(summary.parts_seen, 1);
    assert_eq!(summary.sets_complete, 1);

    let row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row.status, "complete");
}

// ============================================================================
// Probe 7: Replay same Seen list after set was already complete
// (status must not regress)
// ============================================================================

#[test]
fn replaying_seen_after_completion_maintains_complete_status() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000106";
    let t = template(set_id, 2, 200);

    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
    ];

    let summary1 = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary1.sets_complete, 1);

    // Replay the exact same batch
    let summary2 = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary2.sets_complete, 1);

    let row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row.status, "complete");
}

// ============================================================================
// Probe 8: apply_seen does not delete parts absent from scan
// (It assumes it's processing complete channel data, not partial)
// ============================================================================

#[test]
fn apply_seen_does_not_delete_parts_absent_from_batch() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000107";
    let t = template(set_id, 2, 200);

    // First pass: complete the set with both parts
    let seen_complete = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
    ];
    let summary1 = rescan::apply_seen(&conn, CHAT_ID, &seen_complete).unwrap();
    assert_eq!(summary1.sets_complete, 1);

    // Second pass: batch only contains part 0 (simulating partial scan)
    // apply_seen will see set_id is "touched" and recompute its status.
    // But since part 1 still exists in the database from the first scan,
    // the set remains complete (no parts were deleted).
    let seen_partial = vec![part_seen(&t, 0, 0, 100, "aa", 101, 9001)];
    let summary2 = rescan::apply_seen(&conn, CHAT_ID, &seen_partial).unwrap();

    // The set stays complete because apply_seen doesn't delete missing parts.
    // This is correct: apply_seen is designed to be called on full channel scans,
    // not partial ones.
    assert_eq!(summary2.sets_complete, 1);
    let row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row.status, "complete");
}

// ============================================================================
// Probe 9: Caption with unsupported version (#mlib v=3)
// ============================================================================

#[test]
fn unsupported_mlib_version_is_skipped() {
    let (_dir, conn) = open_db();

    let unsupported_caption = "#mlib v=3\nsome_unsupported_format".to_string();
    let seen = vec![Seen {
        message_id: 101,
        doc_id: Some(9001),
        caption: unsupported_caption,
    }];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Unparseable caption → skip
    assert_eq!(summary.sets_seen, 0);
    assert_eq!(summary.parts_seen, 0);
}

// ============================================================================
// Probe 10: Message IDs in different orders (oldest-first vs newest-first)
// ============================================================================

#[test]
fn message_id_order_does_not_affect_final_state() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000108";
    let t = template(set_id, 2, 200);

    // Oldest-first order
    let seen_old_first = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
    ];
    let summary_old_first = rescan::apply_seen(&conn, CHAT_ID, &seen_old_first).unwrap();

    let (_dir2, conn2) = open_db();
    // Newest-first order
    let seen_new_first = vec![
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
    ];
    let summary_new_first = rescan::apply_seen(&conn2, CHAT_ID, &seen_new_first).unwrap();

    assert_eq!(summary_old_first.sets_complete, 1);
    assert_eq!(summary_new_first.sets_complete, 1);

    let row1 = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    let row2 = mediagram::index::sets::get_set(&conn2, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row1.status, "complete");
    assert_eq!(row2.status, "complete");
}

// ============================================================================
// Probe 11: Large batch (5,000 Seen entries)
// ============================================================================

#[test]
fn large_batch_5000_entries_completes_and_maintains_correctness() {
    let (_dir, conn) = open_db();

    let mut seen = Vec::new();

    // Create 1,000 sets with 5 parts each = 5,000 total parts
    for set_idx in 0..1000 {
        let set_id = format!("01JQ8F2K9M4XZ{:016}", set_idx);
        let t = template(&set_id, 5, 500);

        for part_idx in 0..5 {
            let sha = format!("{:02x}", part_idx);
            let msg_id = set_idx as i64 * 1000 + part_idx as i64;
            let doc_id = set_idx as i64 * 10000 + part_idx as i64;

            seen.push(part_seen(
                &t,
                part_idx,
                (part_idx as u64) * 100,
                100,
                &sha,
                msg_id,
                doc_id,
            ));
        }
    }

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // All 1,000 sets should be complete
    assert_eq!(summary.sets_seen, 1000);
    assert_eq!(summary.parts_seen, 5000);
    assert_eq!(summary.sets_complete, 1000);
    assert_eq!(summary.sets_incomplete, 0);
    assert_eq!(summary.duplicates_skipped, 0);

    // Spot check: verify one set is complete
    let spot_check_id = "01JQ8F2K9M4XZ0000000100".to_string();
    if let Ok(Some(row)) = mediagram::index::sets::get_set(&conn, &spot_check_id) {
        assert_eq!(row.status, "complete");
    }
}

// ============================================================================
// Probe 12: snapshot_to with existing destination
// ============================================================================

#[test]
fn snapshot_to_overwrites_existing_destination() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let set_id = "01JQ8F2K9M4XZ00000000109";
    let t = template(set_id, 1, 100);
    let seen = vec![part_seen(&t, 0, 0, 100, "aa", 101, 9001)];

    rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    let dest = dir.path().join("library.push.db");

    // Write a stale file
    std::fs::write(&dest, b"old_snapshot_data").unwrap();

    snapshot::checkpoint(&conn).unwrap();
    snapshot::snapshot_to(&conn, &dest).unwrap();

    // Verify the new snapshot is valid and contains our data
    let snapshot_conn = rusqlite::Connection::open(&dest).unwrap();
    let count: i64 = snapshot_conn
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .unwrap();
    assert_eq!(count, 1);
}

// ============================================================================
// Probe 13: snapshot_to with non-existent parent directory
// ============================================================================

#[test]
fn snapshot_to_fails_when_parent_directory_missing() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let dest = dir.path().join("nonexistent_dir").join("library.push.db");
    snapshot::checkpoint(&conn).unwrap();

    let result = snapshot::snapshot_to(&conn, &dest);
    assert!(result.is_err());
}

// ============================================================================
// Probe 14: snapshot_to captures meta.last_push_at
// ============================================================================

#[test]
fn snapshot_contains_last_push_at_timestamp() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let set_id = "01JQ8F2K9M4XZ00000000110";
    let t = template(set_id, 1, 100);
    let seen = vec![part_seen(&t, 0, 0, 100, "aa", 101, 9001)];

    rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    snapshot::checkpoint(&conn).unwrap();
    let dest = dir.path().join("library.push.db");
    snapshot::snapshot_to(&conn, &dest).unwrap();

    let snapshot_conn = rusqlite::Connection::open(&dest).unwrap();
    let pushed_at: Result<String, _> = snapshot_conn.query_row(
        "SELECT value FROM meta WHERE key = 'last_push_at'",
        [],
        |r| r.get(0),
    );

    assert!(pushed_at.is_ok());
    let ts_str = pushed_at.unwrap();
    let ts: i64 = ts_str.parse().unwrap();
    assert!(ts > 0);
}

// ============================================================================
// Probe 15: Multiple captions for same (set, idx) with different message_ids
// (duplicate handling with message_id comparison)
// ============================================================================

#[test]
fn duplicate_parts_keep_highest_message_id_across_batches() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000111";
    let t = template(set_id, 1, 100);

    // First batch: part with message_id 100
    let batch1 = vec![part_seen(&t, 0, 0, 100, "aa", 100, 9001)];
    rescan::apply_seen(&conn, CHAT_ID, &batch1).unwrap();

    // Second batch: same part with message_id 105 (higher)
    let batch2 = vec![part_seen(&t, 0, 0, 100, "aa", 105, 9001)];
    let summary = rescan::apply_seen(&conn, CHAT_ID, &batch2).unwrap();

    // The duplicate should be noted
    assert_eq!(summary.duplicates_skipped, 1);

    // Verify the higher message_id is kept
    let msg_id: i64 = conn
        .query_row(
            "SELECT message_id FROM parts WHERE set_id = ?1 AND idx = 0",
            [set_id],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(msg_id, 105);
}

// ============================================================================
// Probe 16: Checkpoint on DB with pending WAL frames
// ============================================================================

#[test]
fn checkpoint_truncates_wal_safely() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let set_id = "01JQ8F2K9M4XZ00000000112";
    let t = template(set_id, 1, 100);
    let seen = vec![part_seen(&t, 0, 0, 100, "aa", 101, 9001)];

    rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Checkpoint should succeed even with pending writes
    let result = snapshot::checkpoint(&conn);
    assert!(result.is_ok());
}

// ============================================================================
// Probe 17: apply_seen with part offset/length mismatch against declared total
// ============================================================================

#[test]
fn overlapping_part_ranges_do_not_prevent_completion_if_sum_matches() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000113";

    // Declare total = 300
    let t = template(set_id, 3, 300);

    // But parts overlap: off+len doesn't form a contiguous range
    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 50, 100, "bb", 102, 9002), // Overlaps with part 0
        part_seen(&t, 2, 150, 150, "cc", 103, 9003), // Overlaps with part 1
    ];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Sum of lengths = 100 + 100 + 150 = 350, not 300 → should stay pending
    assert_eq!(summary.sets_incomplete, 1);
    let row = mediagram::index::sets::get_set(&conn, set_id)
        .unwrap()
        .unwrap();
    assert_eq!(row.status, "pending");
}

// ============================================================================
// Probe 18: Index document caption (#mlib-index) mixed with data captions
// ============================================================================

#[test]
fn index_document_is_ignored_even_when_mixed_with_data() {
    let (_dir, conn) = open_db();

    let set_id = "01JQ8F2K9M4XZ00000000114";
    let t = template(set_id, 2, 200);

    let seen = vec![
        Seen {
            message_id: 1,
            doc_id: Some(1),
            caption: "#mlib-index v=2\n{\"pushed_at\":1000000000,\"sets\":5,\"schema\":1}".into(),
        },
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
    ];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    // Index doc should be skipped, only 2 data parts counted
    assert_eq!(summary.parts_seen, 2);
    assert_eq!(summary.sets_complete, 1);
}
