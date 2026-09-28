//! What a pull says it did.

use crate::index::merge::MergeReport;

/// How many added set ids a report names, so it says what it found without
/// printing hundreds.
const NAMED: usize = 5;

pub(super) fn print(report: &MergeReport, dry_run: bool) {
    let verb = if dry_run { "would add" } else { "added" };
    println!("{verb} {} set(s)", report.sets_added.len());
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
        println!("  e.g. {}{tail}", named.join(", "));
    }
    if !report.sets_skipped_pending.is_empty() {
        println!(
            "{} channel set(s) still pending elsewhere, skipped",
            report.sets_skipped_pending.len()
        );
    }
    if !report.sets_skipped_removed.is_empty() {
        println!(
            "{} channel set(s) no longer exist in the channel, skipped",
            report.sets_skipped_removed.len()
        );
    }
    if report.shows_added > 0 || report.shows_filled > 0 {
        println!(
            "{} show(s) added, {} filled in",
            report.shows_added, report.shows_filled
        );
    }
    if report.credits_added > 0 {
        println!("{} credit row(s) added", report.credits_added);
    }
    if report.franchises_added > 0 {
        println!("{} franchise(s) added", report.franchises_added);
    }
    if report.artwork_added > 0 {
        println!("{} artwork row(s) added", report.artwork_added);
    }
    if report.anime_overrides_taken > 0 {
        println!("{} anime override(s) taken", report.anime_overrides_taken);
    }
    if report.categories_taken > 0 {
        println!("{} category row(s) taken", report.categories_taken);
    }
}
