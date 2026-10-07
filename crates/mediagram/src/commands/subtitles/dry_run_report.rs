//! The dry run's TSV: one row per file, then totals by kind/container, and
//! the de/en-subtitled sets no file matched at all.

use std::collections::{BTreeMap, HashMap, HashSet};

use mlib_spec::caption::position_code;

use crate::index::set_row::SetRow;
use crate::media::classify;
use crate::media::prepare::plan::PICTURE_SUBTITLES;
use crate::media::streams::{Probed, StreamKind};

use super::dry_run_totals::{MatchTotals, Totals, add_match, gb};
use super::match_source::{Match, SourceFile, Verdict};

pub fn report_lines(
    source_files: &[SourceFile],
    probes: &[Option<Probed>],
    matches: &[Match],
    sets: &[SetRow],
) -> Vec<String> {
    let by_id: HashMap<&str, &SetRow> = sets.iter().map(|s| (s.set_id.as_str(), s)).collect();

    let mut out = vec!["file\tverdict\tset_id\tkind\ttitle\tde_en_text\tpicture_only".to_string()];

    let mut matched_totals: BTreeMap<(String, String), MatchTotals> = BTreeMap::new();
    let mut fallback_totals: BTreeMap<(String, String), MatchTotals> = BTreeMap::new();
    let mut ambiguous = Totals::default();
    let mut conflicts = Totals::default();
    let mut conflict_sets: HashSet<&str> = HashSet::new();
    let mut unmatched = Totals::default();
    let mut matched_set_ids: HashSet<&str> = HashSet::new();

    for ((source, probed), m) in source_files.iter().zip(probes).zip(matches) {
        let target_id = match &m.verdict {
            Verdict::Matched(id) | Verdict::Fallback(id) | Verdict::Conflict(id) => {
                Some(id.as_str())
            }
            Verdict::Ambiguous | Verdict::Unmatched => None,
        };
        let set = target_id.and_then(|id| by_id.get(id).copied());
        let counts = matches!(m.verdict, Verdict::Matched(_) | Verdict::Fallback(_))
            .then(|| probed.as_ref().map(count_subtitle_tracks))
            .flatten();

        out.push(format!(
            "{}\t{}\t{}\t{}\t{}\t{}\t{}",
            source.path.display(),
            verdict_label(&m.verdict),
            target_id.unwrap_or(""),
            set.map_or("", |s| s.kind.as_str()),
            set.map(display_title).unwrap_or_default(),
            counts.map(|(d, _)| d.to_string()).unwrap_or_default(),
            counts.map(|(_, p)| p.to_string()).unwrap_or_default(),
        ));

        match &m.verdict {
            Verdict::Matched(id) => {
                matched_set_ids.insert(id.as_str());
                add_match(&mut matched_totals, set, source.size, counts);
            }
            Verdict::Fallback(id) => {
                matched_set_ids.insert(id.as_str());
                add_match(&mut fallback_totals, set, source.size, counts);
            }
            Verdict::Ambiguous => {
                ambiguous.count += 1;
                ambiguous.bytes += source.size;
            }
            Verdict::Conflict(id) => {
                conflicts.count += 1;
                conflicts.bytes += source.size;
                conflict_sets.insert(id.as_str());
            }
            Verdict::Unmatched => {
                unmatched.count += 1;
                unmatched.bytes += source.size;
            }
        }
    }

    out.push("#\n# matched sets by kind/container (count, with de/en text, bytes):".to_string());
    for ((kind, container), t) in &matched_totals {
        out.push(format!(
            "#   {kind}/{container}: {} sets, {} with de/en text, {:.2} GB",
            t.count,
            t.with_de_en_text,
            gb(t.bytes)
        ));
    }
    out.push("# fallback candidates by kind/container (listed only, never sent without --accept-fallback):".to_string());
    for ((kind, container), t) in &fallback_totals {
        out.push(format!(
            "#   {kind}/{container}: {} candidates, {} with de/en text, {:.2} GB",
            t.count,
            t.with_de_en_text,
            gb(t.bytes)
        ));
    }
    out.push(format!(
        "# ambiguous: {} files, {:.2} GB",
        ambiguous.count,
        gb(ambiguous.bytes)
    ));
    out.push(format!(
        "# conflicts: {} files across {} sets, {:.2} GB",
        conflicts.count,
        conflict_sets.len(),
        gb(conflicts.bytes)
    ));
    out.push(format!(
        "# unmatched: {} files, {:.2} GB",
        unmatched.count,
        gb(unmatched.bytes)
    ));

    out.push("#\n# de/en-subtitled sets no file matched:".to_string());
    for set in sets {
        let has_de_en = set.slang.iter().any(|l| l == "de" || l == "en");
        if has_de_en && !matched_set_ids.contains(set.set_id.as_str()) {
            out.push(format!(
                "#   {}\t{}\t{}",
                set.set_id,
                set.kind,
                display_title(set)
            ));
        }
    }
    out
}

fn verdict_label(verdict: &Verdict) -> &'static str {
    match verdict {
        Verdict::Matched(_) => "matched",
        Verdict::Fallback(_) => "fallback",
        Verdict::Ambiguous => "ambiguous",
        Verdict::Conflict(_) => "conflict",
        Verdict::Unmatched => "unmatched",
    }
}

fn count_subtitle_tracks(probed: &Probed) -> (u32, u32) {
    let mut de_en_text = 0u32;
    let mut picture_only = 0u32;
    for stream in &probed.streams {
        if stream.kind != StreamKind::Subtitle {
            continue;
        }
        let is_picture = stream
            .codec
            .as_deref()
            .is_some_and(|c| PICTURE_SUBTITLES.contains(&c));
        if is_picture {
            picture_only += 1;
        } else if matches!(
            classify::lang_code(stream.language.as_deref()).as_deref(),
            Some("de" | "en")
        ) {
            de_en_text += 1;
        }
    }
    (de_en_text, picture_only)
}

/// Reads like the label `status` and `remove` print: the show and its
/// position code, so a range and a lesson read as what they are.
fn display_title(set: &SetRow) -> String {
    let code = set
        .season
        .zip(set.episode)
        .and_then(|(s, e)| position_code(set.kind, s, e));
    match (&set.show, code) {
        (Some(show), Some(code)) => format!("{show} {code}"),
        _ => set
            .title
            .clone()
            .or_else(|| set.show.clone())
            .unwrap_or_default(),
    }
}

#[cfg(test)]
#[path = "dry_run_report_tests.rs"]
mod tests;
