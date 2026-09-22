//! `verify::report`'s pure decision logic: size/hash verdicts, the local
//! index invariant check, and row/summary rendering. No Telegram connection
//! is needed since `report` never performs IO.

use mediagram::index::status::PartStatus;
use mediagram::verify::render::{render_rows, summary_line};
use mediagram::verify::report::{
    ExpectedPart, ObservedMessage, PartVerdict, SetReport, apply_hash, check_local_invariant,
    verify_size,
};
use mediagram::verify::{LocalPart, forget_stale_success, load_parts, mark_verified, verified_since};

fn expected() -> ExpectedPart {
    ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(42),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    }
}

#[test]
fn not_uploaded_and_missing_message_fail_without_hash() {
    let v = verify_size(&expected(), &ObservedMessage::NotUploaded);
    assert!(v.failed());
    assert_eq!(v.hash_ok, None);

    let v = verify_size(&expected(), &ObservedMessage::MessageMissing);
    assert!(v.failed());

    let v = verify_size(&expected(), &ObservedMessage::NoDocument);
    assert!(v.failed());
}

#[test]
fn size_mismatch_fails_with_message() {
    let observed = ObservedMessage::Document {
        doc_id: 42,
        size: Some(999),
    };
    let v = verify_size(&expected(), &observed);
    assert!(v.failed());
    assert!(v.failure.unwrap().contains("size mismatch"));
}

#[test]
fn matching_size_and_doc_id_is_clean_ok() {
    let observed = ObservedMessage::Document {
        doc_id: 42,
        size: Some(1024),
    };
    let v = verify_size(&expected(), &observed);
    assert!(!v.failed());
    assert!(v.warning.is_none());
}

#[test]
fn doc_id_mismatch_is_a_warning_not_a_failure() {
    let observed = ObservedMessage::Document {
        doc_id: 99,
        size: Some(1024),
    };
    let v = verify_size(&expected(), &observed);
    assert!(!v.failed());
    assert!(v.warning.unwrap().contains("document id changed"));
}

#[test]
fn hash_match_sets_verified_at() {
    let observed = ObservedMessage::Document {
        doc_id: 42,
        size: Some(1024),
    };
    let v = verify_size(&expected(), &observed);
    let v = apply_hash(v, &"a".repeat(64), Some(&"a".repeat(64)), 1_700_000_000);
    assert_eq!(v.hash_ok, Some(true));
    assert_eq!(v.verified_at, Some(1_700_000_000));
    assert!(!v.failed());
}

#[test]
fn hash_mismatch_fails_even_though_size_matched() {
    let observed = ObservedMessage::Document {
        doc_id: 42,
        size: Some(1024),
    };
    let v = verify_size(&expected(), &observed);
    let v = apply_hash(v, &"b".repeat(64), Some(&"a".repeat(64)), 1_700_000_000);
    assert_eq!(v.hash_ok, Some(false));
    assert!(v.failed());
    assert!(v.failure.unwrap().contains("hash mismatch"));
}

#[test]
fn hash_is_skipped_when_size_already_failed() {
    let observed = ObservedMessage::Document {
        doc_id: 42,
        size: Some(1),
    };
    let v = verify_size(&expected(), &observed);
    let v = apply_hash(v, &"a".repeat(64), Some(&"a".repeat(64)), 1_700_000_000);
    assert_eq!(
        v.hash_ok, None,
        "hash must not be reported once size failed"
    );
    assert!(v.failed());
}

#[test]
fn local_invariant_catches_row_count_and_length_mismatches() {
    assert_eq!(check_local_invariant(3, 3, 300, 300), None);
    assert!(
        check_local_invariant(3, 2, 300, 200)
            .unwrap()
            .contains("part row")
    );
    assert!(
        check_local_invariant(3, 3, 300, 299)
            .unwrap()
            .contains("sum")
    );
}

#[test]
fn summary_and_rows_reflect_a_mixed_result() {
    let ok = PartVerdict {
        idx: 0,
        size_ok: true,
        hash_ok: Some(true),
        warning: None,
        failure: None,
        verified_at: Some(1),
    };
    let failing = PartVerdict {
        idx: 1,
        size_ok: false,
        hash_ok: None,
        warning: None,
        failure: Some("size mismatch: expected 1 bytes, got 2".into()),
        verified_at: None,
    };
    let report = SetReport {
        set_id: "01SET".into(),
        local_issue: None,
        parts: vec![ok, failing],
    };
    assert!(report.failed());
    let rows = render_rows(&report);
    assert_eq!(rows.len(), 2);
    assert!(rows[0].contains("ok"));
    assert!(rows[1].contains("FAIL"));
    assert!(summary_line(&report).contains("1/2"));
}

#[test]
fn all_ok_report_never_fails_and_is_not_a_false_positive() {
    let parts: Vec<PartVerdict> = (0..3)
        .map(|idx| PartVerdict {
            idx,
            size_ok: true,
            hash_ok: Some(true),
            warning: None,
            failure: None,
            verified_at: Some(1_700_000_000),
        })
        .collect();
    let report = SetReport {
        set_id: "01FULLSET".into(),
        local_issue: None,
        parts,
    };
    assert!(!report.failed());
    assert!(summary_line(&report).contains("3/3"));
}

#[test]
fn a_local_issue_fails_the_set_even_with_no_part_failures() {
    let report = SetReport {
        set_id: "01BADSET".into(),
        local_issue: Some("part lengths sum to 5 bytes but the set records total = 10".into()),
        parts: vec![],
    };
    assert!(report.failed());
    assert!(summary_line(&report).contains("local index issue"));
    assert!(render_rows(&report)[0].contains("local index issue"));
}

/// A part recorded in a different chat is not "missing": the message id was
/// never meant to be looked up in the chat now configured, and saying
/// "missing" would read as data loss and invite a re-upload.
#[test]
fn a_part_in_another_chat_names_both_chats() {
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(7),
        sha256: None,
        verified_at: None,
    };
    let verdict = verify_size(
        &expected,
        &ObservedMessage::OtherChat {
            recorded: -1001,
            current: -1002,
        },
    );
    assert!(!verdict.size_ok);
    let failure = verdict.failure.unwrap();
    assert!(
        failure.contains("-1001") && failure.contains("-1002"),
        "{failure}"
    );
}

/// A transient download error is a part-level failure carrying the cause,
/// never a hash mismatch (which would read as corruption on Telegram).
#[test]
fn a_failed_download_reports_the_cause_not_a_hash_mismatch() {
    let expected = ExpectedPart {
        idx: 4,
        byte_length: 2048,
        doc_id: Some(9),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let verdict = verify_size(
        &expected,
        &ObservedMessage::DownloadFailed("connection reset".into()),
    );
    assert!(!verdict.size_ok);
    assert_eq!(verdict.hash_ok, None);
    let failure = verdict.failure.unwrap();
    assert!(failure.contains("connection reset"), "{failure}");
    assert!(!failure.contains("hash mismatch"), "{failure}");
}

/// A part whose index row never recorded a hash cannot be proven by `--full`.
#[test]
fn a_part_with_no_recorded_hash_fails_the_comparison() {
    let expected = ExpectedPart {
        idx: 1,
        byte_length: 512,
        doc_id: Some(3),
        sha256: None,
        verified_at: None,
    };
    let verdict = verify_size(
        &expected,
        &ObservedMessage::Document {
            doc_id: 3,
            size: Some(512),
        },
    );
    let verdict = apply_hash(verdict, &"b".repeat(64), None, 1_700_000_000);
    assert_eq!(verdict.hash_ok, Some(false));
    assert!(verdict.verified_at.is_none());
    assert!(verdict.failure.unwrap().contains("none recorded"));
}

/// `--since` skips only parts already proven at or after the cutoff; an
/// older stamp, a missing stamp, or no cutoff at all means re-check.
#[test]
fn since_skips_only_parts_verified_at_or_after_the_cutoff() {
    let part = |verified_at| LocalPart {
        idx: 0,
        byte_length: 1024,
        chat_id: Some(-1001),
        message_id: Some(2),
        doc_id: Some(3),
        sha256: Some("c".repeat(64)),
        status: PartStatus::Done,
        verified_at,
    };
    assert!(verified_since(&part(Some(1_000)), Some(1_000)));
    assert!(verified_since(&part(Some(1_001)), Some(1_000)));
    assert!(!verified_since(&part(Some(999)), Some(1_000)));
    assert!(!verified_since(&part(None), Some(1_000)));
    assert!(!verified_since(&part(Some(1_000)), None));
}

/// A part that fails today loses the `verified_at` an earlier `--full` run
/// gave it — in the index, which `push-index` snapshots for other clients,
/// and in the verdict the report prints — while a part that passes keeps its
/// stamp.
#[test]
fn a_failing_part_forgets_its_old_success_and_a_passing_one_keeps_it() {
    use mediagram::index::{db, parts};
    use mlib_spec::part_plan::PartRange;

    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let set_id = "01SET0000000000000000001";
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 2048, 2, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
    let ranges = [PartRange { idx: 0, off: 0, len: 1024 }, PartRange { idx: 1, off: 1024, len: 1024 }];
    parts::insert_parts(&conn, set_id, &ranges).unwrap();
    mark_verified(&conn, set_id, 0, 1_700_000_000).unwrap();
    mark_verified(&conn, set_id, 1, 1_700_000_000).unwrap();
    let stored = load_parts(&conn, set_id).unwrap();

    let mut failing = verify_size(&expected(), &ObservedMessage::MessageMissing);
    failing.verified_at = Some(1_700_000_000);
    forget_stale_success(&conn, set_id, &stored[0], &mut failing).unwrap();

    let mut passing = PartVerdict {
        idx: 1,
        size_ok: true,
        hash_ok: None,
        warning: None,
        failure: None,
        verified_at: Some(1_700_000_000),
    };
    forget_stale_success(&conn, set_id, &stored[1], &mut passing).unwrap();

    let after = load_parts(&conn, set_id).unwrap();
    assert_eq!(failing.verified_at, None);
    assert_eq!(after[0].verified_at, None);
    assert_eq!(passing.verified_at, Some(1_700_000_000));
    assert_eq!(after[1].verified_at, Some(1_700_000_000));
}
