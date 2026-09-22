//! The on-disk shape of the prebuilt package: the manifest carried inside the archive,
//! the plaintext pointer published beside it, and the rule that binds the two
//! together so an edited pointer cannot be used with a replayed archive.

use mlib_spec::package::{
    LatestPointer, PACKAGE_FORMAT, PackageManifest, PointerError, PosterEntry, associated_data,
    key_id, package_file_name, pointer_is_readable, poster_key_is_valid,
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
    // A season's artwork: one more part, `s` and digits, nothing else.
    assert!(poster_key_is_valid("tmdb-tv-550-s2"));
    for bad in ["tmdb-tv-550-s", "tmdb-tv-550-2", "tmdb-tv-550-x2", "tmdb-tv-550-s2-s3", "tmdb-tv-550-S2"] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
    assert_eq!(mlib_spec::package::season_poster_key("tmdb-tv-550", 2), "tmdb-tv-550-s2");
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
fn a_pointer_naming_an_unsupported_schema_is_refused() {
    let mut newer = pointer();
    newer.schema = 99;
    assert!(matches!(
        pointer_is_readable(&newer, &[mlib_spec::schema::SCHEMA_VERSION]),
        Err(PointerError::UnsupportedSchema(99))
    ));
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
