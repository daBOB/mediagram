//! End-to-end pipeline tests against an in-memory `Transport`, so they run
//! without a Telegram connection: a full 3-part upload, a crash-then-resume
//! that adopts an unrecorded part instead of re-uploading it, and the
//! `PLAYABLE_SQL` invariant before/after completion.

use mediagram::index::status::SetStatus;
use mediagram::index::{parts, sets};
use mediagram::upload::pipeline::run_set;
use mlib_spec::caption::Part;

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
