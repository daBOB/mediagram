//! An in-memory stand-in for the channel, and the rows an upload runs against.
//!
//! Every upload test needs the same three things: a `Transport` that records
//! what it was handed instead of talking to Telegram, a caption to upload,
//! and an index with the set and its parts already inserted.

use std::sync::Mutex;

use anyhow::Result;
use mediagram::index::rescan::Seen;
use mediagram::index::set_row::SetRow;
use mediagram::index::{db, parts, sets};
use mediagram::upload::part_reader::PartReader;
use mediagram::upload::transport::{Sent, Transport};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;
use mlib_spec::schema::PLAYABLE_SQL;
use sha2::{Digest, Sha256};
use tokio::io::AsyncReadExt;

pub const CHAT_ID: i64 = -1_001_234_567_890;

/// 1 MiB: the smallest valid part size, small enough to exercise multi-part
/// plans against the few-MiB fixtures these tests write.
pub const PART_SIZE: u64 = 1024 * 1024;

pub struct FakeMessage {
    pub message_id: i64,
    pub doc_id: i64,
    pub caption: String,
}

/// In-memory stand-in for the channel: records every sent part, can be
/// pre-seeded with a message that "exists on Telegram" without having gone
/// through `send_part` (simulating a crash between upload and `mark_done`),
/// and can be told to fail one part so a partial upload can be resumed.
pub struct FakeTransport {
    pub messages: Mutex<Vec<FakeMessage>>,
    next_id: Mutex<i64>,
    send_count: Mutex<u32>,
    fail_on_part: Mutex<Option<u32>>,
}

impl FakeTransport {
    pub fn new() -> Self {
        FakeTransport {
            messages: Mutex::new(Vec::new()),
            next_id: Mutex::new(1),
            send_count: Mutex::new(0),
            fail_on_part: Mutex::new(None),
        }
    }

    pub fn seed(&self, caption: String) {
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

    pub fn send_count(&self) -> u32 {
        *self.send_count.lock().unwrap()
    }

    /// Makes `send_part` return an error for this part index, and only that one.
    pub fn set_fail_on_part(&self, idx: u32) {
        *self.fail_on_part.lock().unwrap() = Some(idx);
    }
}

impl Default for FakeTransport {
    fn default() -> Self {
        Self::new()
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
        // Decided before the await: the lock must not be held across one.
        let should_fail = *self.fail_on_part.lock().unwrap() == Some(caption.part.i);

        // Drain the reader like the real client does, so its hash finalizes.
        let mut buf = Vec::new();
        reader.read_to_end(&mut buf).await?;

        if should_fail {
            return Err(anyhow::anyhow!(
                "injected failure on part {}",
                caption.part.i
            ));
        }

        *self.send_count.lock().unwrap() += 1;
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
                sent_at: 0,
            })
            .collect())
    }

    fn chat_id(&self) -> i64 {
        CHAT_ID
    }
}

/// A single-file movie caption of `total` bytes split into `n` parts.
pub fn sample_caption(set_id: &str, total: u64, n: u32) -> Caption {
    Caption {
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

pub fn hex_sha256(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    hex::encode(hasher.finalize())
}

/// Whether the index would offer this set to a player.
pub fn playable(conn: &rusqlite::Connection, set_id: &str) -> bool {
    conn.query_row(
        &format!("SELECT ({PLAYABLE_SQL}) FROM sets s WHERE s.set_id = ?1"),
        [set_id],
        |row| row.get::<_, bool>(0),
    )
    .unwrap()
}

/// An empty index plus the set and part rows `caption` plans out.
pub async fn seeded_index(
    caption: &Caption,
) -> (
    tempfile::TempDir,
    rusqlite::Connection,
    SetRow,
    Vec<mlib_spec::PartRange>,
) {
    let plan = mlib_spec::plan_parts(caption.total, PART_SIZE).unwrap();
    let set_row = SetRow::from_caption(caption, 1_700_000_000);
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
