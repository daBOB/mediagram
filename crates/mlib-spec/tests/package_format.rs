//! The on-disk shape of the prebuilt package: the manifest carried inside the archive,
//! the plaintext pointer published beside it, and the rule that binds the two
//! together so an edited pointer cannot be used with a replayed archive.

use mlib_spec::package::{
    LatestPointer, PACKAGE_FORMAT, PackageManifest, PointerError, PosterEntry, associated_data,
    backdrop_key, key_id, package_file_name, pointer_is_readable, poster_key_is_valid,
};

fn pointer() -> LatestPointer {
    LatestPointer {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        file: "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc".into(),
        url: "https://example.com/prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc".into(),
        bytes: 8_127_744,
        sha256: "a".repeat(64),
        cipher: "aes-256-gcm".into(),
        key_id: "9f2c41ab".into(),
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
    }
}

fn manifest() -> PackageManifest {
    PackageManifest {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
        sets: 312,
        parts: 468,
        posters: vec![PosterEntry {
            key: "tmdb-movie-693134".into(),
            file: "posters/tmdb-movie-693134.jpg".into(),
        }],
    }
}

#[test]
fn manifest_round_trips_byte_identically() {
    let text = serde_json::to_string(&manifest()).unwrap();
    let back: PackageManifest = serde_json::from_str(&text).unwrap();
    assert_eq!(serde_json::to_string(&back).unwrap(), text);
}

#[test]
fn pointer_round_trips_byte_identically() {
    let text = serde_json::to_string(&pointer()).unwrap();
    let back: LatestPointer = serde_json::from_str(&text).unwrap();
    assert_eq!(serde_json::to_string(&back).unwrap(), text);
}

/// Wire order is part of the contract: a second implementation comparing
/// serialized bytes must see the same field sequence.
#[test]
fn manifest_serializes_in_declared_field_order() {
    let text = serde_json::to_string(&manifest()).unwrap();
    let order: Vec<&str> = [
        "format",
        "created_at",
        "schema",
        "spec",
        "sets",
        "parts",
        "posters",
    ]
    .into_iter()
    .collect();
    let mut last = 0;
    for field in order {
        let at = text
            .find(&format!("\"{field}\""))
            .unwrap_or_else(|| panic!("{field} missing from {text}"));
        assert!(at >= last, "{field} out of order in {text}");
        last = at;
    }
}

#[test]
fn manifest_round_trips_with_zero_posters() {
    let mut empty = manifest();
    empty.posters.clear();
    let text = serde_json::to_string(&empty).unwrap();
    let back: PackageManifest = serde_json::from_str(&text).unwrap();
    assert!(back.posters.is_empty());
}

/// Nothing in this layer enforces that poster keys are unique; that is left
/// to whoever builds the manifest.
#[test]
fn manifest_allows_duplicate_poster_keys() {
    let mut with_duplicate = manifest();
    with_duplicate
        .posters
        .push(with_duplicate.posters[0].clone());
    let text = serde_json::to_string(&with_duplicate).unwrap();
    let back: PackageManifest = serde_json::from_str(&text).unwrap();
    assert_eq!(back.posters.len(), 2);
    assert_eq!(back.posters[0].key, back.posters[1].key);
}

#[test]
fn manifest_round_trips_extreme_numeric_values() {
    let extreme = PackageManifest {
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
    let text = serde_json::to_string(&extreme).unwrap();
    let back: PackageManifest = serde_json::from_str(&text).unwrap();
    assert_eq!(back, extreme);
}

/// The pointer is the one file published in the clear, so it must never name
/// a title, a set, a chat or a message.
#[test]
fn pointer_reveals_nothing_about_the_library() {
    let value = serde_json::to_value(pointer()).unwrap();
    let mut fields: Vec<&str> = value
        .as_object()
        .unwrap()
        .keys()
        .map(String::as_str)
        .collect();
    fields.sort_unstable();
    // Pinned as an exact set rather than a denylist: a denylist silently
    // passes the day someone adds a field that does leak, which is the only
    // day this check could matter.
    assert_eq!(
        fields,
        [
            "bytes",
            "cipher",
            "created_at",
            "file",
            "format",
            "key_id",
            "schema",
            "sha256",
            "spec",
            "url"
        ]
    );
}

#[test]
fn associated_data_covers_exactly_the_identifying_fields() {
    let aad = String::from_utf8(associated_data(&pointer())).unwrap();
    let expected = format!(
        r#"{{"format":{},"created_at":1781568000,"key_id":"9f2c41ab","schema":{},"spec":{}}}"#,
        PACKAGE_FORMAT,
        mlib_spec::schema::SCHEMA_VERSION,
        mlib_spec::SPEC_VERSION
    );
    assert_eq!(aad, expected);
}

#[test]
fn associated_data_changes_when_created_at_changes() {
    let mut tampered = pointer();
    tampered.created_at += 1;
    assert_ne!(associated_data(&pointer()), associated_data(&tampered));
}

#[test]
fn associated_data_changes_when_key_id_changes() {
    let mut tampered = pointer();
    tampered.key_id = "deadbeef".into();
    assert_ne!(associated_data(&pointer()), associated_data(&tampered));
}

/// The download-integrity fields are covered by the sha256 the reader checks,
/// not by the tag, so they must stay out of the associated data: including
/// them would make the file name part of what the cipher authenticates.
#[test]
fn associated_data_ignores_the_download_fields() {
    let mut renamed = pointer();
    renamed.file = "something-else.tar.gz.enc".into();
    renamed.url = "https://elsewhere.example/other.tar.gz.enc".into();
    renamed.bytes = 1;
    renamed.sha256 = "b".repeat(64);
    assert_eq!(associated_data(&pointer()), associated_data(&renamed));
}

#[test]
fn associated_data_changes_when_format_changes() {
    let mut newer_format = pointer();
    newer_format.format += 1;
    assert_ne!(associated_data(&pointer()), associated_data(&newer_format));
}

#[test]
fn associated_data_changes_when_spec_changes() {
    let mut newer_spec = pointer();
    newer_spec.spec += 1;
    assert_ne!(associated_data(&pointer()), associated_data(&newer_spec));
}

/// serde_json escapes whatever a key_id contains, so associated data stays
/// valid JSON even for a value `pointer_is_readable` would already refuse.
#[test]
fn associated_data_stays_valid_json_for_any_key_id_content() {
    for awkward in ["abc\"def", "abc\\def", "abc\ndef\nghi", "café_日本"] {
        let mut odd = pointer();
        odd.key_id = awkward.into();
        let text = String::from_utf8(associated_data(&odd)).unwrap();
        serde_json::from_str::<serde_json::Value>(&text)
            .unwrap_or_else(|e| panic!("`{awkward}` produced invalid JSON: {e}"));
    }
}

#[test]
fn associated_data_round_trips_extreme_created_at_and_schema() {
    let mut at_min = pointer();
    at_min.created_at = i64::MIN;
    at_min.schema = i64::MIN;
    let mut at_max = pointer();
    at_max.created_at = i64::MAX;
    at_max.schema = i64::MAX;
    for p in [&at_min, &at_max] {
        let text = String::from_utf8(associated_data(p)).unwrap();
        let parsed: serde_json::Value = serde_json::from_str(&text).unwrap();
        assert_eq!(parsed["created_at"].as_i64().unwrap(), p.created_at);
        assert_eq!(parsed["schema"].as_i64().unwrap(), p.schema);
    }
}

#[test]
fn key_id_is_stable_for_a_key_and_differs_for_another() {
    let a = [7u8; 32];
    let b = [8u8; 32];
    assert_eq!(key_id(&a), key_id(&a));
    assert_ne!(key_id(&a), key_id(&b));
    assert_eq!(key_id(&a).len(), 8, "four bytes as hex");
}

#[test]
fn poster_keys_accept_the_documented_shapes() {
    assert!(poster_key_is_valid("tmdb-movie-693134"));
    assert!(poster_key_is_valid("tmdb-tv-550"));
    assert!(poster_key_is_valid("tvdb-series-12345"));
    assert!(poster_key_is_valid("a-b-1"));
    // A season's artwork: one more part, `s` and digits, nothing else.
    assert!(poster_key_is_valid("tmdb-tv-550-s2"));
    // A title's backdrop: the literal `bg`, never on a season.
    assert!(poster_key_is_valid("tmdb-movie-550-bg"));
    assert!(poster_key_is_valid("tmdb-tv-550-bg"));
    for bad in [
        "tmdb-tv-550-s2-bg",
        "tmdb-tv-550-bg-s2",
        "tmdb-movie-550-BG",
        "tmdb-movie-550-bgx",
        "tmdb-movie-550-b",
    ] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
    for bad in [
        "tmdb-tv-550-s",
        "tmdb-tv-550-2",
        "tmdb-tv-550-x2",
        "tmdb-tv-550-s2-s3",
        "tmdb-tv-550-S2",
    ] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
    assert_eq!(
        mlib_spec::package::season_poster_key("tmdb-tv-550", 2),
        "tmdb-tv-550-s2"
    );
}

/// The key reaches a file name, a manifest path and a tar member name, so
/// anything that could climb out of a directory has to be refused here.
#[test]
fn poster_keys_reject_anything_that_could_escape_a_directory() {
    for bad in [
        "tmdb-movie-../../etc/passwd",
        "../../x",
        "/absolute-1",
        "tmdb-movie-693134/../../x",
        "tmdb-movie-693134\\x",
        "tmdb-movie-",
        "tmdb-movie-12a",
        "TMDB-movie-1",
        "tmdb movie 1",
        "",
    ] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
}

#[test]
fn poster_keys_reject_control_characters() {
    assert!(!poster_key_is_valid("tmdb-movie-1\n"));
    assert!(!poster_key_is_valid("tmdb-movie-1\u{0}"));
}

/// Movie and television id spaces are independent at TMDB, so the kind has to
/// be part of the key or two different titles collide onto one poster.
#[test]
fn poster_keys_separate_movie_and_tv_id_spaces() {
    assert_ne!("tmdb-movie-550", "tmdb-tv-550");
    assert!(poster_key_is_valid("tmdb-movie-550") && poster_key_is_valid("tmdb-tv-550"));
}

/// Only ASCII digits and letters count: a look-alike from another script
/// must not slip through and reach a file name or a tar member name.
#[test]
fn poster_keys_reject_non_ascii_lookalikes_of_valid_characters() {
    assert!(!poster_key_is_valid("tmdb-movie-٦٩٣١٣٤")); // Arabic-Indic digits
    assert!(!poster_key_is_valid("tmdb-movie-６９３１３４")); // fullwidth digits
    assert!(!poster_key_is_valid("ｔｍｄｂ-ｍｏｖｉｅ-693134")); // fullwidth letters
}

/// The spec calls for digits, not a positive non-zero integer, so a leading
/// zero is not itself a reason to reject an id.
#[test]
fn poster_keys_accept_leading_zeros_in_the_id() {
    assert!(poster_key_is_valid("tmdb-movie-00693134"));
    assert!(poster_key_is_valid("tmdb-movie-0"));
    assert!(poster_key_is_valid("tmdb-movie-00000000"));
}

#[test]
fn poster_keys_reject_a_sign_in_the_id() {
    assert!(!poster_key_is_valid("tmdb-movie-+693134"));
    assert!(!poster_key_is_valid("tmdb-movie--693134"));
    assert!(!poster_key_is_valid("tmdb-movie-693134+"));
}

#[test]
fn poster_keys_reject_hex_letters_in_the_id() {
    assert!(!poster_key_is_valid("tmdb-movie-deadbeef"));
    assert!(!poster_key_is_valid("tmdb-movie-a1b2c3"));
}

#[test]
fn poster_keys_accept_an_arbitrarily_long_id() {
    let long_id = format!("tmdb-movie-{}", "1".repeat(10_000));
    assert!(poster_key_is_valid(&long_id));
}

/// The key has exactly three parts; fewer or more must be rejected.
#[test]
fn poster_keys_reject_too_few_or_too_many_parts() {
    assert!(!poster_key_is_valid("tmdb-movie"));
    assert!(!poster_key_is_valid("tmdb"));
    assert!(!poster_key_is_valid("tmdb-movie-123-extra"));
    assert!(!poster_key_is_valid("tmdb-movie.693134")); // one dash: the dot doesn't split
}

#[test]
fn poster_keys_reject_uppercase_anywhere_in_source_or_kind() {
    assert!(!poster_key_is_valid("TMDB-movie-693134"));
    assert!(!poster_key_is_valid("tmdb-MOVIE-693134"));
    assert!(!poster_key_is_valid("Tmdb-Movie-693134"));
}

#[test]
fn poster_keys_reject_special_characters_in_the_id() {
    assert!(!poster_key_is_valid("tmdb-movie-693 134"));
    assert!(!poster_key_is_valid("tmdb-movie-693_134"));
}

#[test]
fn poster_keys_reject_empty_or_whitespace_only_segments() {
    assert!(!poster_key_is_valid("-movie-693134"));
    assert!(!poster_key_is_valid("tmdb--693134"));
    assert!(!poster_key_is_valid("tmdb-movie- "));
}

/// A digest whose first four bytes are the ones that reach the file name.
fn digest(first_four: [u8; 4]) -> [u8; 32] {
    let mut out = [0xffu8; 32];
    out[..4].copy_from_slice(&first_four);
    out
}

#[test]
fn package_file_name_uses_the_date_and_the_ciphertext_hash() {
    let name = package_file_name(1_781_568_000, &digest([0x3d, 0x7e, 0x10, 0xc4]));
    assert_eq!(name, "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc");
}

/// Two exports on the same day must not collide, and the name must not depend
/// on a local counter that a cleaned output directory would reset.
#[test]
fn package_file_name_differs_for_different_content_on_the_same_day() {
    let a = package_file_name(1_781_568_000, &digest([0xaa; 4]));
    let b = package_file_name(1_781_568_000, &digest([0xbb; 4]));
    assert_ne!(a, b);
    assert!(a.contains("20260616") && b.contains("20260616"));
}

#[test]
fn package_file_name_handles_the_unix_epoch() {
    assert!(package_file_name(0, &digest([0xcc; 4])).contains("19700101"));
}

/// Divisible by 100 does not make a leap year unless also divisible by 400.
#[test]
fn package_file_name_follows_the_leap_year_rule_across_centuries() {
    // 2024-02-29: an ordinary leap year.
    assert!(package_file_name(1_709_164_800, &digest([0; 4])).contains("20240229"));
    // 2000-02-29: divisible by 400, so still a leap year.
    assert!(package_file_name(951_782_400, &digest([0; 4])).contains("20000229"));
    // 2100-03-01: divisible by 100 but not 400, so February has only 28 days.
    assert!(package_file_name(4_107_542_400, &digest([0; 4])).contains("21000301"));
}

#[test]
fn package_file_name_crosses_the_millennium_boundary() {
    assert!(package_file_name(946_684_799, &digest([0; 4])).contains("19991231"));
    assert!(package_file_name(946_684_800, &digest([0; 4])).contains("20000101"));
}

/// The date changes only at midnight UTC, whether entering or leaving a day.
#[test]
fn package_file_name_changes_only_at_the_day_boundary() {
    let midnight = 1_781_568_000;
    let before = package_file_name(midnight - 1, &digest([0; 4]));
    let at = package_file_name(midnight, &digest([0; 4]));
    let just_after = package_file_name(midnight + 1, &digest([0; 4]));
    let last_second = package_file_name(midnight + 86_399, &digest([0; 4]));
    let next_midnight = package_file_name(midnight + 86_400, &digest([0; 4]));
    assert_ne!(
        before, at,
        "the previous day must not share a name with this one"
    );
    assert_eq!(at, just_after, "the same day must share one name");
    assert_ne!(
        last_second, next_midnight,
        "the next day must not share a name with this one"
    );
}

/// A pre-epoch timestamp is clamped to the epoch rather than rendered,
/// because a negative year would break the fixed `YYYYMMDD` shape. It cannot
/// arise from a real export, and `pointer_is_readable` rejects a negative
/// `created_at` outright, so clamping here only guarantees a well-formed name.
#[test]
fn package_file_name_clamps_any_pre_epoch_timestamp_to_the_epoch() {
    assert!(package_file_name(-1, &digest([0; 4])).contains("19700101"));
    assert!(package_file_name(-86_400, &digest([0; 4])).contains("19700101"));
    assert!(package_file_name(-315_619_200, &digest([0; 4])).contains("19700101"));
}

#[test]
fn package_file_name_does_not_panic_at_i64_max() {
    let name = package_file_name(i64::MAX, &digest([0; 4]));
    assert!(name.starts_with("prebuilt_mediagram_db_") && name.ends_with(".tar.gz.enc"));
}

#[test]
fn package_file_name_does_not_panic_at_i64_min() {
    let name = package_file_name(i64::MIN, &digest([0; 4]));
    assert!(name.starts_with("prebuilt_mediagram_db_") && name.ends_with(".tar.gz.enc"));
}

/// Only the first four digest bytes reach the name; anything past that
/// boundary cannot affect it.
#[test]
fn package_file_name_uses_only_the_first_four_digest_bytes() {
    let mut a = [0u8; 32];
    let mut b = [0u8; 32];
    a[..4].copy_from_slice(&[0xde, 0xad, 0xbe, 0xef]);
    b[..4].copy_from_slice(&[0xde, 0xad, 0xbe, 0xef]);
    b[4] = 0xff;
    assert_eq!(
        package_file_name(1_781_568_000, &a),
        package_file_name(1_781_568_000, &b)
    );
}

#[test]
fn a_pointer_of_a_known_format_and_schema_is_readable() {
    assert!(pointer_is_readable(&pointer(), &[mlib_spec::schema::SCHEMA_VERSION]).is_ok());
}

#[test]
fn a_pointer_of_an_unknown_format_is_refused() {
    let mut future = pointer();
    future.format = PACKAGE_FORMAT + 1;
    assert!(matches!(
        pointer_is_readable(&future, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::UnsupportedFormat(_))
    ));
}

#[test]
fn a_pointer_naming_a_schema_older_than_the_oldest_readable_is_refused() {
    let mut older = pointer();
    older.schema = mlib_spec::schema::OLDEST_READABLE_SCHEMA - 1;
    assert!(matches!(
        pointer_is_readable(&older, mlib_spec::schema::READABLE_SCHEMAS),
        Err(PointerError::UnsupportedSchema(_))
    ));
}

/// Schema changes only add columns every reader treats as optional; a
/// breaking change moves `format`, which stays exact. So an installed reader
/// keeps following a publisher that upgraded first.
#[test]
fn a_pointer_naming_a_newer_schema_is_read() {
    let mut newer = pointer();
    newer.schema = 99;
    assert!(pointer_is_readable(&newer, mlib_spec::schema::READABLE_SCHEMAS).is_ok());
}

#[test]
fn a_pointer_naming_an_unknown_cipher_is_refused() {
    let mut odd = pointer();
    odd.cipher = "rot13".into();
    assert!(matches!(
        pointer_is_readable(&odd, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::UnsupportedCipher(_))
    ));
}

/// Everything below arrives in a plaintext file from a public URL. These
/// fields reach a cipher, a file name and an allocation, so a reader must be
/// able to reject them before any of that happens.
#[test]
fn a_pointer_with_a_malformed_key_id_is_refused() {
    for bad in [
        "",
        "9F2C41AB",
        "9f2c41a",
        "9f2c41abc",
        "zzzzzzzz",
        "9f2c41a\"",
    ] {
        let mut p = pointer();
        p.key_id = bad.into();
        assert_eq!(
            pointer_is_readable(&p, &[mlib_spec::schema::SCHEMA_VERSION]),
            Err(PointerError::Malformed("key_id")),
            "`{bad}` must be refused"
        );
    }
}

#[test]
fn a_pointer_with_a_malformed_sha256_is_refused() {
    for bad in [
        "",
        &"a".repeat(63),
        &"a".repeat(65),
        &"A".repeat(64),
        &"g".repeat(64),
    ] {
        let mut p = pointer();
        p.sha256 = bad.to_string();
        assert_eq!(
            pointer_is_readable(&p, &[mlib_spec::schema::SCHEMA_VERSION]),
            Err(PointerError::Malformed("sha256"))
        );
    }
}

#[test]
fn a_pointer_claiming_more_than_the_limit_is_refused_before_downloading() {
    let mut p = pointer();
    p.bytes = mlib_spec::package::MAX_PACKAGE_BYTES + 1;
    assert_eq!(
        pointer_is_readable(&p, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::TooLarge(
            mlib_spec::package::MAX_PACKAGE_BYTES + 1
        ))
    );
}

#[test]
fn a_pointer_claiming_zero_bytes_is_refused() {
    let mut p = pointer();
    p.bytes = 0;
    assert_eq!(
        pointer_is_readable(&p, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::Malformed("bytes"))
    );
}

#[test]
fn a_pointer_dated_before_the_epoch_is_refused() {
    let mut p = pointer();
    p.created_at = -1;
    assert_eq!(
        pointer_is_readable(&p, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::Malformed("created_at"))
    );
}

#[test]
fn a_pointer_is_refused_when_the_supported_schema_list_is_empty() {
    assert!(matches!(
        pointer_is_readable(&pointer(), &[]),
        Err(PointerError::UnsupportedSchema(_))
    ));
}

/// The checks run in a fixed order, so a pointer wrong in every way still
/// reports the first one: format.
#[test]
fn pointer_is_readable_checks_format_before_cipher_or_schema() {
    let mut wrong_in_every_way = pointer();
    wrong_in_every_way.format = PACKAGE_FORMAT + 1;
    wrong_in_every_way.cipher = "unknown".into();
    wrong_in_every_way.schema = 999;
    assert!(matches!(
        pointer_is_readable(&wrong_in_every_way, &[1]),
        Err(PointerError::UnsupportedFormat(_))
    ));
}

#[test]
fn a_pointer_with_a_negative_schema_is_accepted_if_listed() {
    let mut p = pointer();
    p.schema = -1;
    assert!(pointer_is_readable(&p, &[-1]).is_ok());
}

#[test]
fn a_pointer_with_schema_zero_is_accepted_if_listed() {
    let mut p = pointer();
    p.schema = 0;
    assert!(pointer_is_readable(&p, &[0]).is_ok());
}

/// `key_id` is hex by the time it reaches the cipher, which is what lets a
/// reader using a different JSON library reproduce these bytes: there is no
/// character left whose escaping the two writers could disagree about.
#[test]
fn associated_data_contains_no_character_a_json_writer_could_escape_differently() {
    let aad = String::from_utf8(associated_data(&pointer())).unwrap();
    assert!(
        !aad.contains('\\') && !aad.contains('<') && !aad.contains('&') && !aad.contains('\''),
        "aad must be free of anything an HTML-safe writer would escape: {aad}"
    );
}

#[test]
fn a_backdrop_sits_beside_its_titles_poster() {
    assert_eq!(backdrop_key("tmdb-movie-550"), "tmdb-movie-550-bg");
    assert!(poster_key_is_valid(&backdrop_key("tmdb-tv-7")));
}
