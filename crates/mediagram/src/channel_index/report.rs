//! What a pull says it did.

use super::conflicts::ResolveSummary;
use crate::index::merge::MergeReport;
use crate::index::sets_pending;

/// How many added set ids a report names, so it says what it found without
/// printing hundreds.
const NAMED: usize = 5;

pub(super) fn print(report: &MergeReport, dry_run: bool) {
    for line in lines(report, dry_run) {
        println!("{line}");
    }
}

/// What [`print`] says, a line per entry: what was added, then only the
/// counts that are not zero.
fn lines(report: &MergeReport, dry_run: bool) -> Vec<String> {
    let verb = if dry_run { "would add" } else { "added" };
    let mut out = vec![format!("{verb} {} set(s)", report.sets_added.len())];
    if !report.sets_added.is_empty() {
        let named: Vec<&str> = report
            .sets_added
            .iter()
            .take(NAMED)
            .map(String::as_str)
            .collect();
        let more = report.sets_added.len().saturating_sub(NAMED);
        let tail = if more > 0 {
            format!(" and {more} more")
        } else {
            String::new()
        };
        out.push(format!("  e.g. {}{tail}", named.join(", ")));
    }
    if !report.sets_skipped_pending.is_empty() {
        out.push(format!(
            "{} channel set(s) still pending elsewhere, skipped",
            report.sets_skipped_pending.len()
        ));
    }
    if !report.sets_skipped_removed.is_empty() {
        out.push(format!(
            "{} channel set(s) no longer exist in the channel, skipped",
            report.sets_skipped_removed.len()
        ));
    }
    if report.shows_added > 0 || report.shows_filled > 0 {
        out.push(format!(
            "{} show(s) added, {} filled in",
            report.shows_added, report.shows_filled
        ));
    }
    if report.credits_added > 0 {
        out.push(format!("{} credit row(s) added", report.credits_added));
    }
    if report.franchises_added > 0 {
        out.push(format!("{} franchise(s) added", report.franchises_added));
    }
    if report.artwork_added > 0 {
        out.push(format!("{} artwork row(s) added", report.artwork_added));
    }
    if report.anime_overrides_taken > 0 {
        out.push(format!(
            "{} anime override(s) taken",
            report.anime_overrides_taken
        ));
    }
    if report.categories_taken > 0 {
        out.push(format!("{} category row(s) taken", report.categories_taken));
    }
    if report.subtitles_taken > 0 {
        out.push(format!("{} subtitle file(s) taken", report.subtitles_taken));
    }
    if report.channel_lacks_subtitles {
        out.push(
            "warning: the channel index has no subtitle_files table but this index holds \
             subtitle rows — an older uploader likely published over it; publish again to \
             restore them"
                .to_string(),
        );
    }
    out
}

/// What resolving the merge's conflicting sets from their captions did.
pub(super) fn print_conflicts(total: usize, summary: &ResolveSummary) {
    for line in conflict_lines(total, summary) {
        println!("{line}");
    }
}

fn conflict_lines(total: usize, summary: &ResolveSummary) -> Vec<String> {
    let mut out = Vec::new();
    if total > 0 {
        out.push(format!(
            "{} of {total} conflicting set(s) re-read from captions",
            summary.resolved
        ));
    }
    out.extend(sets_pending::skipped_lines(&summary.skipped_kinds));
    if summary.newer_captions > 0 {
        out.push(format!(
            "{} caption(s) among the conflicts use a newer #mlib version this build cannot read",
            summary.newer_captions
        ));
    }
    out
}

#[cfg(test)]
#[path = "report_tests.rs"]
mod tests;
