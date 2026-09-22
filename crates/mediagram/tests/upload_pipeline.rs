//! End-to-end pipeline tests against an in-memory `Transport`, so they run
//! without a Telegram connection: a full 3-part upload, a crash-then-resume
//! that adopts an unrecorded part instead of re-uploading it, and the
//! `PLAYABLE_SQL` invariant before/after completion.

use mediagram::index::set_row::SetRow;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, parts, sets};
use mediagram::upload::part_reader::PartReader;
use mediagram::upload::pipeline::run_set;
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

mod support;
use support::upload::{CHAT_ID, FakeTransport, hex_sha256, playable, sample_caption, seeded_index};

#[tokio::test]
async fn uploads_all_parts_and_completes_set() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000TST1", data.len() as u64, 3);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;
    assert_eq!(plan.len(), 3);

    let transport = FakeTransport::new();
    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    assert_eq!(transport.send_count(), 3);
    assert!(
        parts::pending_parts(&conn, &set_row.set_id)
            .unwrap()
            .is_empty()
    );

    let messages = transport.messages.lock().unwrap();
    let mut expected_hashes = Vec::new();
    for range in &plan {
        let window = &data[range.off as usize..(range.off + range.len) as usize];
        let expected_hash = hex_sha256(window);
        expected_hashes.push(expected_hash.clone());

        let parsed = mlib_spec::parse(&messages[range.idx as usize].caption).unwrap();
        assert_eq!(parsed.set, set_row.set_id);
        assert_eq!(parsed.part.i, range.idx);
        assert_eq!(parsed.part.off, range.off);
        assert_eq!(parsed.part.len, range.len);
        assert_eq!(parsed.part.sha256, expected_hash);
    }

    let final_row = sets::get_set(&conn, &set_row.set_id).unwrap().unwrap();
    assert_eq!(final_row.status, SetStatus::Complete);
    assert_eq!(
        final_row.set_hash.as_deref(),
        Some(mlib_spec::set_hash::set_hash(&expected_hashes).as_str())
    );
}

#[tokio::test]
async fn resumes_via_adoption_without_duplicate_upload() {
    let tmp = tempfile::tempdir().unwrap();
    let data: Vec<u8> = (0..3 * 1024 * 1024u32)
        .map(|i| ((i * 7) % 256) as u8)
        .collect();
    let src = tmp.path().join("movie.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000TST2", data.len() as u64, 3);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    // Part 0: already recorded done in a prior run.
    let range0 = plan[0];
    let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
    parts::mark_done(&conn, &set_row.set_id, 0, &parts::Landed { chat_id: CHAT_ID, message_id: 501, doc_id: 10_501, sha256: hash0.clone() }).unwrap();

    // Part 1: uploaded to Telegram, but the crash happened before mark_done.
    let range1 = plan[1];
    let hash1 = hex_sha256(&data[range1.off as usize..(range1.off + range1.len) as usize]);
    let caption1 = caption.with_part(Part {
        i: 1,
        n: 3,
        off: range1.off,
        len: range1.len,
        sha256: hash1.clone(),
    });
    let text1 = mlib_spec::to_text(&caption1, "The Matrix (1999) — part 2/3").unwrap();

    let transport = FakeTransport::new();
    transport.seed(text1);

    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    // Only part 2 needed an actual upload; part 1 was adopted.
    assert_eq!(transport.send_count(), 1);

    let final_row = sets::get_set(&conn, &set_row.set_id).unwrap().unwrap();
    assert_eq!(final_row.status, SetStatus::Complete);

    let range2 = plan[2];
    let hash2 = hex_sha256(&data[range2.off as usize..(range2.off + range2.len) as usize]);
    assert_eq!(
        parts::done_hashes(&conn, &set_row.set_id).unwrap(),
        vec![hash0, hash1, hash2]
    );

    let part1 = parts::pending_parts(&conn, &set_row.set_id).unwrap();
    assert!(part1.is_empty());
}

#[tokio::test]
async fn playable_sql_reflects_set_completeness() {
    let tmp = tempfile::tempdir().unwrap();
    let data = vec![7u8; 512 * 1024];
    let src = tmp.path().join("clip.mkv");
    tokio::fs::write(&src, &data).await.unwrap();

    let caption = sample_caption("01J0000000000000000000TST3", data.len() as u64, 1);
    let (_db_dir, conn, set_row, _plan) = seeded_index(&caption).await;

    assert!(!playable(&conn, &set_row.set_id));

    let transport = FakeTransport::new();
    run_set(&conn, &transport, 0, &set_row, &src, None)
        .await
        .unwrap();

    assert!(playable(&conn, &set_row.set_id));
}

/// A set can read as playable only while every one of its part rows is
/// still there; losing one after completion must flip it back to false.
#[tokio::test]
async fn playable_sql_stays_false_when_a_part_row_is_missing_after_completion() {
    let caption = sample_caption("01J0000000000000000000FSPL", 2 * 1024 * 1024, 2);
    let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

    let data = vec![42u8; 2 * 1024 * 1024];
    for range in &plan {
        let hash = hex_sha256(&data[range.off as usize..(range.off + range.len) as usize]);
        parts::mark_done(
            &conn,
            &set_row.set_id,
            range.idx,
            &parts::Landed {
                chat_id: CHAT_ID,
                message_id: 100 + range.idx as i64,
                doc_id: 10000 + range.idx as i64,
                sha256: hash,
            },
        )
        .unwrap();
    }

    let hashes = parts::done_hashes(&conn, &set_row.set_id).unwrap();
    let set_hash = mlib_spec::set_hash::set_hash(&hashes);
    sets::set_hash_and_complete(&conn, &set_row.set_id, &set_hash).unwrap();
    assert!(playable(&conn, &set_row.set_id));

    conn.execute(
        "DELETE FROM parts WHERE set_id = ?1 AND idx = ?2",
        rusqlite::params![set_row.set_id, 0],
    )
    .unwrap();

    assert!(
        !playable(&conn, &set_row.set_id),
        "set with a deleted part row should not be playable"
    );
}

/// [`PartReader`] is the streaming, hashing reader `run_set` hands to
/// `Transport::send_part`; these pin its window and buffering behaviour.
mod part_reader_windows {
    use super::*;
    use tokio::io::{AsyncReadExt, AsyncWriteExt};

    async fn write_fixture(bytes: &[u8]) -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let mut f = tokio::fs::File::create(dir.path().join("src.bin"))
            .await
            .unwrap();
        f.write_all(bytes).await.unwrap();
        f.flush().await.unwrap();
        dir
    }

    /// A zero-length part (possible for an empty trailing part) reads
    /// nothing and hashes as the empty string, rather than erroring.
    #[tokio::test]
    async fn zero_length_window_reads_nothing() {
        let data = vec![1u8, 2, 3, 4, 5];
        let dir = write_fixture(&data).await;
        let path = dir.path().join("src.bin");

        let mut reader = PartReader::open(&path, 0, 0).await.unwrap();
        let mut out = Vec::new();
        reader.read_to_end(&mut out).await.unwrap();
        assert_eq!(out.len(), 0);
        assert_eq!(reader.finalize(), hex_sha256(b""));
    }

    /// A window whose end lands exactly on the file's own EOF still reads
    /// its full declared length, rather than coming up short.
    #[tokio::test]
    async fn window_ending_exactly_at_eof_reads_its_full_length() {
        let data = vec![7u8; 100];
        let dir = write_fixture(&data).await;
        let path = dir.path().join("src.bin");

        let mut reader = PartReader::open(&path, 50, 50).await.unwrap();
        let mut out = Vec::new();
        reader.read_to_end(&mut out).await.unwrap();

        assert_eq!(out.len(), 50);
        assert_eq!(out, &data[50..100]);
    }

    /// A source file shorter than the window it was planned against — the
    /// file changed after planning — yields only the bytes that exist,
    /// instead of erroring or blocking on bytes that will never arrive.
    #[tokio::test]
    async fn window_extending_past_a_truncated_file_clamps_to_what_exists() {
        let data = vec![9u8; 100];
        let dir = write_fixture(&data).await;
        let path = dir.path().join("src.bin");

        let mut reader = PartReader::open(&path, 50, 100).await.unwrap();
        let mut out = Vec::new();
        reader.read_to_end(&mut out).await.unwrap();

        assert_eq!(out.len(), 50);
        assert_eq!(out, &data[50..100]);
    }

    /// The hash accumulates correctly across many `poll_read` calls,
    /// whether the caller reads it in 1-byte or half-megabyte chunks.
    #[tokio::test]
    async fn incremental_reads_assemble_the_same_bytes_and_hash_at_any_buffer_size() {
        let small: Vec<u8> = (0..1024).map(|i| (i % 256) as u8).collect();
        let dir = write_fixture(&small).await;
        let path = dir.path().join("src.bin");
        let mut reader = PartReader::open(&path, 10, 100).await.unwrap();
        let mut out = Vec::new();
        let mut buf = [0u8; 1];
        loop {
            let n = reader.read(&mut buf).await.unwrap();
            if n == 0 {
                break;
            }
            out.extend_from_slice(&buf[..n]);
        }
        assert_eq!(out, &small[10..110]);
        assert_eq!(reader.finalize(), hex_sha256(&small[10..110]));

        let large: Vec<u8> = (0..10 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let dir = write_fixture(&large).await;
        let path = dir.path().join("src.bin");
        let (off, len) = (1_000_000u64, 5_000_000u64);
        let mut reader = PartReader::open(&path, off, len).await.unwrap();
        let mut out = Vec::new();
        let mut buf = vec![0u8; 512 * 1024];
        loop {
            let n = reader.read(&mut buf).await.unwrap();
            if n == 0 {
                break;
            }
            out.extend_from_slice(&buf[..n]);
        }
        let expected = &large[off as usize..(off + len) as usize];
        assert_eq!(out, expected);
        assert_eq!(reader.finalize(), hex_sha256(expected));
    }
}

/// `run_set`'s handling of a crash, a failure, or a message that looks
/// like a part but is not one it should adopt.
mod resume_and_recovery {
    use super::*;

    /// A part that fails to send leaves every part before it marked done,
    /// every part from it onward still pending, and the set itself pending.
    #[tokio::test]
    async fn a_failed_part_leaves_earlier_parts_done_and_the_set_pending() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let src = tmp.path().join("movie.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST1", data.len() as u64, 3);
        let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

        let range0 = plan[0];
        let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
        parts::mark_done(&conn, &set_row.set_id, 0, &parts::Landed { chat_id: CHAT_ID, message_id: 501, doc_id: 10_501, sha256: hash0 }).unwrap();

        let transport = FakeTransport::new();
        transport.set_fail_on_part(1);

        let result = run_set(&conn, &transport, 0, &set_row, &src, None).await;
        assert!(result.is_err(), "expected failure on part 1");

        let pending = parts::pending_parts(&conn, &set_row.set_id).unwrap();
        let pending_indices: Vec<u32> = pending.iter().map(|p| p.idx).collect();
        assert_eq!(pending_indices, vec![1, 2]);

        let final_row = sets::get_set(&conn, &set_row.set_id).unwrap().unwrap();
        assert_eq!(final_row.status, SetStatus::Pending);
    }

    /// When two channel messages both claim the same set and part index,
    /// only the first is adopted; the rest of the set uploads normally.
    #[tokio::test]
    async fn two_messages_claiming_the_same_part_adopt_only_the_first() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..2 * 1024 * 1024u32)
            .map(|i| ((i * 3) % 256) as u8)
            .collect();
        let src = tmp.path().join("movie.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST2", data.len() as u64, 2);
        let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

        let range0 = plan[0];
        let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
        let caption0 = caption.with_part(Part {
            i: 0,
            n: 2,
            off: range0.off,
            len: range0.len,
            sha256: hash0,
        });
        let text0 = mlib_spec::to_text(&caption0, "Movie — part 1/2").unwrap();

        let transport = FakeTransport::new();
        transport.seed(text0.clone());
        transport.seed(text0);

        run_set(&conn, &transport, 0, &set_row, &src, None)
            .await
            .unwrap();

        // Part 0 was adopted from the first duplicate; only part 1 was sent.
        assert_eq!(transport.send_count(), 1);
    }

    /// A caption that parses cleanly but names a different set is not
    /// treated as an existing part of this one; every part still uploads.
    #[tokio::test]
    async fn a_caption_from_another_set_is_never_adopted() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..2 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let src = tmp.path().join("movie.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST3", data.len() as u64, 2);
        let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

        let range0 = plan[0];
        let hash0 = hex_sha256(&data[range0.off as usize..(range0.off + range0.len) as usize]);
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

        assert_eq!(transport.send_count(), 2);
    }

    /// A channel message that cannot be parsed as an mlib caption at all is
    /// simply ignored as an adoption candidate, not treated as an error.
    #[tokio::test]
    async fn an_unparseable_message_is_ignored_and_every_part_still_uploads() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..2 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let src = tmp.path().join("movie.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST4", data.len() as u64, 2);
        let (_db_dir, conn, set_row, _plan) = seeded_index(&caption).await;

        let transport = FakeTransport::new();
        transport.seed("This is not an mlib caption at all".to_string());

        run_set(&conn, &transport, 0, &set_row, &src, None)
            .await
            .unwrap();

        assert_eq!(transport.send_count(), 2);
    }

    /// A set already marked complete is a no-op: `run_set` sends nothing,
    /// even though its parts and source file are still there.
    #[tokio::test]
    async fn a_set_already_marked_complete_uploads_nothing() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let src = tmp.path().join("clip.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST5", data.len() as u64, 1);
        let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

        let range = plan[0];
        let hash = hex_sha256(&data[range.off as usize..(range.off + range.len) as usize]);
        parts::mark_done(&conn, &set_row.set_id, 0, &parts::Landed { chat_id: CHAT_ID, message_id: 999, doc_id: 10_999, sha256: hash.clone() }).unwrap();

        let set_hash = mlib_spec::set_hash::set_hash(std::slice::from_ref(&hash));
        sets::set_hash_and_complete(&conn, &set_row.set_id, &set_hash).unwrap();

        let transport = FakeTransport::new();
        run_set(&conn, &transport, 0, &set_row, &src, None)
            .await
            .unwrap();

        assert_eq!(transport.send_count(), 0);
    }

    /// A source file that no longer exists at the recorded path fails the
    /// run before any part is sent, rather than uploading a partial set.
    #[tokio::test]
    async fn a_missing_source_file_fails_before_any_upload() {
        let tmp = tempfile::tempdir().unwrap();
        let src = tmp.path().join("movie.mkv");

        let caption = sample_caption("01J0000000000000000000FST6", 1048576_u64, 1);
        let (_db_dir, conn, set_row, _plan) = seeded_index(&caption).await;

        // The source file is never created, so it is "deleted" from run_set's view.
        let transport = FakeTransport::new();
        let result = run_set(&conn, &transport, 0, &set_row, &src, None).await;

        assert!(result.is_err(), "expected error when source file missing");
    }
}

/// Index-layer invariants `run_set` relies on but does not exercise
/// end-to-end: uniqueness, scheduling, and the sqlite connection itself.
mod index_and_storage {
    use super::*;

    /// Planning the same set's parts twice hits the index's UNIQUE
    /// constraint instead of silently duplicating rows.
    #[tokio::test]
    async fn inserting_the_same_set_s_parts_twice_hits_the_unique_constraint() {
        let caption = sample_caption("01J0000000000000000000FST7", 3 * 1024 * 1024, 3);
        let (_db_dir, conn, set_row, plan) = seeded_index(&caption).await;

        let result = parts::insert_parts(&conn, &set_row.set_id, &plan);

        assert!(result.is_err(), "expected UNIQUE constraint violation");
    }

    /// `list_pending` returns only the sets not yet marked complete, even
    /// when a completed and a pending set share the same index.
    #[tokio::test]
    async fn list_pending_excludes_sets_already_marked_complete() {
        let db_dir = tempfile::tempdir().unwrap();
        let mut conn = db::open(db_dir.path()).unwrap();

        let caption1 = sample_caption("01J0000000000000000000FST8", 1024 * 1024, 1);
        let set1 = SetRow::from_caption(&caption1, 1_700_000_000).unwrap();

        let caption2 = sample_caption("01J0000000000000000000FST9", 1024 * 1024, 1);
        let set2 = SetRow::from_caption(&caption2, 1_700_000_001).unwrap();

        {
            let tx = conn.transaction().unwrap();
            sets::insert_set(&tx, &set1).unwrap();
            sets::insert_set(&tx, &set2).unwrap();
            tx.commit().unwrap();
        }

        sets::set_hash_and_complete(&conn, &set1.set_id, "somehash1234").unwrap();

        let pending = sets::list_pending(&conn).unwrap();
        let pending_ids: Vec<String> = pending.iter().map(|s| s.set_id.clone()).collect();
        assert_eq!(pending_ids, vec!["01J0000000000000000000FST9"]);
    }

    /// Opening the index under a read-only parent directory fails cleanly
    /// instead of panicking, since the data directory cannot be created.
    #[tokio::test]
    async fn opening_the_index_under_a_read_only_parent_fails() {
        let root = tempfile::tempdir().unwrap();
        let readonly_parent = root.path().join("readonly");
        tokio::fs::create_dir(&readonly_parent).await.unwrap();

        #[cfg(unix)]
        {
            use std::fs;
            use std::os::unix::fs::PermissionsExt;
            let perms = fs::Permissions::from_mode(0o555);
            fs::set_permissions(&readonly_parent, perms).unwrap();

            let data_dir = readonly_parent.join("data");
            let result = db::open(&data_dir);
            assert!(result.is_err());

            let perms = fs::Permissions::from_mode(0o755);
            fs::set_permissions(&readonly_parent, perms).unwrap();
        }
    }

    /// Two connections opened against the same index in sequence, standing
    /// in for two processes, each see the other's committed writes under WAL.
    #[tokio::test]
    async fn two_connections_in_wal_mode_see_each_other_s_writes() {
        let db_dir = tempfile::tempdir().unwrap();

        let conn1 = db::open(db_dir.path()).unwrap();
        let conn2 = db::open(db_dir.path()).unwrap();

        db::set_meta(&conn1, "test_key_1", "value1").unwrap();
        let val = db::get_meta(&conn2, "test_key_1").unwrap();
        assert_eq!(val, Some("value1".to_string()));

        db::set_meta(&conn2, "test_key_2", "value2").unwrap();
        let val = db::get_meta(&conn1, "test_key_2").unwrap();
        assert_eq!(val, Some("value2".to_string()));
    }
}

/// Caption text and part file naming, exercised here against the same
/// plans `run_set` produces rather than hand-built captions.
mod caption_and_naming {
    use super::*;

    /// Every part's own caption, generated from a real plan, roundtrips its
    /// offset, length and total through `to_text`/`parse` unchanged.
    #[tokio::test]
    async fn each_part_s_caption_roundtrips_its_offset_and_length() {
        let tmp = tempfile::tempdir().unwrap();
        let data: Vec<u8> = (0..3 * 1024 * 1024u32).map(|i| (i % 256) as u8).collect();
        let src = tmp.path().join("movie.mkv");
        tokio::fs::write(&src, &data).await.unwrap();

        let caption = sample_caption("01J0000000000000000000FST10", data.len() as u64, 3);
        let (_db_dir, _conn, set_row, plan) = seeded_index(&caption).await;

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

            assert_eq!(parsed.set, set_row.set_id);
            assert_eq!(parsed.part.i, range.idx);
            assert_eq!(parsed.part.off, range.off);
            assert_eq!(parsed.part.len, range.len);
            assert_eq!(parsed.total, data.len() as u64);
        }
    }

    /// A very long title still produces a non-empty, part-numbered file
    /// name, rather than truncating to nothing or panicking on the budget.
    #[tokio::test]
    async fn a_very_long_title_still_produces_a_usable_part_file_name() {
        let long_title = "A".repeat(100);
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
            title: Some(long_title),
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

        assert!(!part_name.is_empty());
        assert!(part_name.contains(".p000") || part_name.contains(".mkv"));
    }
}
