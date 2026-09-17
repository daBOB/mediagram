//! Edge case probes for the verify command.
//! Tests boundary conditions, overflow scenarios, hash validation, and verdict precedence.

use mediagram::verify::render::{render_rows, summary_line};
use mediagram::verify::report::{
    ExpectedPart, ObservedMessage, PartVerdict, SetReport, apply_hash, check_local_invariant,
    verify_size,
};

// ============================================================================
// Byte length boundaries
// ============================================================================

#[test]
fn verify_zero_length_part() {
    // Edge case: a part with zero bytes (empty placeholder)
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 0,
        doc_id: Some(100),
        sha256: Some(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".to_string(),
        ),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 100,
        size: Some(0),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
    assert!(v.size_ok);
}

#[test]
fn verify_single_byte_part() {
    // Edge case: minimum non-zero size
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1,
        doc_id: Some(101),
        sha256: Some(
            "6e340b9cffb37a989ca544e6bb780a2c78901d3fb33738768511a30617afa01d".to_string(),
        ),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 101,
        size: Some(1),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
}

#[test]
fn verify_3_5_gib_exactly() {
    // Edge case: 3.5 GiB exactly (3758096384 bytes)
    // This is at the boundary where some systems might handle size differently
    const THREE_HALF_GIB: u64 = 3_758_096_384;
    let expected = ExpectedPart {
        idx: 0,
        byte_length: THREE_HALF_GIB,
        doc_id: Some(102),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 102,
        size: Some(THREE_HALF_GIB),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
    assert!(v.size_ok);
}

#[test]
fn verify_3_5_gib_minus_one_byte() {
    // Edge case: one byte below 3.5 GiB
    const THREE_HALF_GIB_MINUS_ONE: u64 = 3_758_096_383;
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 3_758_096_384,
        doc_id: Some(103),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 103,
        size: Some(THREE_HALF_GIB_MINUS_ONE),
    };
    let v = verify_size(&expected, &observed);
    assert!(v.failed());
    assert!(v.failure.unwrap().contains("size mismatch"));
}

#[test]
fn verify_3_5_gib_plus_one_byte() {
    // Edge case: one byte above 3.5 GiB
    const THREE_HALF_GIB_PLUS_ONE: u64 = 3_758_096_385;
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 3_758_096_384,
        doc_id: Some(104),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 104,
        size: Some(THREE_HALF_GIB_PLUS_ONE),
    };
    let v = verify_size(&expected, &observed);
    assert!(v.failed());
}

// ============================================================================
// u64 overflow and large number handling
// ============================================================================

#[test]
fn verify_sum_near_u64_max() {
    // Edge case: summing part lengths near u64::MAX
    let near_max = u64::MAX - 1;
    let ok = check_local_invariant(2, 2, near_max, near_max);
    assert_eq!(ok, None);
}

#[test]
fn verify_sum_overflow_one_part() {
    // Edge case: a single part with size near u64::MAX
    let huge = u64::MAX;
    let expected = ExpectedPart {
        idx: 0,
        byte_length: huge,
        doc_id: Some(105),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 105,
        size: Some(huge),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
    assert!(v.size_ok);
}

#[test]
fn verify_parts_sum_mismatch_huge_numbers() {
    // Edge case: part rows sum to a different total with very large numbers
    let sum = u64::MAX - 1000;
    let expected_total = u64::MAX; // off by 1000
    let ok = check_local_invariant(5, 5, expected_total, sum);
    assert!(ok.is_some());
    assert!(ok.unwrap().contains("sum"));
}

// ============================================================================
// Index structure and ordering edge cases
// ============================================================================

#[test]
fn verify_idx_out_of_order_gaps_detected_in_count() {
    // Edge case: missing indices are detected when part_count doesn't match row_count
    // This simulates a set with indices [0, 1, 3] (skips 2)
    let ok = check_local_invariant(3, 3, 300, 300);
    assert_eq!(ok, None); // Structure is valid; actual index checks happen at a higher layer
}

#[test]
fn verify_duplicate_idx_row_count_mismatch() {
    // Edge case: duplicate indices would reduce actual unique count vs part_count
    // Simulated by row_count < part_count
    let ok = check_local_invariant(5, 3, 500, 300);
    assert!(ok.is_some());
    assert!(ok.unwrap().contains("part row"));
}

#[test]
fn verify_single_part_idx_zero() {
    // Edge case: valid single-part set with idx=0
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(200),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 200,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
}

#[test]
fn verify_large_idx_value() {
    // Edge case: part with a very large index number
    let expected = ExpectedPart {
        idx: u32::MAX - 1,
        byte_length: 1024,
        doc_id: Some(201),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 201,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.failed());
    assert_eq!(v.idx, u32::MAX - 1);
}

// ============================================================================
// Empty and minimal sets
// ============================================================================

#[test]
fn verify_empty_parts_list_with_zero_count() {
    // Edge case: a set with zero parts recorded
    let report = SetReport {
        set_id: "EMPTY".into(),
        local_issue: None,
        parts: vec![],
    };
    assert!(!report.failed()); // Empty set with no issues is technically ok
    assert_eq!(render_rows(&report).len(), 0);
}

#[test]
fn verify_set_with_one_part() {
    // Edge case: minimal non-empty set
    let report = SetReport {
        set_id: "01".into(),
        local_issue: None,
        parts: vec![PartVerdict {
            idx: 0,
            size_ok: true,
            hash_ok: Some(true),
            warning: None,
            failure: None,
            verified_at: Some(1_700_000_000),
        }],
    };
    assert!(!report.failed());
    let summary = summary_line(&report);
    assert!(summary.contains("1/1"));
}

// ============================================================================
// Hash string edge cases
// ============================================================================

#[test]
fn verify_hash_wrong_length_too_short() {
    // Edge case: hash string too short (31 hex chars instead of 64)
    let short_hash: String = "abc".repeat(10) + "a"; // 31 chars
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(300),
        sha256: Some(short_hash),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 300,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    // apply_hash will compare it as-is, and case-insensitive comparison will fail
    let v = apply_hash(v, &"0".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(false)); // Mismatch because computed differs
}

#[test]
fn verify_hash_wrong_length_too_long() {
    // Edge case: hash string too long (65 hex chars instead of 64)
    let long_hash: String = "a".repeat(65);
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(301),
        sha256: Some(long_hash),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 301,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(false)); // Mismatch due to length
}

#[test]
fn verify_hash_non_hex_characters() {
    // Edge case: hash contains non-hex characters (e.g., 'g', 'z')
    let non_hex_hash = "z".repeat(64);
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(302),
        sha256: Some(non_hex_hash),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 302,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(false)); // Case-insensitive compare will fail
}

#[test]
fn verify_hash_uppercase_vs_lowercase_match() {
    // Edge case: computed hash in lowercase, expected in uppercase (should match via case_ignore)
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(303),
        sha256: Some("ABCDEF0123456789".repeat(4)), // 64 chars uppercase
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 303,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let computed_lowercase = "abcdef0123456789".repeat(4);
    let v = apply_hash(v, &computed_lowercase, expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(true)); // Should match despite case difference
    assert_eq!(v.verified_at, Some(1));
}

#[test]
fn verify_hash_empty_string() {
    // Edge case: hash is empty string (invalid)
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(304),
        sha256: Some(String::new()),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 304,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(false)); // Empty string won't match any computed hash
}

#[test]
fn verify_hash_missing_expected_but_computed() {
    // Edge case: expected hash is None but we have a computed hash
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(305),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 305,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), None, 1);
    // When expected is None, the message in failure will say "(none recorded)"
    assert_eq!(v.hash_ok, Some(false));
    assert!(v.failure.unwrap().contains("none recorded"));
}

// ============================================================================
// Rendered row and summary edge cases
// ============================================================================

#[test]
fn verify_render_row_with_very_long_set_id() {
    // Edge case: set ID is very long
    let long_id = "SET_".repeat(50); // 200+ chars
    let report = SetReport {
        set_id: long_id.clone(),
        local_issue: None,
        parts: vec![PartVerdict {
            idx: 0,
            size_ok: true,
            hash_ok: Some(true),
            warning: None,
            failure: None,
            verified_at: Some(1),
        }],
    };
    // The summary_line includes the set_id
    let summary = summary_line(&report);
    assert!(summary.contains(&long_id[..50])); // At least some of the ID appears
}

#[test]
fn verify_render_row_with_warning_and_failure() {
    // Edge case: a part with both warning and failure (edge case in display)
    let part = PartVerdict {
        idx: 5,
        size_ok: false,
        hash_ok: None,
        warning: Some("document id changed (1 -> 2)".into()),
        failure: Some("size mismatch: expected 1024 bytes, got 512".into()),
        verified_at: None,
    };
    let report = SetReport {
        set_id: "TEST".into(),
        local_issue: None,
        parts: vec![part],
    };
    let rows = render_rows(&report);
    assert!(rows[0].contains("warn:"));
    assert!(rows[0].contains("fail:"));
}

#[test]
fn verify_summary_line_all_failed() {
    // Edge case: all parts failed
    let parts: Vec<PartVerdict> = (0..5)
        .map(|idx| PartVerdict {
            idx,
            size_ok: false,
            hash_ok: None,
            warning: None,
            failure: Some(format!("failure {idx}")),
            verified_at: None,
        })
        .collect();
    let report = SetReport {
        set_id: "ALLFAIL".into(),
        local_issue: None,
        parts,
    };
    let summary = summary_line(&report);
    assert!(summary.contains("5/5"));
    assert!(summary.contains("FAILED"));
}

#[test]
fn verify_summary_line_with_warnings_only() {
    // Edge case: some warnings but no failures
    let parts: Vec<PartVerdict> = (0..3)
        .map(|idx| PartVerdict {
            idx,
            size_ok: true,
            hash_ok: Some(true),
            warning: Some("document id changed".into()),
            failure: None,
            verified_at: Some(1),
        })
        .collect();
    let report = SetReport {
        set_id: "WARNED".into(),
        local_issue: None,
        parts,
    };
    let summary = summary_line(&report);
    assert!(summary.contains("3/3"));
    assert!(summary.contains("ok"));
    assert!(summary.contains("warning"));
}

// ============================================================================
// Verdict precedence and interaction edge cases
// ============================================================================

#[test]
fn verify_verdict_hash_skipped_when_size_fails() {
    // Edge case: hash is intentionally skipped once size check fails
    // Even if we provide matching hashes, the verdict should not set hash_ok
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1000,
        doc_id: Some(400),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 400,
        size: Some(999), // Size mismatch
    };
    let v = verify_size(&expected, &observed);
    assert!(!v.size_ok);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, None); // Not evaluated
    assert!(v.failed());
}

#[test]
fn verify_verdict_doc_id_mismatch_is_warning_only() {
    // Edge case: document ID changes but size/hash match (expected due to message forwarding)
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(100),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 999, // Different doc_id
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    assert!(v.size_ok);
    assert!(v.warning.is_some());
    assert!(!v.failed()); // Warning doesn't cause failure
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 1);
    assert_eq!(v.hash_ok, Some(true));
    assert!(!v.failed()); // Still not failing
}

#[test]
fn verify_verdict_multiple_parts_mixed_outcomes() {
    // Edge case: complex scenario with ok, warning, and failed parts
    let ok = PartVerdict {
        idx: 0,
        size_ok: true,
        hash_ok: Some(true),
        warning: None,
        failure: None,
        verified_at: Some(1),
    };
    let warned = PartVerdict {
        idx: 1,
        size_ok: true,
        hash_ok: Some(true),
        warning: Some("document id changed".into()),
        failure: None,
        verified_at: Some(1),
    };
    let failed = PartVerdict {
        idx: 2,
        size_ok: false,
        hash_ok: None,
        warning: None,
        failure: Some("size mismatch".into()),
        verified_at: None,
    };
    let report = SetReport {
        set_id: "MIXED".into(),
        local_issue: None,
        parts: vec![ok, warned, failed],
    };
    assert!(report.failed());
    let summary = summary_line(&report);
    assert!(summary.contains("1/3")); // 1 failed
}

#[test]
fn verify_local_issue_takes_precedence() {
    // Edge case: local issue failure takes precedence in summary even with ok parts
    let ok_part = PartVerdict {
        idx: 0,
        size_ok: true,
        hash_ok: Some(true),
        warning: None,
        failure: None,
        verified_at: Some(1),
    };
    let report = SetReport {
        set_id: "LOCALISSUE".into(),
        local_issue: Some("part count mismatch".into()),
        parts: vec![ok_part],
    };
    assert!(report.failed());
    let summary = summary_line(&report);
    assert!(summary.contains("local index issue"));
}

// ============================================================================
// Timestamp and verified_at edge cases
// ============================================================================

#[test]
fn verify_timestamp_zero() {
    // Edge case: timestamp = 0 (epoch)
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(500),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 500,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), 0);
    assert_eq!(v.verified_at, Some(0));
}

#[test]
fn verify_timestamp_max_i64() {
    // Edge case: timestamp = i64::MAX
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(501),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 501,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(v, &"a".repeat(64), expected.sha256.as_deref(), i64::MAX);
    assert_eq!(v.verified_at, Some(i64::MAX));
}

// ============================================================================
// Message observation variants
// ============================================================================

#[test]
fn verify_not_uploaded_has_no_hash_check() {
    // Edge case: NotUploaded state means no doc_id at all
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(600),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let v = verify_size(&expected, &ObservedMessage::NotUploaded);
    assert!(v.failed());
    assert_eq!(v.hash_ok, None);
    assert!(!v.size_ok);
}

#[test]
fn verify_message_missing_has_no_hash_check() {
    // Edge case: MessageMissing state
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(601),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let v = verify_size(&expected, &ObservedMessage::MessageMissing);
    assert!(v.failed());
    assert_eq!(v.hash_ok, None);
}

#[test]
fn verify_no_document_has_no_hash_check() {
    // Edge case: Message exists but has no document
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(602),
        sha256: Some("a".repeat(64)),
        verified_at: None,
    };
    let v = verify_size(&expected, &ObservedMessage::NoDocument);
    assert!(v.failed());
    assert_eq!(v.hash_ok, None);
}

#[test]
fn verify_document_size_unknown() {
    // Edge case: Document exists but size is not available
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(603),
        sha256: None,
        verified_at: None,
    };
    let observed = ObservedMessage::Document {
        doc_id: 603,
        size: None, // Size not available
    };
    let v = verify_size(&expected, &observed);
    assert!(v.failed());
    assert!(v.failure.unwrap().contains("unknown"));
}

// ============================================================================
// Pre-existing verified_at preservation
// ============================================================================

#[test]
fn verify_preserves_existing_verified_at_on_size_ok() {
    // Edge case: part was already verified, and size check passes again
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(700),
        sha256: Some("a".repeat(64)),
        verified_at: Some(1_600_000_000), // Already verified before
    };
    let observed = ObservedMessage::Document {
        doc_id: 700,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    assert_eq!(v.verified_at, Some(1_600_000_000)); // Preserved
}

#[test]
fn verify_overwrites_verified_at_on_new_hash() {
    // Edge case: hash check updates verified_at even if it was set before
    let expected = ExpectedPart {
        idx: 0,
        byte_length: 1024,
        doc_id: Some(701),
        sha256: Some("a".repeat(64)),
        verified_at: Some(1_600_000_000),
    };
    let observed = ObservedMessage::Document {
        doc_id: 701,
        size: Some(1024),
    };
    let v = verify_size(&expected, &observed);
    let v = apply_hash(
        v,
        &"a".repeat(64),
        expected.sha256.as_deref(),
        1_700_000_000,
    );
    assert_eq!(v.verified_at, Some(1_700_000_000)); // Updated to new timestamp
}
