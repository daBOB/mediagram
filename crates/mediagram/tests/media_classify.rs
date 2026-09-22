//! Pins `media::classify`'s pure helpers at cases its own unit tests
//! (`crates/mediagram/src/media/classify.rs`) don't cover: odd heights for
//! `quality_from_frame`, a mixed HDR/DV combination for `hdr_from_stream`,
//! and the empty/whitespace/case edges of `lang_code` and
//! `container_from_ext`.

use std::path::Path;

use mediagram::media;

#[test]
fn quality_from_frame_for_odd_16_9_derived_heights() {
    // Heights that don't line up with any real resolution, given a 16:9
    // frame where height alone decides the bucket. (Values that reduce to
    // a real resolution — e.g. 720p's 1280x720 — are already covered by
    // classify.rs's own `quality_thresholds` and are skipped here to avoid
    // re-asserting the same call.)
    let q = |h: u32| media::classify::quality_from_frame(h * 16 / 9, h);
    assert_eq!(q(2159), "2160p");
    assert_eq!(q(1081), "1080p");
    assert_eq!(q(479), "480p");
    assert_eq!(q(399), "SD");
    assert_eq!(q(600), "720p");
}

#[test]
fn hdr_dovi_configuration_record_wins_over_smpte2084_transfer() {
    // DOVI side data beats an HDR10-shaped color_transfer, not just a
    // non-HDR one (classify.rs's own `hdr_dv_wins_over_transfer` only
    // pairs DOVI/Dolby-Vision side data with a non-HDR transfer).
    let result =
        media::classify::hdr_from_stream(Some("smpte2084"), &["DOVI configuration record"]);
    assert_eq!(result, "DV");
}

#[test]
fn lang_code_uppercase_2_char_is_lowercased() {
    // A 2-character code skips the 3-letter lookup table, but still gets
    // lowercased.
    let result = media::classify::lang_code(Some("EN"));
    assert_eq!(result, Some("en".into()));
}

#[test]
fn lang_code_empty_string_maps_to_none() {
    let result = media::classify::lang_code(Some(""));
    assert_eq!(result, None);
}

#[test]
fn lang_code_whitespace_only_maps_to_none() {
    // Trimmed to empty before the empty-string check runs.
    let result = media::classify::lang_code(Some("   "));
    assert_eq!(result, None);
}

#[test]
fn container_from_mixed_case_extension_is_lowercased() {
    let result = media::classify::container_from_ext(Path::new("movie.MpEg"));
    assert_eq!(result, "mpeg");
}
