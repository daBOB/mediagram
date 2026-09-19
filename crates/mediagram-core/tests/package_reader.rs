//! Reading a downloaded, sealed package back into an on-disk index: the tag
//! must bind the archive to the exact pointer that named it, and a
//! well-formed package must extract the index it carries.

use std::io::Write;

use flate2::Compression;
use flate2::write::GzEncoder;
use mediagram_core::package::cipher;
use mediagram_core::package::{PackageError, read_package};
use mlib_spec::package::{CIPHER, LatestPointer, PACKAGE_FORMAT, associated_data, key_id};
use sha2::{Digest, Sha256};

const KEY: [u8; 32] = [9u8; 32];

/// Packs one file (`library.db`, holding a placeholder row) as a gzipped
/// tar, seals it, and returns a pointer that matches the sealed bytes.
fn fixture_package() -> (LatestPointer, Vec<u8>, [u8; 32]) {
    let mut tar_bytes = Vec::new();
    {
        let mut builder = tar::Builder::new(&mut tar_bytes);
        let payload = b"sqlite bytes";
        let mut header = tar::Header::new_gnu();
        header.set_size(payload.len() as u64);
        header.set_mode(0o644);
        header.set_cksum();
        builder
            .append_data(&mut header, "library.db", &payload[..])
            .unwrap();
        builder.finish().unwrap();
    }
    let mut gz = Vec::new();
    {
        let mut enc = GzEncoder::new(&mut gz, Compression::default());
        enc.write_all(&tar_bytes).unwrap();
        enc.finish().unwrap();
    }

    let mut pointer = LatestPointer {
        format: PACKAGE_FORMAT,
        created_at: 1_781_568_000,
        file: "prebuilt_mediagram_db.tar.gz.enc".into(),
        url: "https://example.invalid/prebuilt_mediagram_db.tar.gz.enc".into(),
        bytes: 0,
        sha256: String::new(),
        cipher: CIPHER.to_string(),
        key_id: key_id(&KEY),
        schema: mlib_spec::schema::SCHEMA_VERSION,
        spec: mlib_spec::SPEC_VERSION,
    };
    let sealed = cipher::seal(&KEY, &gz, &associated_data(&pointer)).unwrap();
    pointer.bytes = sealed.len() as u64;
    pointer.sha256 = hex::encode(Sha256::digest(&sealed));

    (pointer, sealed, KEY)
}

#[test]
fn rejects_an_archive_whose_tag_does_not_match_the_pointer() {
    let dir = tempfile::tempdir().unwrap();
    let (pointer, sealed, key) = fixture_package();
    let mut tampered = pointer.clone();
    tampered.created_at += 1; // a pointer field that is associated data

    let err = read_package(&tampered, &sealed, &key, dir.path()).unwrap_err();
    assert!(matches!(err, PackageError::Cipher(_)));
}

#[test]
fn extracts_the_index_from_a_well_formed_package() {
    let dir = tempfile::tempdir().unwrap();
    let (pointer, sealed, key) = fixture_package();

    let db = read_package(&pointer, &sealed, &key, dir.path()).unwrap();
    assert!(db.ends_with("library.db"));
    assert!(db.exists());
    assert_eq!(std::fs::read(&db).unwrap(), b"sqlite bytes");
}

#[test]
fn a_digest_mismatch_is_rejected_before_the_cipher_runs() {
    let dir = tempfile::tempdir().unwrap();
    let (mut pointer, sealed, key) = fixture_package();
    pointer.sha256 = hex::encode([0u8; 32]);

    let err = read_package(&pointer, &sealed, &key, dir.path()).unwrap_err();
    assert!(matches!(err, PackageError::DigestMismatch));
}
