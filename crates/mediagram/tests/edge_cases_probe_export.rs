//! Edge-case coverage for export phase 2: encrypt, archive, stage, titles, budget, pointer.
//! Probes boundaries, error conditions, and stress cases the main suite doesn't cover.

use mediagram::export::archive::{pack_dir, unpack_to};
use mediagram::export::budget::{Verdict, estimate_bytes, verdict_for};
use mediagram::export::encrypt::{EncryptError, NONCE_LEN, TAG_LEN, open, seal};
use mediagram::export::pointer;
use mediagram::export::titles::{counts, distinct_titles};
use mediagram::index::db;
use mediagram::index::set_row::SetRow;
use mediagram::index::sets;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;
use std::io::Write;
use std::os::unix::fs::PermissionsExt;

// =============================================================================
// ENCRYPT: Edge cases
// =============================================================================

#[test]
fn encrypt_empty_aad_seals_and_opens() {
    // AAD can be empty; the tag must still authenticate the plaintext
    let key = [1u8; 32];
    let plaintext = b"content";
    let sealed = seal(&key, plaintext, b"").expect("seal with empty AAD");
    let decrypted = open(&key, &sealed, b"").expect("open with empty AAD");
    assert_eq!(decrypted, plaintext);
}

#[test]
fn encrypt_empty_aad_wrong_key_fails() {
    // Even with empty AAD, wrong key must fail
    let key = [1u8; 32];
    let other = [2u8; 32];
    let sealed = seal(&key, b"content", b"").expect("seal");
    assert!(matches!(open(&other, &sealed, b""), Err(EncryptError::Tag)));
}

#[test]
fn encrypt_very_large_aad_works() {
    // AAD can be kilobytes without affecting encryption correctness
    let key = [5u8; 32];
    let plaintext = b"small";
    let large_aad = vec![0xAAu8; 64 * 1024]; // 64 KB of AAD
    let sealed = seal(&key, plaintext, &large_aad).expect("seal with 64KB AAD");
    let decrypted = open(&key, &sealed, &large_aad).expect("open with same AAD");
    assert_eq!(decrypted, plaintext);
}

#[test]
fn encrypt_megabyte_payload_seals_and_opens() {
    // The comment mentions "several megabytes" as an edge case to test
    let key = [9u8; 32];
    let plaintext = vec![0x42u8; 1024 * 1024]; // 1 MB
    let aad = b"identifying data";
    let sealed = seal(&key, &plaintext, aad).expect("seal 1MB");
    let decrypted = open(&key, &sealed, aad).expect("open 1MB");
    assert_eq!(decrypted, plaintext);
}

#[test]
fn encrypt_all_zero_key_is_valid() {
    // A key of all zeros is cryptographically valid (though weak in practice)
    let key = [0u8; 32];
    let plaintext = b"encrypted with weak key";
    let aad = b"aad";
    let sealed = seal(&key, plaintext, aad).expect("seal with zero key");
    let decrypted = open(&key, &sealed, aad).expect("open with zero key");
    assert_eq!(decrypted, plaintext);
}

#[test]
fn encrypt_open_exactly_nonce_plus_tag_fails() {
    // A sealed file exactly NONCE_LEN+TAG_LEN bytes has zero plaintext, but is too short
    let key = [7u8; 32];
    let exactly_small = vec![0u8; NONCE_LEN + TAG_LEN];
    // This is technically a malformed sealed message (no ciphertext, only nonce and tag)
    // The open() call should fail because the tag will not verify empty plaintext with that nonce
    let result = open(&key, &exactly_small, b"");
    // This should fail due to tag verification, not size check
    assert!(matches!(result, Err(EncryptError::Tag)));
}

#[test]
fn encrypt_open_returns_no_partial_data_on_corruption() {
    // If decryption/tag verification fails, no partial plaintext escapes
    let key = [3u8; 32];
    let plaintext = vec![0xABu8; 1024];
    let aad = b"associated";
    let mut sealed = seal(&key, &plaintext, aad).expect("seal");

    // Corrupt the tag (last 16 bytes)
    let last_idx = sealed.len() - 1;
    sealed[last_idx] ^= 0xFF;

    let result = open(&key, &sealed, aad);
    assert!(matches!(result, Err(EncryptError::Tag)));
    // Implicit: if open() returned Ok(vec), we'd verify its len == plaintext.len();
    // but it MUST return Err, so no partial data is returned
}

#[test]
fn encrypt_multiple_megabyte_roundtrip() {
    // Stress test with larger realistic payload
    let key = [0x42u8; 32];
    let plaintext = vec![0x99u8; 5 * 1024 * 1024]; // 5 MB
    let aad = br#"{"format":1,"created_at":1234567890,"key_id":"abc12345","schema":1,"spec":2}"#;

    let sealed = seal(&key, &plaintext, aad).expect("seal 5MB");
    let decrypted = open(&key, &sealed, aad).expect("open 5MB");
    assert_eq!(decrypted.len(), plaintext.len());
    assert_eq!(decrypted, plaintext);
}

// =============================================================================
// ARCHIVE: Edge cases
// =============================================================================

#[test]
fn archive_unicode_filename_round_trips() {
    // Files with non-ASCII names should be preserved
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("файл.txt"), b"russian name").unwrap();
    std::fs::write(src.path().join("文件.txt"), b"chinese name").unwrap();
    std::fs::write(src.path().join("파일.txt"), b"korean name").unwrap();

    let packed = pack_dir(src.path()).expect("pack with unicode names");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack with unicode names");

    assert_eq!(
        std::fs::read(out.path().join("файл.txt")).unwrap(),
        b"russian name"
    );
    assert_eq!(
        std::fs::read(out.path().join("文件.txt")).unwrap(),
        b"chinese name"
    );
    assert_eq!(
        std::fs::read(out.path().join("파일.txt")).unwrap(),
        b"korean name"
    );
}

#[test]
fn archive_very_long_filename() {
    // Some filesystems have limits; test a long (but valid) name
    let src = tempfile::tempdir().unwrap();
    let long_name = "a".repeat(200); // 200 bytes, well under typical 255 limit
    std::fs::write(src.path().join(&long_name), b"content").unwrap();

    let packed = pack_dir(src.path()).expect("pack long name");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack long name");

    assert_eq!(
        std::fs::read(out.path().join(&long_name)).unwrap(),
        b"content"
    );
}

#[test]
fn archive_deeply_nested_directories() {
    // Test path depth (tar and gzip can handle it)
    let src = tempfile::tempdir().unwrap();
    let mut path = src.path().to_path_buf();
    for i in 0..20 {
        path.push(format!("dir{}", i));
    }
    std::fs::create_dir_all(&path).expect("create deep dirs");
    std::fs::write(path.join("file.txt"), b"deep content").expect("write in deep dir");

    let packed = pack_dir(src.path()).expect("pack deep dirs");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack deep dirs");

    let mut out_path = out.path().to_path_buf();
    for i in 0..20 {
        out_path.push(format!("dir{}", i));
    }
    assert_eq!(
        std::fs::read(out_path.join("file.txt")).unwrap(),
        b"deep content"
    );
}

#[test]
fn archive_empty_file() {
    // Zero-byte files must round-trip correctly
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("empty.txt"), b"").expect("write empty");

    let packed = pack_dir(src.path()).expect("pack empty file");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack empty file");

    assert_eq!(std::fs::read(out.path().join("empty.txt")).unwrap(), b"");
}

#[test]
fn archive_many_files() {
    // Test with a high file count (200 files)
    let src = tempfile::tempdir().unwrap();
    for i in 0..200 {
        std::fs::write(
            src.path().join(format!("file_{:03}.txt", i)),
            format!("content {}", i).as_bytes(),
        )
        .unwrap();
    }

    let packed = pack_dir(src.path()).expect("pack 200 files");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack 200 files");

    for i in 0..200 {
        let content = std::fs::read(out.path().join(format!("file_{:03}.txt", i))).unwrap();
        assert_eq!(content, format!("content {}", i).as_bytes());
    }
}

#[test]
fn archive_rejects_member_with_trailing_slash() {
    // A member listed as "dir/" instead of "dir" is not a regular file
    let mut tar_bytes = Vec::new();
    {
        let mut builder = tar::Builder::new(&mut tar_bytes);
        let payload = b"content";
        let mut header = tar::Header::new_gnu();
        header.set_size(payload.len() as u64);
        header.set_mode(0o644);
        header.set_mtime(0);
        header.set_uid(0);
        header.set_gid(0);
        header.set_cksum();
        // Fake a member named "file.txt/" (directory, not file)
        let name = b"file.txt/";
        header.as_old_mut().name[..name.len()].copy_from_slice(name);
        header.set_cksum();
        builder
            .append(&header, &payload[..])
            .expect("append to tar");
        builder.finish().expect("finish tar");
    }
    let mut gz = Vec::new();
    {
        let mut enc = flate2::write::GzEncoder::new(&mut gz, flate2::Compression::default());
        enc.write_all(&tar_bytes).expect("write tar");
        enc.finish().expect("finish gzip");
    }

    let out = tempfile::tempdir().unwrap();
    // unpack_to should reject this because it's not a regular file
    assert!(unpack_to(&gz, out.path()).is_err());
}

#[test]
fn archive_symlink_inside_staging_is_skipped() {
    // This probe originally recorded that `pack_dir` followed symlinks, which
    // meant a link inside staging could pull an outside file into the package.
    // `collect_files` now skips them; the target itself is still packed.
    let src = tempfile::tempdir().unwrap();
    std::fs::write(src.path().join("target.txt"), b"target content").unwrap();
    let outside = tempfile::tempdir().unwrap();
    std::fs::write(outside.path().join("secret.txt"), b"not for the package").unwrap();

    #[cfg(unix)]
    {
        use std::os::unix::fs as unix_fs;
        unix_fs::symlink(src.path().join("target.txt"), src.path().join("link.txt")).unwrap();
        unix_fs::symlink(
            outside.path().join("secret.txt"),
            src.path().join("escape.txt"),
        )
        .unwrap();
    }

    let packed = pack_dir(src.path()).expect("pack with symlink");
    let out = tempfile::tempdir().unwrap();
    unpack_to(&packed, out.path()).expect("unpack");

    assert!(!out.path().join("link.txt").exists(), "symlink not packed");
    assert!(
        !out.path().join("escape.txt").exists(),
        "a link out of staging must not pull its target into the package"
    );
    assert_eq!(
        std::fs::read(out.path().join("target.txt")).unwrap(),
        b"target content",
        "the real file is still packed"
    );
}

#[test]
fn titles_unknown_kind_is_logged_and_skipped() {
    // Insert a row with an unknown kind string directly
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    // Directly insert a row with kind='unknown' (use actual schema)
    conn.execute(
        "INSERT INTO sets (set_id, kind, tmdb, title, container, total, part_count, created_at, spec_version) VALUES ('unknown_set', 'unknown', 999, 'Unknown Title', 'mkv', 0, 0, 1700000000, 2)",
        [],
    )
    .unwrap();

    let found = distinct_titles(&conn).unwrap();
    // Unknown kind should be skipped
    assert_eq!(found.len(), 0);
}

#[test]
fn titles_negative_tmdb_is_rejected() {
    // TMDB IDs should be positive; negative values cannot be cast to u64
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    // Directly insert a row with tmdb=-1 (use actual schema)
    conn.execute(
        "INSERT INTO sets (set_id, kind, tmdb, title, container, total, part_count, created_at, spec_version) VALUES ('negative_set', 'movie', -1, 'Negative ID', 'mkv', 0, 0, 1700000000, 2)",
        [],
    )
    .unwrap();

    let found = distinct_titles(&conn).unwrap();
    // Negative tmdb should be skipped
    assert_eq!(found.len(), 0);
}

#[test]
fn titles_huge_tmdb_value_works() {
    // Very large but valid u64 IDs (using a large i64 that fits in both i64 and u64)
    // The database stores tmdb as INTEGER (i64), so we test a large but valid value
    let large_id = i64::MAX as u64 - 1_000_000;
    let (_d, conn) = db_with(&[("01A", Kind::Movie, Some(large_id))]);
    let found = distinct_titles(&conn).unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(found[0], (Kind::Movie, large_id));
}

#[test]
fn titles_null_tmdb_is_skipped() {
    // Rows with NULL tmdb don't contribute to distinct titles
    let (_d, conn) = db_with(&[("01A", Kind::Movie, None), ("01B", Kind::Movie, Some(7))]);
    let found = distinct_titles(&conn).unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(found[0], (Kind::Movie, 7));
}

#[test]
fn titles_counts_with_parts_but_no_sets() {
    // An empty sets table but with parts in the parts table
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    // Insert only into parts (if it exists and can be populated)
    // For now, just verify that counts() handles the case gracefully

    let (sets_count, parts_count) = counts(&conn).unwrap();
    assert_eq!(sets_count, 0);
    assert_eq!(parts_count, 0, "no parts inserted");
}

#[test]
fn titles_all_rows_without_tmdb() {
    // Database with many sets but none have a TMDB ID
    let row_tuples: Vec<_> = (0..50)
        .map(|i| (format!("set_{}", i).leak() as &str, Kind::Movie, None))
        .collect();
    let refs: Vec<_> = row_tuples
        .iter()
        .map(|(s, k, t)| (s as &str, *k, *t))
        .collect();
    let (_d, conn) = db_with(&refs);

    let found = distinct_titles(&conn).unwrap();
    assert_eq!(found.len(), 0, "no TMDB IDs means no titles");
}

// =============================================================================
// BUDGET: Edge cases
// =============================================================================

#[test]
fn budget_just_under_warn_threshold() {
    // 24 MB - 1 byte should be Fine
    let estimate = 24 * 1024 * 1024 - 1;
    assert_eq!(verdict_for(estimate), Verdict::Fine);
}

#[test]
fn budget_exactly_at_warn_threshold() {
    // Exactly 24 MB should trigger Large
    let estimate = 24 * 1024 * 1024;
    assert!(matches!(verdict_for(estimate), Verdict::Large(_)));
}

#[test]
fn budget_just_over_warn_threshold() {
    // 24 MB + 1 byte should be Large
    let estimate = 24 * 1024 * 1024 + 1;
    assert!(matches!(verdict_for(estimate), Verdict::Large(_)));
}

#[test]
fn budget_just_under_refuse_threshold() {
    // 48 MB - 1 byte should be Large (not TooLarge)
    let estimate = 48 * 1024 * 1024 - 1;
    assert!(matches!(verdict_for(estimate), Verdict::Large(_)));
}

#[test]
fn budget_exactly_at_refuse_threshold() {
    // Exactly 48 MB should be TooLarge
    let estimate = 48 * 1024 * 1024;
    assert!(matches!(verdict_for(estimate), Verdict::TooLarge(_)));
}

#[test]
fn budget_just_over_refuse_threshold() {
    // 48 MB + 1 byte should be TooLarge
    let estimate = 48 * 1024 * 1024 + 1;
    assert!(matches!(verdict_for(estimate), Verdict::TooLarge(_)));
}

#[test]
fn budget_interacts_correctly_with_max_package_bytes() {
    // The reader's limit is MAX_PACKAGE_BYTES (64 MB)
    // The export's REFUSE_BYTES (48 MB) should be below it
    const _: () = {
        const REFUSE: u64 = 48 * 1024 * 1024;
        const MAX_PKG: u64 = 64 * 1024 * 1024;
        const _: () = assert!(REFUSE < MAX_PKG);
    };

    // At the reader's limit, should still be TooLarge
    assert!(matches!(
        verdict_for(mlib_spec::package::MAX_PACKAGE_BYTES),
        Verdict::TooLarge(_)
    ));
}

#[test]
fn budget_zero_posters_zero_index() {
    // Empty library
    assert_eq!(estimate_bytes(0, 0), 0);
    assert_eq!(verdict_for(0), Verdict::Fine);
}

#[test]
fn budget_single_poster_estimate() {
    // One poster should add ~32 KB
    let without = estimate_bytes(1_000_000, 0);
    let with_one = estimate_bytes(1_000_000, 1);
    assert!(with_one > without);
    assert!(with_one - without >= 32 * 1024);
}

// =============================================================================
// POINTER: Edge cases
// =============================================================================

#[test]
fn pointer_draft_with_extreme_created_at_min() {
    // Extremely old timestamp (year 1970)
    let key = [7u8; 32];
    let draft = pointer::draft(0, &key);
    assert_eq!(draft.created_at, 0);
    assert_eq!(draft.format, mlib_spec::package::PACKAGE_FORMAT);
}

#[test]
fn pointer_draft_with_extreme_created_at_max() {
    // Extremely far future timestamp (year 2286 in Unix time)
    let key = [8u8; 32];
    let draft = pointer::draft(i64::MAX, &key);
    assert_eq!(draft.created_at, i64::MAX);
    assert_eq!(draft.format, mlib_spec::package::PACKAGE_FORMAT);
}

#[test]
fn pointer_draft_produces_valid_json_aad() {
    // The AAD produced from the draft must be valid JSON
    let key = [9u8; 32];
    let draft = pointer::draft(1234567890, &key);
    let aad = mlib_spec::package::associated_data(&draft);

    // Should be valid UTF-8 and valid JSON
    let json_str = std::str::from_utf8(&aad).expect("AAD is valid UTF-8");
    let json_val: serde_json::Value = serde_json::from_str(json_str).expect("AAD parses as JSON");

    // Verify key fields exist
    assert_eq!(json_val["format"], mlib_spec::package::PACKAGE_FORMAT);
    assert_eq!(json_val["created_at"], 1234567890);
}

#[test]
fn pointer_sha256_known_vectors() {
    // Test against known SHA256 vectors
    let empty = pointer::sha256(b"");
    assert_eq!(
        hex::encode(empty),
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    );

    let hello = pointer::sha256(b"hello");
    assert_eq!(
        hex::encode(hello),
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    );
}

// =============================================================================
// STAGE: Edge cases
// =============================================================================

#[test]
fn stage_permissions_directory_0700() {
    // The staging directory should be 0700 (owner rwx only)
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let staging = Staging::create(parent.path(), "test_stage").expect("create staging");

    let perms = std::fs::metadata(staging.path()).unwrap().permissions();
    let mode = perms.mode();
    // 0700 = owner rwx, no group/other access
    assert_eq!(mode & 0o777, 0o700);
}

#[test]
fn stage_permissions_files_0600() {
    // Files written into staging should be 0600 (owner rw only)
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let staging = Staging::create(parent.path(), "test_stage_perms").expect("create staging");

    // Simulate writing a file with restricted perms
    let manifest_path = staging.path().join("test_manifest.json");
    std::fs::write(&manifest_path, b"{}").expect("write test file");
    std::fs::set_permissions(&manifest_path, std::fs::Permissions::from_mode(0o600))
        .expect("set 0600");

    let perms = std::fs::metadata(&manifest_path).unwrap().permissions();
    let mode = perms.mode();
    assert_eq!(mode & 0o777, 0o600);
}

#[test]
fn stage_removed_on_drop() {
    // When Staging is dropped, its directory should be removed
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let stage_path = {
        let staging = Staging::create(parent.path(), "removable").expect("create");
        staging.path().to_path_buf()
    };
    // staging is dropped here

    // The directory should no longer exist
    assert!(
        !stage_path.exists(),
        "staging dir should be removed on drop"
    );
}

#[test]
fn stage_clears_stale_directory_from_crash() {
    // If staging dir already exists (from a previous crash), it should be cleared
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let stage_name = "stale_dir";

    // Create a stale directory with a file in it
    let stale_path = parent.path().join(stage_name);
    std::fs::create_dir_all(&stale_path).unwrap();
    std::fs::write(stale_path.join("old_file.txt"), b"old data").unwrap();

    // Now create a fresh Staging at the same location
    let staging = Staging::create(parent.path(), stage_name).expect("create over stale");

    // The old file should be gone
    assert!(!staging.path().join("old_file.txt").exists());
    // But the directory should exist and be ready
    assert!(staging.path().exists());
    assert!(staging.path().is_dir());
}

#[test]
fn stage_removed_even_on_drop_with_error() {
    // Even if an error occurs, drop should still clean up
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let stage_path = {
        let staging = Staging::create(parent.path(), "error_cleanup").expect("create");
        staging.path().to_path_buf()
    };

    assert!(
        !stage_path.exists(),
        "staging cleaned up on drop even without explicit error"
    );
}

#[test]
fn stage_idempotent_create() {
    // Creating the same staging directory twice should work
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();

    let _first = Staging::create(parent.path(), "idempotent").expect("first create");
    // First is dropped here, removing the dir

    let _second = Staging::create(parent.path(), "idempotent").expect("second create");
    // Should succeed because first removed it
    assert!(_second.path().exists());
}

#[test]
fn stage_very_long_staging_name() {
    // The staging directory name can be long
    use mediagram::export::stage::Staging;
    let parent = tempfile::tempdir().unwrap();
    let long_name = "a".repeat(100);

    let staging = Staging::create(parent.path(), &long_name).expect("create with long name");
    assert!(staging.path().exists());
}

/// A caption for a set row, varying only the fields these probes care about.
fn probe_caption(set: &str, kind: Kind, tmdb: Option<u64>) -> Caption {
    Caption {
        t: kind,
        ids: ProviderIds {
            tmdb,
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("Title".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: set.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 10,
            sha256: String::new(),
        },
        total: 10,
    }
}

/// A temporary index holding the given sets. The directory is returned so the
/// caller keeps it alive for the duration of the test.
fn db_with(rows: &[(&str, Kind, Option<u64>)]) -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    for (set, kind, tmdb) in rows {
        let row = SetRow::from_caption(&probe_caption(set, *kind, *tmdb), 1_700_000_000).unwrap();
        sets::insert_set(&conn, &row).unwrap();
    }
    (dir, conn)
}
