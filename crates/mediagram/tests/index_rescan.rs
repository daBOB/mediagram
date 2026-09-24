//! `index::rescan::apply_seen` against fixture caption lists and a real
//! sqlite file; no Telegram connection. `snapshot_to`'s happy path is
//! covered in `tests/index_snapshot.rs`; its missing-parent-directory error
//! path is pinned here instead, reusing this file's `apply_seen` fixtures.

use mediagram::index::rescan::Seen;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, rescan, sets, snapshot};
use mlib_spec::caption::Part;

mod support;
use support::rescan::{CHAT_ID, open_db, part_seen, template};

mod basics {
    use super::*;

    /// An empty scan produces an all-zero summary rather than erroring.
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
}

mod completion {
    use super::*;

    #[test]
    fn complete_set_becomes_complete_with_correct_set_hash() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000001";
        let t = template(set_id, 3, 300);
        let seen = vec![
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
            part_seen(&t, 2, 200, 100, "cc", 103, 9003),
        ];
        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        assert_eq!(summary.sets_seen, 1);
        assert_eq!(summary.parts_seen, 3);
        assert_eq!(summary.sets_complete, 1);
        assert_eq!(summary.sets_incomplete, 0);
        assert_eq!(summary.duplicates_skipped, 0);
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Complete);
        let expected = mlib_spec::set_hash::set_hash(&["aa", "bb", "cc"]);
        assert_eq!(row.set_hash.as_deref(), Some(expected.as_str()));
    }

    /// A set missing a part stays pending, but the part count from the
    /// caption header is still recorded against it.
    #[test]
    fn incomplete_set_stays_pending() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000002";
        let t = template(set_id, 3, 300);
        let seen = vec![
            part_seen(&t, 0, 0, 100, "aa", 201, 9101),
            part_seen(&t, 1, 100, 100, "bb", 202, 9102),
        ];
        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        assert_eq!(summary.sets_complete, 0);
        assert_eq!(summary.sets_incomplete, 1);
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Pending);
        assert_eq!(row.part_count, 3);
        assert!(row.set_hash.is_none());
    }

    /// A set with every declared part present still stays pending when the
    /// parts' byte lengths don't sum to the caption's declared total.
    #[test]
    fn byte_length_sum_mismatch_keeps_set_pending_despite_full_part_count() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000101";
        let t = template(set_id, 2, 200);

        let seen = vec![
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
            part_seen(&t, 1, 100, 150, "bb", 102, 9002),
        ];
        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

        assert_eq!(summary.sets_incomplete, 1);
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Pending);
    }
}

mod duplicates {
    use super::*;

    fn recorded_message_id(conn: &rusqlite::Connection, set_id: &str) -> i64 {
        conn.query_row(
            "SELECT message_id FROM parts WHERE set_id = ?1 AND idx = 0",
            [set_id],
            |row| row.get(0),
        )
        .unwrap()
    }

    #[test]
    fn duplicate_part_keeps_the_higher_message_id() {
        let (_dir, conn) = open_db();

        // Higher message id arrives second: it replaces the recorded one.
        let set_a = "01JQ8F2K9M4XZ0000000000A";
        let ta = template(set_a, 1, 100);
        let seen_a = vec![
            part_seen(&ta, 0, 0, 100, "aa", 10, 9201),
            part_seen(&ta, 0, 0, 100, "aa-again", 20, 9202),
        ];
        let summary_a = rescan::apply_seen(&conn, CHAT_ID, &seen_a).unwrap();
        assert_eq!(summary_a.parts_seen, 1);
        assert_eq!(summary_a.duplicates_skipped, 1);
        assert_eq!(recorded_message_id(&conn, set_a), 20);

        // Lower message id arrives second: the existing higher one is kept.
        let set_b = "01JQ8F2K9M4XZ0000000000B";
        let tb = template(set_b, 1, 100);
        let seen_b = vec![
            part_seen(&tb, 0, 0, 100, "aa", 20, 9301),
            part_seen(&tb, 0, 0, 100, "aa-again", 10, 9302),
        ];
        let summary_b = rescan::apply_seen(&conn, CHAT_ID, &seen_b).unwrap();
        assert_eq!(summary_b.duplicates_skipped, 1);
        assert_eq!(recorded_message_id(&conn, set_b), 20);
    }
}

mod batches {
    use super::*;

    #[test]
    fn applying_the_same_batch_twice_is_idempotent() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000003";
        let t = template(set_id, 2, 200);
        let seen = vec![
            part_seen(&t, 0, 0, 100, "aa", 301, 9401),
            part_seen(&t, 1, 100, 100, "bb", 302, 9402),
        ];

        let first = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        let second = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

        assert_eq!(first, second);
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Complete);
    }

    /// Two distinct sets whose parts are interleaved in one batch are
    /// tracked independently.
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

        let row_a = sets::get_set(&conn, set_a).unwrap().unwrap();
        let row_b = sets::get_set(&conn, set_b).unwrap().unwrap();
        assert_eq!(row_a.status, SetStatus::Complete);
        assert_eq!(row_b.status, SetStatus::Complete);
    }

    /// A second scan that only re-observes part 0 does not delete part 1's
    /// existing row, so a previously complete set stays complete: `apply_seen`
    /// is meant for full channel scans, and never deletes what it doesn't see.
    #[test]
    fn a_partial_second_batch_does_not_regress_a_completed_set() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000107";
        let t = template(set_id, 2, 200);

        let seen_complete = vec![
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        ];
        let summary1 = rescan::apply_seen(&conn, CHAT_ID, &seen_complete).unwrap();
        assert_eq!(summary1.sets_complete, 1);

        let seen_partial = vec![part_seen(&t, 0, 0, 100, "aa", 101, 9001)];
        let summary2 = rescan::apply_seen(&conn, CHAT_ID, &seen_partial).unwrap();

        assert_eq!(summary2.sets_complete, 1);
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Complete);
    }

    /// Two scans that observe the same parts in different message order
    /// finish with the same set status.
    #[test]
    fn batch_order_does_not_affect_the_final_set_state() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000108";
        let t = template(set_id, 2, 200);

        let seen_old_first = vec![
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        ];
        let summary_old_first = rescan::apply_seen(&conn, CHAT_ID, &seen_old_first).unwrap();

        let (_dir2, conn2) = open_db();
        let seen_new_first = vec![
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        ];
        let summary_new_first = rescan::apply_seen(&conn2, CHAT_ID, &seen_new_first).unwrap();

        assert_eq!(summary_old_first.sets_complete, 1);
        assert_eq!(summary_new_first.sets_complete, 1);

        let row1 = sets::get_set(&conn, set_id).unwrap().unwrap();
        let row2 = sets::get_set(&conn2, set_id).unwrap().unwrap();
        assert_eq!(row1.status, SetStatus::Complete);
        assert_eq!(row2.status, SetStatus::Complete);
    }

    /// A single scan spanning many sets completes every one of them
    /// correctly.
    #[test]
    fn a_large_batch_across_many_sets_completes_correctly() {
        let (_dir, conn) = open_db();

        let mut seen = Vec::new();

        // 1,000 sets with 5 parts each = 5,000 total parts.
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

        assert_eq!(summary.sets_seen, 1000);
        assert_eq!(summary.parts_seen, 5000);
        assert_eq!(summary.sets_complete, 1000);
        assert_eq!(summary.sets_incomplete, 0);
        assert_eq!(summary.duplicates_skipped, 0);

        let spot_check_id = format!("01JQ8F2K9M4XZ{:016}", 100);
        let row = sets::get_set(&conn, &spot_check_id).unwrap().unwrap();
        assert_eq!(row.status, SetStatus::Complete);
    }
}

mod ignored_and_malformed_input {
    use super::*;

    #[test]
    fn index_document_and_plain_text_messages_are_ignored() {
        let (_dir, conn) = open_db();
        let seen = vec![
            Seen {
                message_id: 1,
                doc_id: Some(1),
                caption: "#mlib-index v=2\n{\"pushed_at\":1,\"sets\":0,\"schema\":1}".into(),
                sent_at: 0,
            },
            Seen {
                message_id: 2,
                doc_id: Some(2),
                caption: "just a note, not a caption".into(),
                sent_at: 0,
            },
        ];

        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

        assert_eq!(summary.sets_seen, 0);
        assert_eq!(summary.parts_seen, 0);
        assert_eq!(summary.duplicates_skipped, 0);
        let count: i64 = conn
            .query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
            .unwrap();
        assert_eq!(count, 0);
    }

    /// An `#mlib-index` snapshot message mixed into a batch with real part
    /// captions is ignored; only the data parts are counted.
    #[test]
    fn an_index_snapshot_message_mixed_into_a_data_batch_is_ignored() {
        let (_dir, conn) = open_db();

        let set_id = "01JQ8F2K9M4XZ00000000114";
        let t = template(set_id, 2, 200);

        let seen = vec![
            Seen {
                message_id: 1,
                doc_id: Some(1),
                caption: "#mlib-index v=2\n{\"pushed_at\":1000000000,\"sets\":5,\"schema\":1}"
                    .into(),
                sent_at: 0,
            },
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        ];

        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

        assert_eq!(summary.parts_seen, 2);
        assert_eq!(summary.sets_complete, 1);
    }

    /// A caption message with no attached document has nothing to index,
    /// so it is skipped entirely rather than recorded as an empty part.
    #[test]
    fn part_with_no_document_id_is_skipped() {
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
            doc_id: None,
            caption: caption_text,
            sent_at: 0,
        }];

        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        assert_eq!(summary.parts_seen, 0);
        assert_eq!(summary.sets_seen, 0);
    }

    /// A caption carrying the `#mlib v=` marker but invalid JSON is skipped
    /// and counted as unparsed, not treated as a set with zero parts.
    #[test]
    fn malformed_caption_json_is_skipped_and_counted_as_unparsed() {
        let (_dir, conn) = open_db();

        let seen = vec![Seen {
            message_id: 101,
            doc_id: Some(9001),
            caption: "#mlib v=3\nsome_unsupported_format".to_string(),
            sent_at: 0,
        }];

        let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

        assert_eq!(summary.sets_seen, 0);
        assert_eq!(summary.parts_seen, 0);
        assert_eq!(summary.unparsed, 1);
    }
}

mod snapshot_error_paths {
    use super::*;

    /// `snapshot_to` fails outright when its destination's parent directory
    /// does not exist, rather than creating it.
    #[test]
    fn snapshot_to_fails_when_destination_parent_directory_is_missing() {
        let dir = tempfile::tempdir().unwrap();
        let conn = db::open(dir.path()).unwrap();

        let dest = dir.path().join("nonexistent_dir").join("library.push.db");
        snapshot::checkpoint(&conn).unwrap();

        let result = snapshot::snapshot_to(&conn, &dest);
        assert!(result.is_err());
    }
}

mod dating {
    use super::*;
    use support::rescan::SENT_BASE;

    /// A set found by a scan is dated by when its upload began — its earliest
    /// part — however the scan meets them. A channel is read newest first.
    #[test]
    fn a_folded_in_set_is_dated_by_its_earliest_part() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000D01";
        let t = template(set_id, 3, 300);
        let seen = vec![
            part_seen(&t, 2, 200, 100, "cc", 103, 9003),
            part_seen(&t, 1, 100, 100, "bb", 102, 9002),
            part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        ];
        rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.created_at, SENT_BASE + 101);
    }

    /// The defect this closes: every set a scan found was dated by the scan,
    /// so a whole library tied for "latest" and a stale title won the tie.
    #[test]
    fn sets_found_in_one_scan_keep_their_own_dates() {
        let (_dir, conn) = open_db();
        let earlier = template("01JQ8F2K9M4XZ00000000D02", 1, 100);
        let later = template("01JQ8F2K9M4XZ00000000D03", 1, 100);
        let seen = vec![
            part_seen(&later, 0, 0, 100, "bb", 250, 9102),
            part_seen(&earlier, 0, 0, 100, "aa", 120, 9101),
        ];
        rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
        let earlier_at = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000D02")
            .unwrap()
            .unwrap()
            .created_at;
        let later_at = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000D03")
            .unwrap()
            .unwrap()
            .created_at;
        assert_eq!((earlier_at, later_at), (SENT_BASE + 120, SENT_BASE + 250));
    }

    /// A rescan is additive: a set the index already had keeps its date, even
    /// when a later scan meets a part of it sent earlier than that date.
    #[test]
    fn a_set_already_indexed_keeps_its_date() {
        let (_dir, conn) = open_db();
        let set_id = "01JQ8F2K9M4XZ00000000D04";
        let t = template(set_id, 2, 200);
        rescan::apply_seen(
            &conn,
            CHAT_ID,
            &[part_seen(&t, 1, 100, 100, "bb", 300, 9202)],
        )
        .unwrap();

        rescan::apply_seen(&conn, CHAT_ID, &[part_seen(&t, 0, 0, 100, "aa", 100, 9201)]).unwrap();

        let row = sets::get_set(&conn, set_id).unwrap().unwrap();
        assert_eq!(row.created_at, SENT_BASE + 300);
    }
}
