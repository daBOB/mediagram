//! Asking, before a show is uploaded, which of its files a browser could not
//! play directly — and saying so once, grouped by reason.

use std::path::Path;

use futures::stream::{self, StreamExt};

use crate::media::direct_play::{self, Blocker};
use crate::media::show_episodes::Episode;
use crate::media::streams;

/// How many files are probed at once. Each spawns an ffprobe, so this is
/// bounded by processes rather than by bandwidth.
const PROBE_CONCURRENCY: usize = 4;

/// Asks each file whether a browser could open it.
///
/// Probes run together: each spawns an ffprobe, and a season of them one at a
/// time is a minute of nothing happening before the question is even asked.
pub(super) async fn survey(episodes: &[Episode]) -> Vec<Vec<Blocker>> {
    stream::iter(episodes)
        .map(|ep| async move {
            let probed = streams::probe(&ep.path).await.ok()?;
            let found = direct_play::blockers(&ep.path, &probed.streams);
            // Files with nothing wrong are dropped here, so what comes back
            // is one entry per file that will be converted — which is what
            // the count reported to the viewer means.
            (!found.is_empty()).then_some(found)
        })
        .buffered(PROBE_CONCURRENCY)
        .filter_map(|found| async move { found })
        .collect()
        .await
}

pub(super) fn report_blockers(by_file: &[Vec<Blocker>], dir: &Path) {
    if by_file.is_empty() {
        return;
    }
    println!();
    // Counted by file, not by reason: one file with three things wrong with
    // it is still one file the viewer has to do something about. Split by
    // what they can do — one group has a command to run and the other does
    // not — and a file can appear in both.
    let group = |fixable: bool| -> (usize, Vec<String>) {
        let matching: Vec<&Blocker> = by_file
            .iter()
            .flatten()
            .filter(|b| b.fixable_by_prepare() == fixable)
            .collect();
        let files = by_file
            .iter()
            .filter(|found| found.iter().any(|b| b.fixable_by_prepare() == fixable))
            .count();
        (files, reasons(&matching))
    };
    let (fixable_files, fixable_why) = group(true);
    let (stuck_files, stuck_why) = group(false);

    if fixable_files > 0 {
        println!(
            "{fixable_files} file(s) will be converted on every play: {}",
            fixable_why.join("; ")
        );
        println!(
            "   mediagram prepare \"{}\" --mp4 --out <dir> would fix that",
            dir.display()
        );
    }
    if stuck_files > 0 {
        println!(
            "{stuck_files} file(s) will be converted on every play: {}",
            stuck_why.join(" / ")
        );
        println!("   prepare cannot fix that — the picture itself would have to be re-encoded");
    }
}

/// The distinct reasons in a group, in a stable order.
fn reasons(blockers: &[&Blocker]) -> Vec<String> {
    let mut named: Vec<String> = blockers.iter().map(|b| b.reason()).collect();
    named.sort_unstable();
    named.dedup();
    named
}
