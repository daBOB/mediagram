//! `verify::report`'s pure decision logic: size/hash verdicts, the local
//! index invariant check, and row/summary rendering. No Telegram connection
//! is needed since `report` never performs IO.

use mediagram::verify::report::{
    ExpectedPart, ObservedMessage, PartVerdict, SetReport, apply_hash, check_local_invariant,
    render_rows, summary_line, verify_size,
};

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
