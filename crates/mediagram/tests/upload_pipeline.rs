//! End-to-end pipeline tests against an in-memory `Transport`, so they run
//! without a Telegram connection: a full 3-part upload, a crash-then-resume
//! that adopts an unrecorded part instead of re-uploading it, and the
//! `PLAYABLE_SQL` invariant before/after completion.

use std::sync::Mutex;

use anyhow::Result;
use mediagram::index::{db, parts, sets};
use mediagram::upload::part_reader::PartReader;
use mediagram::upload::pipeline::run_set;
use mediagram::upload::transport::{Seen, Sent, Transport};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;
use mlib_spec::schema::PLAYABLE_SQL;
use sha2::{Digest, Sha256};
use tokio::io::AsyncReadExt;

const CHAT_ID: i64 = -1_001_234_567_890;

struct FakeMessage {
    message_id: i64,
    doc_id: i64,
    caption: String,
}

/// In-memory stand-in for the channel: records every sent part and can be
/// pre-seeded with a message that "exists on Telegram" without having gone
/// through `send_part`, simulating a crash between upload and `mark_done`.
struct FakeTransport {
    messages: Mutex<Vec<FakeMessage>>,
    next_id: Mutex<i64>,
    send_count: Mutex<u32>,
}

impl FakeTransport {
    fn new() -> Self {
        FakeTransport {
            messages: Mutex::new(Vec::new()),
            next_id: Mutex::new(1),
            send_count: Mutex::new(0),
        }
    }

    fn seed(&self, caption: String) {
        let mut messages = self.messages.lock().unwrap();
        let mut next_id = self.next_id.lock().unwrap();
        let message_id = *next_id;
        *next_id += 1;
        messages.push(FakeMessage {
            message_id,
            doc_id: message_id + 10_000,
            caption,
        });
    }

    fn send_count(&self) -> u32 {
        *self.send_count.lock().unwrap()
    }
}

impl Transport for FakeTransport {
    async fn send_part(
        &self,
        _name: String,
        _mime: &str,
        caption: &Caption,
        human: &str,
        reader: &mut PartReader,
        _len: u64,
    ) -> Result<Sent> {
        *self.send_count.lock().unwrap() += 1;
        // Drain the reader like the real client does, so its hash finalizes.
        let mut buf = Vec::new();
        reader.read_to_end(&mut buf).await?;
        let final_caption = caption.with_part(Part {
            sha256: reader.finalize(),
            ..caption.part.clone()
        });
        let text = mlib_spec::to_text(&final_caption, human)?;

        let mut messages = self.messages.lock().unwrap();
        let mut next_id = self.next_id.lock().unwrap();
        let message_id = *next_id;
        *next_id += 1;
        let doc_id = message_id + 10_000;
        messages.push(FakeMessage {
            message_id,
            doc_id,
            caption: text,
        });
        Ok(Sent { message_id, doc_id })
    }

    async fn recent_messages(&self, limit: usize) -> Result<Vec<Seen>> {
        let messages = self.messages.lock().unwrap();
        Ok(messages
            .iter()
            .rev()
            .take(limit)
            .map(|m| Seen {
                message_id: m.message_id,
                doc_id: Some(m.doc_id),
                caption: m.caption.clone(),
            })
            .collect())
    }

    fn chat_id(&self) -> i64 {
        CHAT_ID
    }
}

fn sample_caption(set_id: &str, total: u64, n: u32) -> Caption {
    Caption {
        cid: None,
        chap: None,
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(603),
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("The Matrix".into()),
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
        set: set_id.to_string(),
        part: Part {
            i: 0,
            n,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    }
}

fn hex_sha256(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    hex::encode(hasher.finalize())
}

fn playable(conn: &rusqlite::Connection, set_id: &str) -> bool {
    conn.query_row(
        &format!("SELECT ({PLAYABLE_SQL}) FROM sets s WHERE s.set_id = ?1"),
        [set_id],
        |row| row.get::<_, bool>(0),
    )
    .unwrap()
}

/// 1 MiB: the smallest valid part size, small enough to exercise multi-part
/// plans against the few-MiB fixtures these tests write.
const PART_SIZE: u64 = 1024 * 1024;

/// Sets up an empty index db plus an inserted set/parts row for `caption`.
async fn seeded_index(
    caption: &Caption,
) -> (
    tempfile::TempDir,
    rusqlite::Connection,
    sets::SetRow,
    Vec<mlib_spec::PartRange>,
) {
    let plan = mlib_spec::plan_parts(caption.total, PART_SIZE).unwrap();
    let set_row = sets::SetRow::from_caption(caption, 1_700_000_000).unwrap();
    let db_dir = tempfile::tempdir().unwrap();
    let mut conn = db::open(db_dir.path()).unwrap();
    {
        let tx = conn.transaction().unwrap();
        sets::insert_set(&tx, &set_row).unwrap();
        parts::insert_parts(&tx, &set_row.set_id, &plan).unwrap();
        tx.commit().unwrap();
    }
    (db_dir, conn, set_row, plan)
}

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
    run_set(&conn, &transport, 0, &set_row, &src).await.unwrap();

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
    assert_eq!(final_row.status, "complete");
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
    parts::mark_done(&conn, &set_row.set_id, 0, CHAT_ID, 501, 10_501, &hash0).unwrap();

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

    run_set(&conn, &transport, 0, &set_row, &src).await.unwrap();

    // Only part 2 needed an actual upload; part 1 was adopted.
    assert_eq!(transport.send_count(), 1);

    let final_row = sets::get_set(&conn, &set_row.set_id).unwrap().unwrap();
    assert_eq!(final_row.status, "complete");

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
    run_set(&conn, &transport, 0, &set_row, &src).await.unwrap();

    assert!(playable(&conn, &set_row.set_id));
}
