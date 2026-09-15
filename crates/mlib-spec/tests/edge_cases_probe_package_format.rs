// Comprehensive edge case probes for mlib-spec::package module.
// Tests explore boundary conditions, date calculations, filename generation,
// pointer validation, and manifest handling that may not be covered by
// the primary test suite.

use mlib_spec::package::{
    CIPHER, LatestPointer, PACKAGE_FORMAT, PackageManifest, PointerError, PosterEntry,
    associated_data, package_file_name, pointer_is_readable, poster_key_is_valid,
};

/// A 32-byte digest seeded by one byte, so a probe can vary the bytes that
/// reach a file name without caring about the rest.
fn digest(seed: u8) -> [u8; 32] {
    [seed; 32]
}

// ============================================================================
// civil_from_unix probes (via package_file_name)
// ============================================================================

#[test]
fn package_file_name_epoch_zero_is_1970_01_01() {
    // Unix epoch: seconds=0 should map to 1970-01-01
    let name = package_file_name(0, &digest(b'a'));
    println!("Epoch 0 filename: {}", name);
    assert!(name.contains("19700101"), "Epoch should produce 1970-01-01");
}

#[test]
fn package_file_name_clamps_pre_epoch_timestamps_to_the_epoch() {
    // A pre-epoch timestamp is clamped rather than rendered, because a
    // negative year formats as a variable-width, signed number and would
    // break the fixed `YYYYMMDD` shape a reader parses. It cannot arise from
    // a real export, and `pointer_is_readable` rejects `created_at < 0`
    // outright, so clamping here only guarantees a well-formed name.
    let name = package_file_name(-86_400, &digest(b'b'));
    assert!(name.contains("19700101"), "pre-epoch clamps to the epoch");
    assert!(!name.contains('-') || name.matches('-').count() == 1);
}

#[test]
fn package_file_name_stays_well_formed_far_before_the_epoch() {
    let name = package_file_name(-315_619_200, &digest(b'c'));
    assert!(name.starts_with("prebuilt_mediagram_db_19700101-"));
}

#[test]
fn package_file_name_leap_year_feb_29_2024() {
    // 2024-02-29 exists (leap year)
    // 2024 is a leap year. Feb 29, 2024 00:00:00 UTC
    // Need to calculate unix timestamp for 2024-02-29 00:00:00 UTC
    // Using known value: 1709251200
    let name = package_file_name(1_709_164_800, &digest(b'd'));
    println!("Leap day 2024: {}", name);
    assert!(name.contains("20240229"), "Should handle leap day 2024");
}

#[test]
fn package_file_name_leap_year_feb_29_2000() {
    // 2000 is a leap year (divisible by 400).
    // Feb 29, 2000 00:00:00 UTC = 951782400 (verified against Python datetime)
    let name = package_file_name(951_782_400, &digest(b'e'));
    println!("Leap day 2000: {}", name);
    assert!(name.contains("20000229"), "Should handle leap day 2000");
}

#[test]
fn package_file_name_century_boundary_1999_12_31() {
    // Last day of 1999: 1999-12-31 23:59:59 UTC
    // 946684799 = 1999-12-31 23:59:59
    let name = package_file_name(946_684_799, &digest(b'f'));
    println!("1999-12-31 23:59:59: {}", name);
    assert!(name.contains("19991231"), "Should handle end of 1999");
}

#[test]
fn package_file_name_century_boundary_2000_01_01() {
    // First second of Y2K: 2000-01-01 00:00:00 UTC
    // 946684800 = 2000-01-01 00:00:00
    let name = package_file_name(946_684_800, &digest(b'a'));
    println!("2000-01-01 00:00:00: {}", name);
    assert!(name.contains("20000101"), "Should handle Y2K boundary");
}

#[test]
fn package_file_name_millennium_boundary_1999_2000() {
    // Compare dates around millennium
    let name_1999 = package_file_name(946_684_799, &digest(b'a'));
    let name_2000 = package_file_name(946_684_800, &digest(b'a'));
    println!("1999 last sec: {}", name_1999);
    println!("2000 first sec: {}", name_2000);
    assert!(name_1999.contains("1999"), "1999");
    assert!(name_2000.contains("2000"), "2000");
}

#[test]
fn package_file_name_future_century_boundary_2100_03_01() {
    // 2100 is NOT a leap year (divisible by 100 but not 400).
    // So 2100-02-28 is the last day of Feb.
    // 2100-03-01 00:00:00 UTC = 4107542400 (verified against Python datetime)
    let name = package_file_name(4_107_542_400, &digest(b'b'));
    println!("2100-03-01: {}", name);
    assert!(name.contains("21000301"), "Should handle 2100");
}

#[test]
fn package_file_name_midnight_utc_boundary() {
    // Second 0 of a day: midnight UTC
    let midnight = 1_781_568_000; // Some arbitrary midnight
    let one_sec_before = midnight - 1;
    let one_sec_after = midnight + 1;

    let name_before = package_file_name(one_sec_before, &digest(b'x'));
    let name_at = package_file_name(midnight, &digest(b'x'));
    let name_after = package_file_name(one_sec_after, &digest(b'x'));

    println!("Before midnight: {}", name_before);
    println!("At midnight: {}", name_at);
    println!("After midnight: {}", name_after);

    // Before and at might differ; at and after should be same date
    assert_ne!(name_before, name_at, "Day boundary should change date");
    assert_eq!(name_at, name_after, "Same calendar day");
}

#[test]
fn package_file_name_last_second_of_day() {
    // 23:59:59 UTC = 86399 seconds into the day
    // Use timestamp where we know the day
    let midnight = 1_781_568_000;
    let last_sec_of_day = midnight + 86_399; // 23:59:59
    let first_sec_of_next = midnight + 86_400; // Next midnight

    let name_last = package_file_name(last_sec_of_day, &digest(b'y'));
    let name_next = package_file_name(first_sec_of_next, &digest(b'y'));

    println!("23:59:59: {}", name_last);
    println!("Next midnight: {}", name_next);
    assert_ne!(name_last, name_next, "Day boundary at midnight");
}

#[test]
fn package_file_name_i64_max_produces_some_date() {
    // i64::MAX = 9223372036854775807
    // This is way in the future (year 292277026596)
    // The function should not panic, even with extreme values
    let name = package_file_name(i64::MAX, &digest(0x7f));
    println!("i64::MAX filename: {}", name);
    assert!(name.starts_with("prebuilt_mediagram_db_"));
    assert!(name.ends_with(".tar.gz.enc"));
}

#[test]
fn package_file_name_i64_min_produces_some_date() {
    // i64::MIN = -9223372036854775808
    // This is way in the past (year -292277022656)
    // The function should not panic
    let name = package_file_name(i64::MIN, &digest(0x80));
    println!("i64::MIN filename: {}", name);
    assert!(name.starts_with("prebuilt_mediagram_db_"));
    assert!(name.ends_with(".tar.gz.enc"));
}

// ============================================================================
// package_file_name with edge case hashes
// ============================================================================

#[test]
fn package_file_name_uses_only_the_first_four_digest_bytes() {
    // The helper takes `&[u8; 32]`, so a short, empty or non-hex hash is not
    // expressible: the earlier probes for those cases were removed when the
    // signature changed from a string to a digest. What remains is the
    // truncation boundary, which is still real.
    let mut a = [0u8; 32];
    let mut b = [0u8; 32];
    a[..4].copy_from_slice(&[0xde, 0xad, 0xbe, 0xef]);
    b[..4].copy_from_slice(&[0xde, 0xad, 0xbe, 0xef]);
    b[4] = 0xff; // differs only past the truncation point
    assert_eq!(
        package_file_name(1_781_568_000, &a),
        package_file_name(1_781_568_000, &b)
    );
    assert!(package_file_name(1_781_568_000, &a).contains("deadbeef"));
}

#[test]
fn a_digest_cannot_carry_a_path_separator_into_a_file_name() {
    // Two probes lived here that fed `package_file_name` a non-hex string and
    // a string containing `/` and `\\`, and asserted the function copied them
    // through. That was a real hole: the name reaches a published path. The
    // signature now takes `&[u8; 32]`, so those inputs cannot be expressed and
    // every produced name is hex.
    let name = package_file_name(1_781_568_000, &digest(0xff));
    let hash_part = name
        .trim_start_matches("prebuilt_mediagram_db_")
        .trim_end_matches(".tar.gz.enc");
    assert!(
        hash_part.chars().all(|c| c.is_ascii_hexdigit() || c == '-'),
        "file name must be hex and dashes only: {name}"
    );
    assert!(!name.contains('/') && !name.contains('\\') && !name.contains(".."));
}

#[test]
fn file_names_are_always_lowercase_hex() {
    // Case handling used to depend on what the caller passed as a string.
    // With a digest, `hex::encode` decides, and it is always lowercase, so a
    // name can never differ only by case on a case-insensitive file system.
    let mut d = [0u8; 32];
    d[..4].copy_from_slice(&[0xde, 0xad, 0xbe, 0xef]);
    let name = package_file_name(1_781_568_000, &d);
    assert!(name.contains("deadbeef"));
    assert_eq!(name, name.to_lowercase());
}

#[test]
fn poster_key_valid_ascii_lowercase_digits_format() {
    // Documented valid format: source-kind-id where all lowercase/digits
    assert!(poster_key_is_valid("tmdb-movie-693134"));
    assert!(poster_key_is_valid("tmdb-tv-550"));
    assert!(poster_key_is_valid("tvdb-series-12345"));
    assert!(poster_key_is_valid("a-b-1"));
}

#[test]
fn poster_key_invalid_unicode_digits() {
    // Arabic-Indic digits (U+0660–U+0669): ٠١٢٣٤٥٦٧٨٩
    // Should be rejected because they're not ASCII digits
    assert!(!poster_key_is_valid("tmdb-movie-٦٩٣١٣٤"));
}

#[test]
fn poster_key_invalid_fullwidth_digits() {
    // Full-width digits (U+FF10–U+FF19): １２３４５６７８９
    // Should be rejected
    assert!(!poster_key_is_valid("tmdb-movie-６９３１３４"));
}

#[test]
fn poster_key_invalid_fullwidth_ascii() {
    // Full-width ASCII (U+FF41–U+FF5A for a–z)
    // Should be rejected
    assert!(!poster_key_is_valid("ｔｍｄｂ-ｍｏｖｉｅ-693134"));
}

#[test]
fn poster_key_invalid_leading_zeros_in_id() {
    // Leading zeros in the ID part: should be accepted
    // (the spec says "digits", not "positive non-zero integer")
    assert!(poster_key_is_valid("tmdb-movie-00693134"));
    assert!(poster_key_is_valid("tmdb-movie-0"));
    assert!(poster_key_is_valid("tmdb-movie-00000000"));
}

#[test]
fn poster_key_invalid_sign_in_id() {
    // ID with + or - sign
    assert!(!poster_key_is_valid("tmdb-movie-+693134"));
    assert!(!poster_key_is_valid("tmdb-movie--693134"));
    assert!(!poster_key_is_valid("tmdb-movie-693134+"));
}

#[test]
fn poster_key_invalid_hex_in_id() {
    // Hex letters a-f in ID (not valid for ASCII digits)
    assert!(!poster_key_is_valid("tmdb-movie-deadbeef"));
    assert!(!poster_key_is_valid("tmdb-movie-a1b2c3"));
}

#[test]
fn poster_key_very_long_id() {
    // Very long but valid ID
    let long_id = format!("tmdb-movie-{}", "1".repeat(10000));
    assert!(
        poster_key_is_valid(&long_id),
        "Long digit string should be valid"
    );
}

#[test]
fn poster_key_valid_but_absurd_combinations() {
    // Valid format but nonsensical
    assert!(poster_key_is_valid("zzz-zzz-999999999"));
    assert!(poster_key_is_valid("a-a-1"));
    assert!(poster_key_is_valid("source-kind-1234567890"));
}

#[test]
fn poster_key_missing_parts() {
    // Missing parts (only 1 or 2 dashes)
    assert!(!poster_key_is_valid("tmdb-movie"));
    assert!(!poster_key_is_valid("tmdb"));
    assert!(!poster_key_is_valid("tmdb-movie-123-extra"));
}

#[test]
fn poster_key_uppercase_in_source_or_kind() {
    // Uppercase letters in source/kind (should be rejected)
    assert!(!poster_key_is_valid("TMDB-movie-693134"));
    assert!(!poster_key_is_valid("tmdb-MOVIE-693134"));
    assert!(!poster_key_is_valid("Tmdb-Movie-693134"));
}

#[test]
fn poster_key_spaces_or_special_chars() {
    // Spaces and special chars
    assert!(!poster_key_is_valid("tmdb-movie-693 134"));
    assert!(!poster_key_is_valid("tmdb-movie-693_134"));
    assert!(!poster_key_is_valid("tmdb-movie.693134"));
    assert!(!poster_key_is_valid("tmdb movie 693134"));
}

#[test]
fn poster_key_empty_parts() {
    // Empty source, kind, or id
    assert!(!poster_key_is_valid("-movie-693134"));
    assert!(!poster_key_is_valid("tmdb--693134"));
    assert!(!poster_key_is_valid("tmdb-movie-"));
    assert!(!poster_key_is_valid("tmdb-movie- "));
}

// ============================================================================
// associated_data probes
// ============================================================================

#[test]
fn associated_data_key_id_with_quotes() {
    // key_id containing double quotes
    let mut p = test_pointer();
    p.key_id = "abc\"def".into();

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("Key ID with quotes: {}", json_str);

    // Should be valid JSON (quotes escaped)
    let _parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
}

#[test]
fn associated_data_key_id_with_backslashes() {
    // key_id containing backslashes
    let mut p = test_pointer();
    p.key_id = "abc\\def".into();

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("Key ID with backslash: {}", json_str);

    // Should be valid JSON (backslashes escaped)
    let _parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
}

#[test]
fn associated_data_key_id_with_newlines() {
    // key_id containing newlines
    let mut p = test_pointer();
    p.key_id = "abc\ndef\nghi".into();

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("Key ID with newlines: {}", json_str);

    // Should be valid JSON
    let _parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
}

#[test]
fn associated_data_key_id_with_non_ascii() {
    // key_id with non-ASCII UTF-8
    let mut p = test_pointer();
    p.key_id = "café_日本".into();

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("Key ID with UTF-8: {}", json_str);

    // Should be valid JSON
    let _parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
}

#[test]
fn associated_data_extreme_created_at_min() {
    // Extreme created_at value: i64::MIN
    let mut p = test_pointer();
    p.created_at = i64::MIN;

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("created_at = i64::MIN: {}", json_str);

    let parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
    assert_eq!(
        parsed["created_at"].as_i64().unwrap(),
        i64::MIN,
        "Should preserve i64::MIN"
    );
}

#[test]
fn associated_data_extreme_created_at_max() {
    // Extreme created_at value: i64::MAX
    let mut p = test_pointer();
    p.created_at = i64::MAX;

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("created_at = i64::MAX: {}", json_str);

    let parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
    assert_eq!(
        parsed["created_at"].as_i64().unwrap(),
        i64::MAX,
        "Should preserve i64::MAX"
    );
}

#[test]
fn associated_data_extreme_schema() {
    // Extreme schema value
    let mut p = test_pointer();
    p.schema = i64::MIN;

    let data = associated_data(&p);
    let json_str = String::from_utf8(data).expect("valid utf8");
    println!("schema = i64::MIN: {}", json_str);

    let parsed: serde_json::Value = serde_json::from_str(&json_str).expect("should be valid JSON");
    assert_eq!(parsed["schema"].as_i64().unwrap(), i64::MIN);
}

#[test]
fn associated_data_format_changes_hash() {
    // Changing format should change the output
    let mut p1 = test_pointer();
    p1.format = 1;

    let mut p2 = test_pointer();
    p2.format = 2;

    let d1 = associated_data(&p1);
    let d2 = associated_data(&p2);

    assert_ne!(d1, d2, "Different format should produce different data");
}

#[test]
fn associated_data_spec_changes_hash() {
    // Changing spec should change the output
    let mut p1 = test_pointer();
    p1.spec = 1;

    let mut p2 = test_pointer();
    p2.spec = 2;

    let d1 = associated_data(&p1);
    let d2 = associated_data(&p2);

    assert_ne!(d1, d2, "Different spec should produce different data");
}

// ============================================================================
// pointer_is_readable probes
// ============================================================================

#[test]
fn pointer_is_readable_with_empty_schema_list() {
    // supported_schema is empty
    let p = test_pointer();
    let result = pointer_is_readable(&p, &[]);

    println!("Empty schema list result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedSchema(_))),
        "Should reject when supported list is empty"
    );
}

#[test]
fn pointer_is_readable_with_duplicate_schemas() {
    // supported_schema has duplicate entries
    let p = test_pointer();
    let schemas = vec![1, 1, 1];

    let result = pointer_is_readable(&p, &schemas);
    println!("Duplicate schemas result: {:?}", result);
    assert!(
        result.is_ok(),
        "Should accept if schema exists, even with duplicates"
    );
}

#[test]
fn pointer_is_readable_with_many_supported_schemas() {
    // Large list of supported schemas
    let mut p = test_pointer();
    p.schema = 42;

    let mut schemas = vec![];
    for i in 1..=1000 {
        schemas.push(i as i64);
    }

    let result = pointer_is_readable(&p, &schemas);
    println!("Large schema list result: {:?}", result);
    assert!(result.is_ok(), "Should find schema 42 in large list");
}

#[test]
fn pointer_is_readable_format_zero() {
    // Pointer with format = 0 (not the current PACKAGE_FORMAT=1)
    let mut p = test_pointer();
    p.format = 0;

    let result = pointer_is_readable(&p, &[p.schema]);
    println!("Format 0 result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedFormat(0))),
        "Should reject format 0"
    );
}

#[test]
fn pointer_is_readable_format_very_large() {
    // Pointer with extremely large format number
    let mut p = test_pointer();
    p.format = u32::MAX;

    let result = pointer_is_readable(&p, &[p.schema]);
    println!("Format u32::MAX result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedFormat(u32::MAX))),
        "Should reject future format"
    );
}

#[test]
fn pointer_is_readable_cipher_empty_string() {
    // Empty cipher name
    let mut p = test_pointer();
    p.cipher = String::new();

    let result = pointer_is_readable(&p, &[p.schema]);
    println!("Empty cipher result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedCipher(_))),
        "Should reject empty cipher"
    );
}

#[test]
fn pointer_is_readable_cipher_whitespace() {
    // Cipher with only whitespace
    let mut p = test_pointer();
    p.cipher = "   ".into();

    let result = pointer_is_readable(&p, &[p.schema]);
    println!("Whitespace cipher result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedCipher(_))),
        "Should reject whitespace cipher"
    );
}

#[test]
fn pointer_is_readable_cipher_case_sensitive() {
    // Cipher with wrong case
    let mut p = test_pointer();
    p.cipher = "AES-256-GCM".into(); // uppercase

    let result = pointer_is_readable(&p, &[p.schema]);
    println!("Uppercase cipher result: {:?}", result);
    assert!(
        matches!(result, Err(PointerError::UnsupportedCipher(_))),
        "Should be case-sensitive (aes-256-gcm != AES-256-GCM)"
    );
}

#[test]
fn pointer_is_readable_all_three_errors_present() {
    // Point where all three could fail; format should be checked first
    let mut p = test_pointer();
    p.format = PACKAGE_FORMAT + 1;
    p.cipher = "unknown".into();
    p.schema = 999;

    let result = pointer_is_readable(&p, &[1]);
    println!("All errors result: {:?}", result);

    // Should fail on format first (per the implementation order)
    assert!(
        matches!(result, Err(PointerError::UnsupportedFormat(_))),
        "Should check format first"
    );
}

#[test]
fn pointer_is_readable_negative_schema() {
    // Pointer with negative schema value
    let mut p = test_pointer();
    p.schema = -1;

    let result = pointer_is_readable(&p, &[-1]);
    println!("Negative schema result: {:?}", result);
    assert!(result.is_ok(), "Should accept negative schema if in list");
}

#[test]
fn pointer_is_readable_zero_schema() {
    // Schema value of exactly 0
    let mut p = test_pointer();
    p.schema = 0;

    let result = pointer_is_readable(&p, &[0]);
    println!("Zero schema result: {:?}", result);
    assert!(result.is_ok(), "Should accept schema 0 if in list");
}

// ============================================================================
// PackageManifest probes
// ============================================================================

#[test]
fn manifest_with_zero_posters() {
    // Empty posters list
    let m = PackageManifest {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        schema: 1,
        spec: 2,
        sets: 100,
        parts: 200,
        posters: vec![],
    };

    let json = serde_json::to_string(&m).expect("serialize");
    println!("Zero posters JSON: {}", json);

    let back: PackageManifest = serde_json::from_str(&json).expect("deserialize");
    assert_eq!(back.posters.len(), 0);
}

#[test]
fn manifest_with_many_posters() {
    // Large number of posters
    let mut posters = vec![];
    for i in 0..1000 {
        posters.push(PosterEntry {
            key: format!("tmdb-movie-{}", i),
            file: format!("posters/tmdb-movie-{}.jpg", i),
        });
    }

    let m = PackageManifest {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        schema: 1,
        spec: 2,
        sets: 1000,
        parts: 5000,
        posters,
    };

    let json = serde_json::to_string(&m).expect("serialize");
    println!("1000 posters JSON length: {}", json.len());

    let back: PackageManifest = serde_json::from_str(&json).expect("deserialize");
    assert_eq!(back.posters.len(), 1000);
}

#[test]
fn manifest_with_duplicate_poster_keys() {
    // Two posters with the same key (allowed by structure, but semantically odd)
    let posters = vec![
        PosterEntry {
            key: "tmdb-movie-693134".into(),
            file: "posters/file1.jpg".into(),
        },
        PosterEntry {
            key: "tmdb-movie-693134".into(),
            file: "posters/file2.jpg".into(),
        },
    ];

    let m = PackageManifest {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        schema: 1,
        spec: 2,
        sets: 1,
        parts: 1,
        posters,
    };

    let json = serde_json::to_string(&m).expect("serialize");
    println!("Duplicate keys JSON: {}", json);

    let back: PackageManifest = serde_json::from_str(&json).expect("deserialize");
    assert_eq!(back.posters.len(), 2);
    assert_eq!(back.posters[0].key, back.posters[1].key);
}

#[test]
fn manifest_roundtrip_with_extreme_values() {
    // Extreme but valid field values
    let m = PackageManifest {
        format: u32::MAX,
        created_at: i64::MIN,
        schema: i64::MAX,
        spec: u32::MAX,
        sets: u64::MAX,
        parts: u64::MAX,
        posters: vec![PosterEntry {
            key: "a-b-1".into(),
            file: "f".into(),
        }],
    };

    let json = serde_json::to_string(&m).expect("serialize");
    let back: PackageManifest = serde_json::from_str(&json).expect("deserialize");

    assert_eq!(back.format, u32::MAX);
    assert_eq!(back.created_at, i64::MIN);
    assert_eq!(back.schema, i64::MAX);
    assert_eq!(back.spec, u32::MAX);
    assert_eq!(back.sets, u64::MAX);
    assert_eq!(back.parts, u64::MAX);
}

#[test]
fn manifest_roundtrip_field_order_preserved() {
    // Verify field order is consistent across serialization
    let m = PackageManifest {
        format: 1,
        created_at: 1234567890,
        schema: 5,
        spec: 2,
        sets: 100,
        parts: 50,
        posters: vec![PosterEntry {
            key: "test-key-123".into(),
            file: "test.jpg".into(),
        }],
    };

    let json1 = serde_json::to_string(&m).expect("first serialize");
    let back: PackageManifest = serde_json::from_str(&json1).expect("deserialize");
    let json2 = serde_json::to_string(&back).expect("second serialize");

    assert_eq!(json1, json2, "Serialization should be canonical");
}

// ============================================================================
// Helper functions
// ============================================================================

fn test_pointer() -> LatestPointer {
    LatestPointer {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        file: "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc".into(),
        url: "https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc".into(),
        bytes: 8_127_744,
        sha256: "a".repeat(64),
        cipher: CIPHER.into(),
        key_id: "9f2c41ab".into(),
        schema: 1,
        spec: 2,
    }
}
