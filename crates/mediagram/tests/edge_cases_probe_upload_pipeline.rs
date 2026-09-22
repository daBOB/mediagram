//! Comprehensive edge case probes for the upload path: streaming part upload,
//! PartReader behavior, adoption logic, database constraints, and concurrency.

use mediagram::index::status::SetStatus;
use mediagram::index::{db, parts, sets};
use mediagram::upload::part_reader::PartReader;
use mediagram::upload::pipeline::run_set;
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;
use mlib_spec::schema::PLAYABLE_SQL;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

mod support;
use support::upload::{
    CHAT_ID, FakeTransport, PART_SIZE, hex_sha256, sample_caption, seeded_index,
};

async fn write_fixture(bytes: &[u8]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    let mut f = tokio::fs::File::create(dir.path().join("src.bin"))
        .await
        .unwrap();
    f.write_all(bytes).await.unwrap();
    f.flush().await.unwrap();
    dir
}

// ===== PartReader Edge Cases =====

#[tokio::test]
async fn part_reader_len_zero_reads_no_bytes() {
    let data = vec![1u8, 2, 3, 4, 5];
    let dir = write_fixture(&data).await;
    let path = dir.path().join("src.bin");

    let mut reader = PartReader::open(&path, 0, 0).await.unwrap();
    let mut out = Vec::new();
    reader.read_to_end(&mut out).await.unwrap();
    assert_eq!(out.len(), 0);
    // Hash of empty should be known
    let hash = reader.finalize();
    assert_eq!(hash, hex_sha256(b""));
}

#[tokio::test]
async fn part_reader_window_ending_exactly_at_eof() {
    let data = vec![7u8; 100];
    let dir = write_fixture(&data).await;
    let path = dir.path().join("src.bin");

    // Read exactly the last 50 bytes
    let mut reader = PartReader::open(&path, 50, 50).await.unwrap();
    let mut out = Vec::new();
    reader.read_to_end(&mut out).await.unwrap();

    assert_eq!(out.len(), 50);
    assert_eq!(out, &data[50..100]);
}

#[tokio::test]
async fn part_reader_off_plus_len_beyond_eof() {
    let data = vec![9u8; 100];
    let dir = write_fixture(&data).await;
    let path = dir.path().join("src.bin");

    // Request bytes beyond EOF: off=50, len=100 (but only 50 available)
    let mut reader = PartReader::open(&path, 50, 100).await.unwrap();
    let mut out = Vec::new();
    reader.read_to_end(&mut out).await.unwrap();

    // Should only get 50 bytes, not 100
    assert_eq!(out.len(), 50);
    assert_eq!(out, &data[50..100]);
}

#[tokio::test]
async fn part_reader_tiny_buffer_reads() {
    let data: Vec<u8> = (0..1024).map(|i| (i % 256) as u8).collect();
    let dir = write_fixture(&data).await;
    let path = dir.path().join("src.bin");

    let mut reader = PartReader::open(&path, 10, 100).await.unwrap();
    let mut out = Vec::new();

    // Read with 1-byte buffers
    let mut buf = [0u8; 1];
    loop {
        let n = reader.read(&mut buf).await.unwrap();
        if n == 0 {
            break;
        }
        out.extend_from_slice(&buf[..n]);
    }

    assert_eq!(out, &data[10..110]);
    assert_eq!(reader.finalize(), hex_sha256(&data[10..110]));
}

#[tokio::test]
async fn part_reader_large_buffer_reads() {
    let data: Vec<u8> = (0..10 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let dir = write_fixture(&data).await;
    let path = dir.path().join("src.bin");

    let off = 1_000_000u64;
    let len = 5_000_000u64;

    let mut reader = PartReader::open(&path, off, len).await.unwrap();
    let mut out = Vec::new();

    // Read with large buffers
    let mut buf = vec![0u8; 512 * 1024];
    loop {
        let n = reader.read(&mut buf).await.unwrap();
        if n == 0 {
            break;
        }
        out.extend_from_slice(&buf[..n]);
    }

    assert_eq!(out.len(), len as usize);
    assert_eq!(out, &data[off as usize..(off + len) as usize]);
    assert_eq!(
        reader.finalize(),
        hex_sha256(&data[off as usize..(off + len) as usize])
    );
}

// ===== run_set Edge Cases =====

#[tokio::test]
async fn run_set_transport_send_fails_on_part_1() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST1", data.len() as u64, 3);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    // Manually mark part 0 as done
    let range0 = plan[0];
    let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
    parts::mark_done(&conn, &set_row.set_id, 0, &parts::Landed { chat_id: CHAT_ID, message_id: 501, doc_id: 10_501, sha256: hash0.clone() }).unwrap();

    let transport = FakeTransport::new();
    transport.set_fail_on_part(1);

    let result = run_set(&conn, &transport, 0, &set_row, &src, None).await;
    assert!(result.is_err(), "Expected failure on part 1");

    // Check database state: part 0 marked done, parts 1-2 still pending
    let pending = parts::pending_parts(&conn, &set_row.set_id).unwrap();
    let pending_indices: Vec<u32> = pending.iter().map(|p| p.idx).collect();
    assert_eq!(pending_indices, vec![1, 2]);

    // Set should still be pending
    let final_row = sets::get_set(&conn, &set_row.set_id).unwrap().unwrap();
    assert_eq!(final_row.status, SetStatus::Pending);
}

#[tokio::test]
async fn run_set_duplicate_adopt_first_wins() {
    // When two messages have same set+idx, adoption_map keeps the first one.
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..2 * 1024 * 1024u32)
        .map(|i| ((i * 3) % 256) as u8)
        .collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST2", data.len() as u64, 2);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    // Create two messages for part 0, simulating duplicate adoption candidates
    let range0 = plan[0];
    let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
    let caption0 = caption.with_part(Part {
        i: 0,
        n: 2,
        off: range0.off,
        len: range0.len,
        sha256: hash0.clone(),
    });
    let text0 = mlib_spec::to_text(&caption0, "Movie — part 1/2").unwrap();

    let transport = FakeTransport::new();
    // Seed two messages with the same set+part combo
    transport.seed(text0.clone());
    transport.seed(text0.clone());

    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    // Verify: part 0 was adopted (not re-uploaded), so send_count should be 1 (just part 1)
    assert_eq!(transport.send_count(), 1);
}

#[tokio::test]
async fn run_set_caption_from_different_set_not_adopted() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..2 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST3", data.len() as u64, 2);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    let range0 = plan[0];
    let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);

    // Create a caption for a *different* set_id
    let other_caption = caption.with_part(Part {
        i: 0,
        n: 2,
        off: range0.off,
        len: range0.len,
        sha256: hash0,
    });
    let mut modified = other_caption.clone();
    modified.set = "DIFFERENT_SET_ID_HERE".to_string();
    let text = mlib_spec::to_text(&modified, "Movie — part 1/2").unwrap();

    let transport = FakeTransport::new();
    transport.seed(text);

    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    // All parts should be uploaded (not adopted from different set)
    assert_eq!(transport.send_count(), 2);
}

#[tokio::test]
async fn run_set_malformed_caption_ignored() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..2 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST4", data.len() as u64, 2);
    let (_db_dir, conn, set_row, _plan) = seeded_index(&caption).await;

    let transport = FakeTransport::new();
    // Seed with a caption that cannot be parsed (not mlib format)
    transport.seed("This is not an mlib caption at all".to_string());

    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    // Should upload all parts since the message was unparseable
    assert_eq!(transport.send_count(), 2);
}

#[tokio::test]
async fn run_set_already_complete_is_noop() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("clip.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST5", data.len() as u64, 1);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    // Pre-mark all parts as done
    let range = plan[0];
    let hash = hex_sha256(&data[range.off as usize..(range.off + range.len) as usize]);
    parts::mark_done(&conn, &set_row.set_id, 0, &parts::Landed { chat_id: CHAT_ID, message_id: 999, doc_id: 10_999, sha256: hash.clone() }).unwrap();

    // Pre-mark set as complete
    let set_hash = mlib_spec::set_hash::set_hash(std::slice::from_ref(&hash));
    sets::set_hash_and_complete(&conn, &set_row.set_id, &set_hash).unwrap();

    let transport = FakeTransport::new();
    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    // No uploads should have happened (already complete)
    assert_eq!(transport.send_count(), 0);
}

#[tokio::test]
async fn run_set_deleted_source_file_error() {
    let tmp = tempfile::tempdir().unwrap();
    let src = tmp.path().join("movie.mkv");

    let caption = sample_caption("01J0000000000000000000FST6", 1048576_u64, 1);
    let (_db_dir, conn, set_row, _plan) = seeded_index(&caption).await;

    // Don't create the source file, so it's "deleted"
    let transport = FakeTransport::new();
    let result = run_set(&conn, &transport, 0, &set_row, &src, None).await;

    assert!(result.is_err(), "Expected error when source file missing");
}

// ===== Database Constraint Tests =====

#[tokio::test]
async fn parts_insert_twice_same_set_duplicate_key_error() {
    let caption = sample_caption("01J0000000000000000000FST7", 3 * 1024 * 1024, 3);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    // Try to insert the same parts again
    let result = parts::insert_parts(&conn, &set_row.set_id, &plan);

    assert!(result.is_err(), "Expected UNIQUE constraint violation");
}

#[tokio::test]
async fn list_pending_excludes_complete_sets() {
    let db_dir = tempfile::tempdir().unwrap();
    let mut conn = db::open(db_dir.path()).unwrap();

    // Insert two sets
    let caption1 = sample_caption("01J0000000000000000000FST8", 1024 * 1024, 1);
    let set1 = sets::SetRow::from_caption(&caption1, 1_700_000_000).unwrap();

    let caption2 = sample_caption("01J0000000000000000000FST9", 1024 * 1024, 1);
    let set2 = sets::SetRow::from_caption(&caption2, 1_700_000_001).unwrap();

    {
        let tx = conn.transaction().unwrap();
        sets::insert_set(&tx, &set1).unwrap();
        sets::insert_set(&tx, &set2).unwrap();
        tx.commit().unwrap();
    }

    // Mark one as complete
    sets::set_hash_and_complete(&conn, &set1.set_id, "somehash1234").unwrap();

    // list_pending should only return set2
    let pending = sets::list_pending(&conn).unwrap();
    let pending_ids: Vec<String> = pending.iter().map(|s| s.set_id.clone()).collect();
    assert_eq!(pending_ids, vec!["01J0000000000000000000FST9"]);
}

#[tokio::test]
async fn caption_roundtrip_preserves_part_offsets() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000FST10", data.len() as u64, 3);
    let (_db_dir, _conn, set_row, plan) = seeded_index(&caption).await;

    // For each part, create its caption and verify roundtrip
    for range in &plan {
        let part_caption = caption.with_part(Part {
            i: range.idx,
            n: 3,
            off: range.off,
            len: range.len,
            sha256: hex_sha256(&data[range.off as usize..(range.off + range.len) as usize]),
        });
        let text = mlib_spec::to_text(&part_caption, "Test Movie").unwrap();
        let parsed = mlib_spec::parse(&text).unwrap();

        // Verify roundtrip
        assert_eq!(parsed.set, set_row.set_id);
        assert_eq!(parsed.part.i, range.idx);
        assert_eq!(parsed.part.off, range.off);
        assert_eq!(parsed.part.len, range.len);
        assert_eq!(parsed.total, data.len() as u64);
    }
}

#[tokio::test]
async fn long_title_in_part_name() {
    // Test that part_name handles long titles without truncating incorrectly
    let long_title = "A".repeat(100); // Very long title
    let caption = Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(603),
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some(long_title.clone()),
        year: Some(1999),
        s: None,
        e: None::<Episode>,
        abs: None,
        q: Some("1080p".into()),
        hdr: Some("SDR".into()),
        container: "mkv".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["en".into()],
        slang: vec![],
        dur: Some(8160),
        variant: None,
        set: "01J0000000000000000000FTLT".to_string(),
        part: Part {
            i: 0,
            n: 2,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: 2 * 1024 * 1024,
    };

    let base_name = mlib_spec::part_name::base_name(&caption);
    let part_name = mlib_spec::part_name::part_file_name(&base_name, "mkv", 0, 2);

    // Should not panic or produce empty name
    assert!(!part_name.is_empty());
    // Should contain part info
    assert!(part_name.contains(".p000") || part_name.contains(".mkv"));
}

// ===== Database Concurrency Tests =====

#[tokio::test]
async fn db_open_read_only_parent_directory() {
    // Create a directory structure
    let root = tempfile::tempdir().unwrap();
    let readonly_parent = root.path().join("readonly");
    tokio::fs::create_dir(&readonly_parent).await.unwrap();

    // Make parent read-only
    #[cfg(unix)]
    {
        use std::fs;
        use std::os::unix::fs::PermissionsExt;
        let perms = fs::Permissions::from_mode(0o555);
        fs::set_permissions(&readonly_parent, perms).unwrap();

        let data_dir = readonly_parent.join("data");
        let result = db::open(&data_dir);

        // Should fail because we can't create the data directory
        assert!(result.is_err());

        // Cleanup: restore permissions
        let perms = fs::Permissions::from_mode(0o755);
        fs::set_permissions(&readonly_parent, perms).unwrap();
    }
}

#[tokio::test]
async fn db_concurrent_connections_with_wal() {
    let db_dir = tempfile::tempdir().unwrap();

    // Open two connections in sequence (simulating two processes)
    let conn1 = db::open(db_dir.path()).unwrap();
    let conn2 = db::open(db_dir.path()).unwrap();

    // Both should succeed with WAL mode enabled
    db::set_meta(&conn1, "test_key_1", "value1").unwrap();
    let val = db::get_meta(&conn2, "test_key_1").unwrap();
    assert_eq!(val, Some("value1".to_string()));

    db::set_meta(&conn2, "test_key_2", "value2").unwrap();
    let val = db::get_meta(&conn1, "test_key_2").unwrap();
    assert_eq!(val, Some("value2".to_string()));
}

// ===== PLAYABLE_SQL Edge Cases =====

#[tokio::test]
async fn playable_sql_with_deleted_part_row() {
    let db_dir = tempfile::tempdir().unwrap();
    let mut conn = db::open(db_dir.path()).unwrap();

    let caption = sample_caption("01J0000000000000000000FSPL", 1024 * 1024, 2);
    let set_row = sets::SetRow::from_caption(&caption, 1_700_000_000).unwrap();
    let plan = mlib_spec::plan_parts(caption.total, PART_SIZE).unwrap();

    {
        let tx = conn.transaction().unwrap();
        sets::insert_set(&tx, &set_row).unwrap();
        parts::insert_parts(&tx, &set_row.set_id, &plan).unwrap();
        tx.commit().unwrap();
    }

    // Mark both parts as done
    let data = vec![42u8; 1024 * 1024];
    for range in &plan {
        let hash = hex_sha256(&data[range.off as usize..(range.off + range.len) as usize]);
        parts::mark_done(&conn, &set_row.set_id, range.idx, &parts::Landed { chat_id: CHAT_ID, message_id: 100 + range.idx as i64, doc_id: 10000 + range.idx as i64, sha256: hash.clone() })
        .unwrap();
    }

    // Mark set as complete
    let hashes = parts::done_hashes(&conn, &set_row.set_id).unwrap();
    let set_hash = mlib_spec::set_hash::set_hash(&hashes);
    sets::set_hash_and_complete(&conn, &set_row.set_id, &set_hash).unwrap();

    // Now delete one part row (simulating DB corruption)
    conn.execute(
        "DELETE FROM parts WHERE set_id = ?1 AND idx = ?2",
        rusqlite::params![set_row.set_id, 0],
    )
    .unwrap();

    // PLAYABLE_SQL should return false (incomplete due to deleted row)
    let playable = conn
        .query_row(
            &format!("SELECT ({PLAYABLE_SQL}) FROM sets s WHERE s.set_id = ?1"),
            [&set_row.set_id],
            |row| row.get::<_, bool>(0),
        )
        .unwrap();
    assert!(!playable, "Set with deleted part should not be playable");
}
