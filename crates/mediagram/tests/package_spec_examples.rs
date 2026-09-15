//! The examples in `docs/mlib-package-v1.md` are generated here and asserted
//! against the document. A specification a player author trusts must not
//! drift from what the exporter actually writes, and the only way to
//! guarantee that is to fail the test run when it does.

use mediagram::export::{latest, pointer};
use mlib_spec::package::{PackageManifest, PosterEntry, associated_data};

const SPEC: &str = include_str!("../../../docs/mlib-package-v1.md");

fn key() -> [u8; 32] {
    let mut k = [0u8; 32];
    for (i, b) in k.iter_mut().enumerate() {
        *b = i as u8;
    }
    k
}

#[test]
fn the_manifest_example_matches_what_the_exporter_writes() {
    let manifest = PackageManifest {
        format: mlib_spec::package::PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
        sets: 312,
        parts: 468,
        posters: vec![PosterEntry {
            key: "tmdb-movie-693134".into(),
            file: "posters/tmdb-movie-693134.jpg".into(),
        }],
    };
    let rendered = serde_json::to_string(&manifest).unwrap();
    assert!(
        SPEC.contains(&rendered),
        "manifest example in docs/mlib-package-v1.md is stale.\nexpected: {rendered}"
    );
}

#[test]
fn the_associated_data_example_matches_what_the_cipher_authenticates() {
    let draft = pointer::draft(1_781_568_000, &key());
    let rendered = String::from_utf8(associated_data(&draft)).unwrap();
    assert!(
        SPEC.contains(&rendered),
        "associated-data example in docs/mlib-package-v1.md is stale.\nexpected: {rendered}"
    );
}

#[test]
fn the_pointer_example_matches_a_completed_pointer() {
    let draft = pointer::draft(1_781_568_000, &key());
    let done = latest::complete(
        &draft,
        "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc",
        "https://example.com",
        8_127_744,
        "…64 hex…",
    );
    let rendered = serde_json::to_string(&done).unwrap();
    assert!(
        SPEC.contains(&rendered),
        "pointer example in docs/mlib-package-v1.md is stale.\nexpected: {rendered}"
    );
}

/// Two warnings in the reader algorithm describe ways a correct-looking
/// reader is silently wrong, and one records a limitation of this format.
/// None may quietly disappear from the document.
#[test]
fn the_spec_keeps_the_warnings_a_reader_gets_wrong() {
    assert!(SPEC.contains("Never skip on `sha256`"));
    assert!(SPEC.contains("CipherInputStream"));
    assert!(SPEC.contains("does not sign the pointer"));
}
