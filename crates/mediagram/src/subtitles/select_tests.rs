use super::*;
use crate::media::streams::parse_probe;

const ARCANE: &str = include_str!("../../tests/fixtures/ffprobe/subtitles_arcane.json");
const BAND_OF_BROTHERS: &str =
    include_str!("../../tests/fixtures/ffprobe/subtitles_band_of_brothers.json");

fn found(json: &str) -> Found {
    find_wanted(&parse_probe(json).unwrap().streams)
}

/// (language, forced, sdh) of each wanted stream, in stream order.
fn shapes(found: &Found) -> Vec<(&str, bool, bool)> {
    found
        .wanted
        .iter()
        .map(|w| (w.lang.as_str(), w.forced, w.sdh))
        .collect()
}

#[test]
fn arcane_yields_forced_german_german_and_sdh_english_by_flag_and_title() {
    let found = found(ARCANE);

    // Commentary, French and the untagged untitled track are left out. The
    // English SDH track has no hearing_impaired flag; its title says it.
    assert_eq!(
        shapes(&found),
        vec![
            ("de", true, false),
            ("de", false, false),
            ("en", false, true)
        ]
    );
    assert!(found.wanted[0].default);
    assert_eq!(found.pictures, 0);
}

#[test]
fn band_of_brothers_keeps_the_forced_srt_and_counts_the_picture_tracks() {
    let found = found(BAND_OF_BROTHERS);

    assert_eq!(shapes(&found), vec![("de", true, false)]);
    assert_eq!(found.pictures, 4);
}

#[test]
fn an_untagged_track_takes_its_language_from_its_title() {
    let mut probed = parse_probe(ARCANE).unwrap();
    for stream in &mut probed.streams {
        stream.language = None;
    }

    let found = find_wanted(&probed.streams);

    // "German (Forced)", "German" and "English (SDH)" name themselves; the
    // track with no title at all and the French one do not.
    assert_eq!(found.wanted.len(), 3);
}

#[test]
fn a_track_that_claims_forced_and_sdh_is_forced() {
    let mut probed = parse_probe(ARCANE).unwrap();
    for stream in &mut probed.streams {
        stream.hearing_impaired = true;
    }

    let found = find_wanted(&probed.streams);

    assert!(found.wanted[0].forced);
    assert!(!found.wanted[0].sdh);
}

#[test]
fn sdh_is_recognised_as_a_word_not_a_substring() {
    assert!(claims_sdh("english [sdh]"));
    assert!(claims_sdh("deutsch (cc)"));
    assert!(claims_sdh("für hörgeschädigte"));
    assert!(!claims_sdh("crosscut"));
}
