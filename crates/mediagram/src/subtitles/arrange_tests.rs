use super::*;

const HOUR: f64 = 3600.0;

/// A WebVTT document of `cues` cues.
fn vtt(cues: usize) -> String {
    let mut text = String::from("WEBVTT\n");
    for n in 0..cues {
        text.push_str(&format!(
            "\n00:00:{:02}.000 --> 00:00:{:02}.500\nline {n}\n",
            n % 60,
            n % 60
        ));
    }
    text
}

fn track(origin: Origin, lang: &str, forced: bool, sdh: bool, cues: usize) -> Extracted {
    Extracted {
        origin,
        lang: lang.to_string(),
        forced,
        sdh,
        default: false,
        codec: "subrip".to_string(),
        vtt: vtt(cues),
    }
}

fn embedded(lang: &str, forced: bool, sdh: bool, cues: usize) -> Extracted {
    track(Origin::Embedded, lang, forced, sdh, cues)
}

fn labels(tracks: &[BundleTrack]) -> Vec<&str> {
    tracks.iter().map(|t| t.label.as_str()).collect()
}

#[test]
fn arcane_shape_is_forced_german_then_german_then_sdh_english() {
    let tracks = arrange(
        vec![
            embedded("en", false, true, 700),
            embedded("de", false, false, 650),
            embedded("de", true, false, 30),
        ],
        0.7 * HOUR,
    );

    assert_eq!(
        labels(&tracks),
        ["German (Forced)", "German", "English (SDH)"]
    );
}

/// The Boardwalk shape: one untitled, unflagged German track with a handful
/// of cues for signs and foreign speech.
#[test]
fn a_sparse_unflagged_track_is_the_forced_one() {
    let tracks = arrange(vec![embedded("de", false, false, 8)], 0.9 * HOUR);

    assert_eq!(labels(&tracks), ["German (Forced)"]);
    assert!(tracks[0].forced);
}

#[test]
fn a_full_unflagged_track_is_the_regular_one() {
    let tracks = arrange(vec![embedded("de", false, false, 600)], HOUR);

    assert_eq!(labels(&tracks), ["German"]);
}

#[test]
fn a_track_far_sparser_than_its_languages_densest_is_forced_even_when_not_rare_per_hour() {
    let tracks = arrange(
        vec![
            embedded("de", false, false, 2000),
            embedded("de", false, false, 400),
        ],
        HOUR,
    );

    assert_eq!(labels(&tracks), ["German (Forced)", "German"]);
}

#[test]
fn density_does_not_apply_to_a_track_that_claims_to_be_sdh() {
    let tracks = arrange(vec![embedded("en", false, true, 5)], HOUR);

    assert_eq!(labels(&tracks), ["English (SDH)"]);
}

#[test]
fn density_does_not_apply_to_a_sidecar() {
    let tracks = arrange(vec![track(Origin::Sidecar, "de", false, false, 5)], HOUR);

    assert_eq!(labels(&tracks), ["German"]);
}

#[test]
fn an_unknown_duration_leaves_only_the_relative_rule() {
    let tracks = arrange(vec![embedded("de", false, false, 5)], 0.0);

    assert_eq!(labels(&tracks), ["German"]);
}

#[test]
fn an_empty_track_is_dropped() {
    assert!(arrange(vec![embedded("de", false, false, 0)], HOUR).is_empty());
}

#[test]
fn a_sidecar_beats_an_embedded_track_of_the_same_kind() {
    let tracks = arrange(
        vec![
            embedded("de", false, false, 600),
            track(Origin::Sidecar, "de", false, false, 500),
        ],
        HOUR,
    );

    assert_eq!(tracks.len(), 1);
    assert_eq!(tracks[0].source, "sidecar");
}

#[test]
fn a_default_embedded_track_beats_a_later_one() {
    let mut first = embedded("de", false, false, 600);
    first.codec = "first".into();
    let mut second = embedded("de", false, false, 610);
    second.codec = "second".into();
    second.default = true;

    let tracks = arrange(vec![first.clone(), second], HOUR);
    assert_eq!(tracks[0].codec, "second");

    // Without a default, the first one stays.
    let mut later = embedded("de", false, false, 610);
    later.codec = "later".into();
    assert_eq!(arrange(vec![first, later], HOUR)[0].codec, "first");
}

#[test]
fn an_unknown_language_is_called_subtitles() {
    let tracks = arrange(vec![track(Origin::Sidecar, "und", false, false, 5)], HOUR);

    assert_eq!(labels(&tracks), ["Subtitles"]);
}
