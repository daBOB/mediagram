//! Printable output for a [`SetReport`]: one row per part plus a one-line
//! summary. Split from [`super::report`] so the decision logic and the
//! presentation of it stay separately readable and testable.

use super::report::{PartVerdict, SetReport};

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
        if total == 0 {
            format!("set {set_id}: FAILED{suffix}")
        } else {
            format!("set {set_id}: {failed}/{total} part(s) FAILED{suffix}")
        }
    } else if warned > 0 {
        format!("set {set_id}: {total}/{total} part(s) ok, {warned} warning(s)")
    } else {
        format!("set {set_id}: {total}/{total} part(s) ok")
    }
}
