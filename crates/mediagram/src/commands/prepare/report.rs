//! What `prepare` prints before it changes anything: the plan per file, and
//! which files `--mp4` cannot make direct-playable.

use super::Candidate;
use crate::media::direct_play;
use crate::media::prepare::plan::Verdict;
use crate::paths::file_name;

pub(super) fn print_table(planned: &[Candidate], limit: u64) {
    println!(
        "{:<44} {:>9} {:>6} {:>5} {:>11}  verdict",
        "file", "size", "audio", "subs", "estimated"
    );
    for Candidate { file, size, plan, .. } in planned {
        let verdict = match plan.verdict {
            Verdict::AlreadyFits => "already fits".to_string(),
            Verdict::NothingToDrop => "nothing to drop".to_string(),
            Verdict::Prepare => "prepare".to_string(),
            Verdict::PrepareStillOversized => "prepare, still needs 2 parts".to_string(),
        };
        println!(
            "{:<44} {:>8.2}G {:>6} {:>5} {:>10.2}G  {}",
            truncate(&file_name(file), 44),
            *size as f64 / 1e9,
            plan.dropped_audio,
            plan.dropped_subtitles,
            plan.estimated_bytes as f64 / 1e9,
            verdict
        );
    }
    let total: u64 = planned.iter().map(|c| c.size).sum();
    let after: u64 = planned
        .iter()
        .map(|c| match c.plan.verdict {
            Verdict::Prepare | Verdict::PrepareStillOversized => c.plan.estimated_bytes,
            _ => c.size,
        })
        .sum();
    println!(
        "\n{:.1} GB -> {:.1} GB, saving {:.1} GB. One part is {:.2} GB.",
        total as f64 / 1e9,
        after as f64 / 1e9,
        (total - after) as f64 / 1e9,
        limit as f64 / 1e9
    );
}

/// Says which files `--mp4` cannot make direct-playable, and why.
///
/// Converting the wrapper of an HEVC file is wasted work: the player converts
/// it on every play regardless, because the picture itself is what a browser
/// will not open. Better to say so before an hour of encoding than after.
pub(super) fn warn_about_video_codecs(planned: &[Candidate]) {
    let mut names: Vec<String> = planned
        .iter()
        .flat_map(|c| direct_play::blockers(&c.file, &c.plan.keep))
        .filter(|blocker| !blocker.fixable_by_prepare())
        .map(|blocker| blocker.reason())
        .collect();
    let count = names.len();
    if count == 0 {
        return;
    }
    names.sort_unstable();
    names.dedup();
    println!(
        "\nwarning: {count} file(s) carry {}, which a browser will not open. \
         Changing the wrapper does not change that — the picture itself would \
         have to be re-encoded, so these will still be converted on every play.",
        names.join(" / ")
    );
}


pub(super) fn truncate(s: &str, max: usize) -> String {
    if s.chars().count() <= max {
        return s.to_string();
    }
    s.chars().take(max - 1).collect::<String>() + "…"
}
