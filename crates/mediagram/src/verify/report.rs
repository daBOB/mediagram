//! Pure decision logic for `verify`: turns observed Telegram state (and a
//! `--full` hash) into per-part verdicts and a set report. No IO, so it is
//! covered directly by `tests/verify_report.rs`.

/// What the local index expects a part to look like.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExpectedPart {
    pub idx: u32,
    pub byte_length: u64,
    /// `None` when the part was never marked `done` locally.
    pub doc_id: Option<i64>,
    pub sha256: Option<String>,
    pub verified_at: Option<i64>,
}

/// What was observed on Telegram for a part's recorded message id.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ObservedMessage {
    /// The part was never uploaded locally: no message id to look up.
    NotUploaded,
    /// `get_messages_by_id` returned nothing for the recorded message id.
    MessageMissing,
    /// The message exists but carries no document media.
    NoDocument,
    Document {
        doc_id: i64,
        size: Option<u64>,
    },
}

/// Outcome of checking one part.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PartVerdict {
    pub idx: u32,
    pub size_ok: bool,
    /// `None` in default mode (no `--full`); `Some` once a hash was compared.
    pub hash_ok: Option<bool>,
    /// Non-fatal observation, e.g. a document id that changed after a forward.
    pub warning: Option<String>,
    /// Set once `size_ok` is false or `hash_ok` is `Some(false)`.
    pub failure: Option<String>,
    pub verified_at: Option<i64>,
}

impl PartVerdict {
    pub fn failed(&self) -> bool {
        self.failure.is_some()
    }
}

/// Checks message existence and document size against the local index.
/// Hashing (`--full`) is layered on afterwards via [`apply_hash`].
pub fn verify_size(expected: &ExpectedPart, observed: &ObservedMessage) -> PartVerdict {
    let base = |failure: Option<String>| PartVerdict {
        idx: expected.idx,
        size_ok: failure.is_none(),
        hash_ok: None,
        warning: None,
        failure,
        verified_at: expected.verified_at,
    };
    match observed {
        ObservedMessage::NotUploaded => base(Some("no upload recorded locally".into())),
        ObservedMessage::MessageMissing => base(Some("message not found on the channel".into())),
        ObservedMessage::NoDocument => base(Some("message has no document media".into())),
        ObservedMessage::Document { doc_id, size } => {
            let mut verdict = if *size == Some(expected.byte_length) {
                base(None)
            } else {
                let observed_size = size
                    .map(|s| s.to_string())
                    .unwrap_or_else(|| "unknown".to_string());
                base(Some(format!(
                    "size mismatch: expected {} bytes, got {observed_size}",
                    expected.byte_length
                )))
            };
            if expected.doc_id.is_some_and(|d| d != *doc_id) {
                verdict.warning = Some(format!(
                    "document id changed ({} -> {doc_id}); likely forwarded",
                    expected.doc_id.unwrap()
                ));
            }
            verdict
        }
    }
}

/// Folds a `--full` hash comparison into a verdict already produced by
/// [`verify_size`]. A part that already failed the size check is left
/// untouched: a wrong-size document is not worth downloading.
pub fn apply_hash(
    mut verdict: PartVerdict,
    computed_sha256: &str,
    expected_sha256: Option<&str>,
    now: i64,
) -> PartVerdict {
    if !verdict.size_ok {
        return verdict;
    }
    let matches = expected_sha256.is_some_and(|e| e.eq_ignore_ascii_case(computed_sha256));
    verdict.hash_ok = Some(matches);
    if matches {
        verdict.verified_at = Some(now);
    } else {
        verdict.failure = Some(format!(
            "hash mismatch: expected {}, got {computed_sha256}",
            expected_sha256.unwrap_or("(none recorded)")
        ));
    }
    verdict
}

/// Structural check, run once per set before any remote calls: the local
/// part row count must match `part_count`, and lengths must sum to `total`.
pub fn check_local_invariant(
    part_count: u32,
    row_count: usize,
    total: u64,
    sum_len: u64,
) -> Option<String> {
    if row_count as u32 != part_count {
        return Some(format!(
            "index has {row_count} part row(s), expected {part_count}"
        ));
    }
    (sum_len != total).then(|| format!("part lengths sum to {sum_len}, expected total {total}"))
}

/// One set's full verification outcome.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SetReport {
    pub set_id: String,
    /// Set once [`check_local_invariant`] finds a structural problem.
    pub local_issue: Option<String>,
    pub parts: Vec<PartVerdict>,
}

impl SetReport {
    pub fn failed(&self) -> bool {
        self.local_issue.is_some() || self.parts.iter().any(PartVerdict::failed)
    }
}

/// One printable line per part: index, size result, hash result, timestamp.
pub fn render_rows(report: &SetReport) -> Vec<String> {
    let set_id = &report.set_id;
    let mut rows = Vec::with_capacity(report.parts.len() + 1);
    if let Some(issue) = &report.local_issue {
        rows.push(format!("  set {set_id}: local index issue: {issue}"));
    }
    rows.extend(report.parts.iter().map(render_row));
    rows
}

fn render_row(part: &PartVerdict) -> String {
    let size_col = if part.size_ok { "ok" } else { "FAIL" };
    let hash_col = match part.hash_ok {
        None => "skipped",
        Some(true) => "ok",
        Some(false) => "FAIL",
    };
    let verified = part
        .verified_at
        .map(|t| t.to_string())
        .unwrap_or_else(|| "-".to_string());
    let mut row = format!(
        "  part {:>3}  size {size_col:<7} hash {hash_col:<7} verified_at {verified}",
        part.idx
    );
    if let Some(warning) = &part.warning {
        row.push_str(&format!("  warn: {warning}"));
    }
    if let Some(failure) = &part.failure {
        row.push_str(&format!("  fail: {failure}"));
    }
    row
}

/// One-line summary for a set, printed after its part rows.
pub fn summary_line(report: &SetReport) -> String {
    let total = report.parts.len();
    let failed = report.parts.iter().filter(|p| p.failed()).count();
    let warned = report.parts.iter().filter(|p| p.warning.is_some()).count();
    let set_id = &report.set_id;
    if report.failed() {
        let suffix = if report.local_issue.is_some() {
            " (local index issue)"
        } else {
            ""
        };
        format!("set {set_id}: {failed}/{total} part(s) FAILED{suffix}")
    } else if warned > 0 {
        format!("set {set_id}: {total}/{total} part(s) ok, {warned} warning(s)")
    } else {
        format!("set {set_id}: {total}/{total} part(s) ok")
    }
}
