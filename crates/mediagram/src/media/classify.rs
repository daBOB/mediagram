// Not wired into any command yet; the add command calls these once inspect
// results are available.
#![allow(dead_code)]

//! Pure classification helpers: pixel height to a caption `q` label, HDR
//! transfer/side-data to a `hdr` label, ISO 639-2 to 639-1 language codes,
//! and file extension to a container name. No I/O, easy to unit test.

use std::path::Path;

/// Maps a video stream's pixel height to the closest standard quality label.
/// Uses the nearest common bucket rather than exact equality so odd
/// encodes (e.g. 1076p) still classify sensibly.
pub fn quality_from_height(h: u32) -> &'static str {
    match h {
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
        assert_eq!(quality_from_height(2160), "2160p");
        assert_eq!(quality_from_height(2000), "2160p");
        assert_eq!(quality_from_height(1440), "1440p");
        assert_eq!(quality_from_height(1080), "1080p");
        assert_eq!(quality_from_height(900), "1080p");
        assert_eq!(quality_from_height(720), "720p");
        assert_eq!(quality_from_height(480), "480p");
        assert_eq!(quality_from_height(360), "SD");
        assert_eq!(quality_from_height(64), "SD");
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
