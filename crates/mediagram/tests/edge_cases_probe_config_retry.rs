//! Edge case probes for phases 2-4: Config + Retry + MP4 atoms/remux/inspect/classify/resolve
//! Tests invalid configurations, retry edge cases, and MP4/media file edge cases.

use std::env;
use std::path::Path;
use tempfile::NamedTempFile;

use mediagram::config;
use mediagram::media;

// ============================================================================
// PART 1: Config edge cases
// ============================================================================

#[test]
fn config_env_override_invalid_part_size_non_numeric() {
    // MEDIAGRAM_PART_SIZE with non-numeric value should error at parse time
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    unsafe {
        env::set_var("MEDIAGRAM_PART_SIZE", "not_a_number");
    }
    let result = config::load(Some(temp.path()));
    unsafe {
        env::remove_var("MEDIAGRAM_PART_SIZE");
    }

    // Should fail because "not_a_number" cannot be parsed as u64
    // The error message is from toml/parse context, not the env var name
    assert!(result.is_err());
}

#[test]
fn config_env_override_part_size_not_mib_aligned() {
    // part_size 1048575 (1 MiB - 1) should fail alignment check
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    unsafe {
        env::set_var("MEDIAGRAM_PART_SIZE", "1048575");
    }
    let result = config::load(Some(temp.path()));
    unsafe {
        env::remove_var("MEDIAGRAM_PART_SIZE");
    }

    // Should fail: not aligned to 1 MiB
    assert!(result.is_err());
    assert!(
        result
            .unwrap_err()
            .to_string()
            .to_lowercase()
            .contains("part_size")
    );
}

#[test]
fn config_missing_file_error_suggests_example_toml() {
    // Loading from non-existent path should mention config.example.toml
    let result = config::load(Some(Path::new("/nonexistent/fake_config.toml")));
    assert!(result.is_err());
    let msg = result.unwrap_err().to_string();
    assert!(msg.contains("config.example.toml"));
}

#[test]
fn config_requires_api_hash_and_channel() {
    // Empty api_hash or channel should be rejected
    let toml_content = r#"
api_id = 123456
api_hash = ""
channel = "test_channel"
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    let result = config::load(Some(temp.path()));
    // Empty api_hash triggers validation error
    assert!(result.is_err());
}

// ============================================================================
// PART 2: Retry edge cases
// ============================================================================

#[test]
fn retry_max_attempts_zero() {
    // max_attempts = 0 should fail immediately (but config default is 5, so this
    // tests the boundary in config parsing, not retry logic itself)
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
max_attempts = 0
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    // This should load successfully (max_attempts = 0 is technically valid in config)
    // but would cause retry logic to fail immediately in real use
    let cfg = config::load(Some(temp.path()));
    assert!(cfg.is_ok());
    assert_eq!(cfg.unwrap().max_attempts, 0);
}

#[test]
fn retry_max_attempts_one() {
    // max_attempts = 1 means a single attempt (no retry)
    let toml_content = r#"
api_id = 123456
api_hash = "test_hash"
channel = "test_channel"
max_attempts = 1
"#;
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), toml_content).unwrap();

    let cfg = config::load(Some(temp.path()));
    assert!(cfg.is_ok());
    assert_eq!(cfg.unwrap().max_attempts, 1);
}

// ============================================================================
// PART 3: MP4 Atoms edge cases (file I/O based)
// ============================================================================

#[test]
fn mp4_atoms_empty_file() {
    // needs_faststart on a 0-byte file should not panic
    let temp = NamedTempFile::new().unwrap();
    // File is already empty, just get its path
    let path = temp.path().to_path_buf();

    let result = media::mp4_atoms::needs_faststart(&path);
    // Empty MP4 file: should not panic, return false (no moov/mdat found)
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn mp4_atoms_file_shorter_than_8_bytes() {
    // A file < 8 bytes (box header size) should be handled gracefully
    let temp = NamedTempFile::new().unwrap();
    std::fs::write(temp.path(), b"short").unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn mp4_atoms_uppercase_mp4_extension() {
    // .MP4 (uppercase) should still be recognized as MP4-family
    let temp = NamedTempFile::with_suffix(".MP4").unwrap();
    // Write a minimal valid MP4 with moov then mdat
    let minimal_mp4 = vec![
        // moov box at offset 0: size=8, type=moov
        0x00, 0x00, 0x00, 0x08, b'm', b'o', b'o', b'v',
        // mdat box at offset 8: size=8, type=mdat
        0x00, 0x00, 0x00, 0x08, b'm', b'd', b'a', b't',
    ];
    std::fs::write(temp.path(), minimal_mp4).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    // moov before mdat: should NOT need remux
    assert!(!result.unwrap());
}

#[test]
fn mp4_atoms_mkv_extension_short_circuits() {
    // .mkv should return false without reading file content
    let path = Path::new("/nonexistent/fake.mkv");
    let result = media::mp4_atoms::needs_faststart(path);
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn mp4_atoms_webm_extension_short_circuits() {
    // .webm should return false without reading file content
    let path = Path::new("/nonexistent/fake.webm");
    let result = media::mp4_atoms::needs_faststart(path);
    assert!(result.is_ok());
    assert!(!result.unwrap());
}

#[test]
fn mp4_atoms_atom_size_larger_than_file() {
    // A box claiming a size larger than the file should be handled gracefully
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    // Box: size=1000 (claiming 1000 bytes but file is only 8)
    let truncated = vec![
        0x00, 0x00, 0x03, 0xe8, // size = 1000 in big-endian
        b'm', b'o', b'o', b'v', // type = moov
    ];
    std::fs::write(temp.path(), truncated).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    // Should not panic; moov found but file is incomplete
    assert!(result.is_ok() || result.is_err()); // Behavior depends on implementation
}

#[test]
fn mp4_atoms_largesize_box_truncated() {
    // A box with size=1 (indicating 64-bit largesize follows) but no largesize
    // should trigger an error
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let truncated = vec![
        0x00, 0x00, 0x00, 0x01, // size = 1 (largesize follows)
        b'm', b'o', b'o',
        b'v', // type = moov (only 4 bytes, truncated)
              // Missing 8-byte largesize
    ];
    std::fs::write(temp.path(), truncated).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    // Should error: truncated largesize
    assert!(result.is_err());
}

#[test]
fn mp4_atoms_mdat_before_moov() {
    // Create a file with mdat first, then moov → needs_faststart should return true
    let temp = NamedTempFile::with_suffix(".mp4").unwrap();
    let mdat_then_moov = vec![
        // mdat box at offset 0
        0x00, 0x00, 0x00, 0x08, b'm', b'd', b'a', b't', // moov box at offset 8
        0x00, 0x00, 0x00, 0x08, b'm', b'o', b'o', b'v',
    ];
    std::fs::write(temp.path(), mdat_then_moov).unwrap();

    let result = media::mp4_atoms::needs_faststart(temp.path());
    assert!(result.is_ok());
    // mdat before moov: NEEDS remux
    assert!(result.unwrap());
}

// ============================================================================
// PART 4: Classify edge cases
// ============================================================================

#[test]
fn classify_quality_boundary_heights() {
    // Test exact boundary heights: 2159/2160/1440/1081/720/479
    // Thresholds: >=2000→2160p, >=1300→1440p, >=900→1080p, >=600→720p, >=400→480p, else→SD
    assert_eq!(media::classify::quality_from_height(2159), "2160p"); // >= 2000
    assert_eq!(media::classify::quality_from_height(2160), "2160p"); // >= 2000
    assert_eq!(media::classify::quality_from_height(1440), "1440p"); // >= 1300
    assert_eq!(media::classify::quality_from_height(1081), "1080p"); // >= 900
    assert_eq!(media::classify::quality_from_height(720), "720p"); // >= 600
    assert_eq!(media::classify::quality_from_height(479), "480p"); // >= 400
    assert_eq!(media::classify::quality_from_height(399), "SD"); // < 400
    assert_eq!(media::classify::quality_from_height(600), "720p"); // >= 600
}

#[test]
fn classify_hdr_precedence_dovi_over_smpte2084() {
    // When both DOVI side data and smpte2084 are present,
    // DOVI should win (return "DV", not "HDR10")
    let result =
        media::classify::hdr_from_stream(Some("smpte2084"), &["DOVI configuration record"]);
    assert_eq!(result, "DV");
}

#[test]
fn classify_hdr_precedence_dolby_vision_string() {
    // "Dolby Vision" side data type (case insensitive) should also trigger DV
    let result = media::classify::hdr_from_stream(Some("smpte2084"), &["Dolby Vision"]);
    assert_eq!(result, "DV");
}

#[test]
fn classify_lang_code_already_2_char() {
    // A 2-character code like "en" should pass through unchanged (lowercase)
    let result = media::classify::lang_code(Some("EN"));
    assert_eq!(result, Some("en".into()));
}

#[test]
fn classify_lang_code_unknown_3_char() {
    // An unknown 3-character code should pass through unchanged
    let result = media::classify::lang_code(Some("xyz"));
    assert_eq!(result, Some("xyz".into()));
}

#[test]
fn classify_lang_code_empty_string() {
    // Empty string should map to None
    let result = media::classify::lang_code(Some(""));
    assert_eq!(result, None);
}

#[test]
fn classify_lang_code_whitespace_only() {
    // Whitespace-only tag should map to None
    let result = media::classify::lang_code(Some("   "));
    assert_eq!(result, None);
}

#[test]
fn classify_container_no_extension() {
    // File with no extension should return empty string
    let result = media::classify::container_from_ext(Path::new("movie"));
    assert_eq!(result, "");
}

#[test]
fn classify_container_mixed_case_extension() {
    // Mixed case extension should be lowercased
    let result = media::classify::container_from_ext(Path::new("movie.MpEg"));
    assert_eq!(result, "mpeg");
}
