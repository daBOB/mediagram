//! Turning extracted tracks into the bundle's tracks: which of them is the
//! forced one, which duplicates lose, what each is called, in what order.

use std::collections::HashMap;

use mlib_spec::subtitle_bundle::BundleTrack;

/// Up to this many cues an hour, an embedded track nobody flagged is the
/// forced one. Measured on German-dub episodes whose single German track
/// carries only signs and foreign speech: 1 to 86 cues an hour, against at
/// least 442 for a full track. A near-silent film with only a full track
/// would be taken for forced; the flag and the title are checked first, and
/// a denser track of the same language is what settles the rest.
pub const FORCED_MAX_CUES_PER_HOUR: f64 = 120.0;
/// Or up to this share of the densest track in the same language.
pub const FORCED_MAX_SHARE_OF_DENSEST: f64 = 0.25;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Origin {
    Embedded,
    Sidecar,
}

/// One track read out to WebVTT, before it is judged.
#[derive(Debug, Clone)]
pub struct Extracted {
    pub origin: Origin,
    pub lang: String,
    pub forced: bool,
    pub sdh: bool,
    pub default: bool,
    pub codec: String,
    pub vtt: String,
}

pub fn cue_count(vtt: &str) -> usize {
    vtt.lines().filter(|line| line.contains(" --> ")).count()
}

/// Settles forced and SDH, drops duplicates and empty tracks, and orders
/// what is left: by language, the forced track before the regular one.
pub fn arrange(tracks: Vec<Extracted>, duration_s: f64) -> Vec<BundleTrack> {
    let tracks: Vec<(Extracted, usize)> = tracks
        .into_iter()
        .map(|t| {
            let cues = cue_count(&t.vtt);
            (t, cues)
        })
        .filter(|(_, cues)| *cues > 0)
        .collect();
    let mut densest: HashMap<String, usize> = HashMap::new();
    for (t, cues) in &tracks {
        let entry = densest.entry(t.lang.clone()).or_default();
        *entry = (*entry).max(*cues);
    }
    let mut settled: Vec<Extracted> = Vec::new();
    for (mut track, cues) in tracks {
        if track.origin == Origin::Embedded && !track.forced && !track.sdh {
            track.forced = sparse(cues, duration_s, densest[&track.lang]);
        }
        let key = |t: &Extracted| (t.lang.clone(), t.forced, t.sdh);
        match settled.iter_mut().find(|other| key(other) == key(&track)) {
            Some(slot) if rank(&track) > rank(slot) => *slot = track,
            Some(_) => {}
            None => settled.push(track),
        }
    }
    let mut out = settled;
    out.sort_by(|a, b| (&a.lang, !a.forced, a.sdh).cmp(&(&b.lang, !b.forced, b.sdh)));
    out.into_iter().map(into_bundle_track).collect()
}

fn sparse(cues: usize, duration_s: f64, densest: usize) -> bool {
    let per_hour =
        duration_s > 0.0 && cues as f64 / (duration_s / 3600.0) <= FORCED_MAX_CUES_PER_HOUR;
    per_hour || cues as f64 <= FORCED_MAX_SHARE_OF_DENSEST * densest as f64
}

/// A sidecar beats an embedded track, and a `default` embedded track beats a
/// later one (ties keep the first).
fn rank(t: &Extracted) -> (bool, bool) {
    (t.origin == Origin::Sidecar, t.default)
}

pub fn label(lang: &str, forced: bool, sdh: bool) -> String {
    let name = match lang {
        "de" => "German",
        "en" => "English",
        _ => "Subtitles",
    };
    match (forced, sdh) {
        (true, _) => format!("{name} (Forced)"),
        (_, true) => format!("{name} (SDH)"),
        _ => name.to_string(),
    }
}

fn into_bundle_track(t: Extracted) -> BundleTrack {
    BundleTrack {
        label: label(&t.lang, t.forced, t.sdh),
        source: match t.origin {
            Origin::Embedded => "embedded",
            Origin::Sidecar => "sidecar",
        }
        .to_string(),
        lang: t.lang,
        forced: t.forced,
        sdh: t.sdh,
        codec: t.codec,
        vtt: t.vtt,
    }
}

#[cfg(test)]
#[path = "arrange_tests.rs"]
mod tests;
