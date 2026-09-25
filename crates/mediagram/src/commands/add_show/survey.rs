//! Asking, before a show is uploaded, which of its files a browser could not
//! play directly — and saying so once, grouped by reason.

use std::path::Path;

use anyhow::{Context, Result};
use futures::stream::{self, StreamExt};

use crate::media::direct_play::{self, Blocker};
use crate::media::show_episodes::Episode;
use crate::media::streams;

/// How many files are probed at once. Each spawns an ffprobe, so this is
/// bounded by processes rather than by bandwidth.
const PROBE_CONCURRENCY: usize = 4;

#[derive(Default)]
pub(super) struct Survey {
    blockers: Vec<Vec<Blocker>>,
    failures: Vec<String>,
}

impl Survey {
    pub(super) fn needs_confirmation(&self) -> bool {
        !self.blockers.is_empty() || !self.failures.is_empty()
    }

    pub(super) fn report(&self, dir: &Path) {
        report_blockers(&self.blockers, dir);
        if !self.failures.is_empty() {
            println!(
                "\n{} file(s) have unknown browser compatibility:",
                self.failures.len()
            );
            for failure in &self.failures {
                println!("  {failure}");
            }
        }
    }
}

/// Asks each file whether a browser could open it.
///
/// Probes run together: each spawns an ffprobe, and a season of them one at a
/// time is a minute of nothing happening before the question is even asked.
pub(super) async fn survey(episodes: &[Episode]) -> Survey {
    let results = stream::iter(episodes)
        .map(|ep| async move {
            let probed = streams::probe(&ep.path).await.with_context(|| {
                format!("checking browser compatibility of {}", ep.path.display())
            })?;
            Ok::<_, anyhow::Error>(direct_play::blockers(&ep.path, &probed.streams))
        })
        .buffered(PROBE_CONCURRENCY)
        .collect::<Vec<Result<Vec<Blocker>>>>()
        .await;
    let mut survey = Survey::default();
    for result in results {
        match result {
            Ok(found) if !found.is_empty() => survey.blockers.push(found),
            Ok(_) => {}
            Err(error) => survey.failures.push(format!("{error:#}")),
        }
    }
    survey
}

fn report_blockers(by_file: &[Vec<Blocker>], dir: &Path) {
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

#[cfg(test)]
#[path = "survey_tests.rs"]
mod tests;
