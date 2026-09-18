//! Pure classification helpers: frame size to a caption `q` label, HDR
//! transfer/side-data to a `hdr` label, ISO 639-2 to 639-1 language codes,
//! and file extension to a container name. No I/O, easy to unit test.

use std::path::Path;

/// The quality label for a frame, from whichever dimension carries the format.
///
/// Height alone undersells anything wider than 16:9. A 2.39:1 film is stored
/// 1920x804, and 804 reads as 720p though every pixel across is 1080p. Width
/// alone gets 4:3 wrong the same way, so the label comes from the greater of
/// the real height and the height a 16:9 frame this wide would have.
///
/// Buckets are nearest-common rather than exact, so an odd encode (1076p)
/// still classifies sensibly.
pub fn quality_from_frame(width: u32, height: u32) -> &'static str {
    match height.max(width * 9 / 16) {
        h if h >= 2000 => "2160p",
        h if h >= 1300 => "1440p",
        h if h >= 900 => "1080p",
        h if h >= 600 => "720p",
        h if h >= 400 => "480p",
        _ => "SD",
    }
}

/// Classifies HDR variant from ffprobe's `color_transfer` and any side-data
/// type names attached to the video stream. Dolby Vision side data wins
/// over the base transfer characteristic since DV streams often carry an
/// SDR-compatible `color_transfer` as their base layer.
pub fn hdr_from_stream(color_transfer: Option<&str>, side_data_types: &[&str]) -> &'static str {
    let has_dovi = side_data_types.iter().any(|t| {
        t.eq_ignore_ascii_case("DOVI configuration record")
            || t.eq_ignore_ascii_case("Dolby Vision")
    });
    if has_dovi {
        return "DV";
    }
    match color_transfer {
        Some(t) if t.eq_ignore_ascii_case("smpte2084") => "HDR10",
        Some(t) if t.eq_ignore_ascii_case("arib-std-b67") => "HLG",
        _ => "SDR",
    }
}

/// Maps an ISO 639-2 (or already-639-1) language tag to its 639-1 code.
/// `None`/`"und"` (undetermined) map to `None`; unknown codes pass through
/// unchanged so unusual languages are not silently dropped.
pub fn lang_code(tag: Option<&str>) -> Option<String> {
    let tag = tag?.trim();
    if tag.is_empty() || tag.eq_ignore_ascii_case("und") {
        return None;
    }
    if tag.len() == 2 {
        return Some(tag.to_ascii_lowercase());
    }
    let lower = tag.to_ascii_lowercase();
    let mapped = match lower.as_str() {
        "eng" => "en",
        "deu" | "ger" => "de",
        "fra" | "fre" => "fr",
        "spa" => "es",
        "ita" => "it",
        "jpn" => "ja",
        "por" => "pt",
        "rus" => "ru",
        "kor" => "ko",
        "zho" | "chi" => "zh",
        "nld" | "dut" => "nl",
        "swe" => "sv",
        "dan" => "da",
        "nor" => "no",
        "fin" => "fi",
        "pol" => "pl",
        "tur" => "tr",
        "ara" => "ar",
        "hin" => "hi",
        other => return Some(other.to_string()),
    };
    Some(mapped.to_string())
}

/// Lowercased file extension, used as the caption `container` field.
pub fn container_from_ext(path: &Path) -> String {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|e| e.to_ascii_lowercase())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn quality_thresholds() {
        // 16:9, where height alone was always right.
        assert_eq!(quality_from_frame(3840, 2160), "2160p");
        assert_eq!(quality_from_frame(2560, 1440), "1440p");
        assert_eq!(quality_from_frame(1920, 1080), "1080p");
        assert_eq!(quality_from_frame(1280, 720), "720p");
        assert_eq!(quality_from_frame(854, 480), "480p");
        assert_eq!(quality_from_frame(640, 360), "SD");

        // Scope ratios: every pixel across is 1080p, and the label must say so.
        // These are real frames from the library — 2.20:1 and 2.39:1.
        assert_eq!(quality_from_frame(1918, 872), "1080p");
        assert_eq!(quality_from_frame(1920, 804), "1080p");
        assert_eq!(quality_from_frame(3840, 1600), "2160p");

        // 4:3 is the other direction: narrow for its height, and judging on
        // width alone would call this 720p.
        assert_eq!(quality_from_frame(1440, 1080), "1080p");

        // Width unknown falls back to height.
        assert_eq!(quality_from_frame(0, 1080), "1080p");
        assert_eq!(quality_from_frame(0, 64), "SD");
    }

    #[test]
    fn hdr_dv_wins_over_transfer() {
        assert_eq!(
            hdr_from_stream(Some("bt709"), &["DOVI configuration record"]),
            "DV"
        );
        assert_eq!(hdr_from_stream(Some("smpte2084"), &["Dolby Vision"]), "DV");
    }

    #[test]
    fn hdr_hdr10_and_hlg() {
        assert_eq!(hdr_from_stream(Some("smpte2084"), &[]), "HDR10");
        assert_eq!(hdr_from_stream(Some("SMPTE2084"), &[]), "HDR10");
        assert_eq!(hdr_from_stream(Some("arib-std-b67"), &[]), "HLG");
    }

    #[test]
    fn hdr_defaults_to_sdr() {
        assert_eq!(hdr_from_stream(Some("bt709"), &[]), "SDR");
        assert_eq!(hdr_from_stream(None, &[]), "SDR");
    }

    #[test]
    fn lang_mapping() {
        assert_eq!(lang_code(Some("eng")), Some("en".into()));
        assert_eq!(lang_code(Some("deu")), Some("de".into()));
        assert_eq!(lang_code(Some("ger")), Some("de".into()));
        assert_eq!(lang_code(Some("fra")), Some("fr".into()));
        assert_eq!(lang_code(Some("zho")), Some("zh".into()));
        assert_eq!(lang_code(Some("chi")), Some("zh".into()));
        assert_eq!(lang_code(Some("en")), Some("en".into()));
        assert_eq!(lang_code(Some("und")), None);
        assert_eq!(lang_code(None), None);
        assert_eq!(lang_code(Some("xyz")), Some("xyz".into()));
    }

    #[test]
    fn container_from_extension() {
        assert_eq!(container_from_ext(Path::new("movie.MKV")), "mkv");
        assert_eq!(container_from_ext(Path::new("movie.mp4")), "mp4");
        assert_eq!(container_from_ext(Path::new("movie")), "");
    }
}
