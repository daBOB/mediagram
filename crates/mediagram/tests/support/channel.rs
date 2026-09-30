//! An in-memory channel for `channel_index`, and the indexes to put in it.
//!
//! Models only what `ChannelRemote` promises: messages with a caption, an
//! optional document, a pin flag and whether the channel itself posted them.
//! The knobs are the ways a real channel has misbehaved: another machine
//! publishing mid-publish, an unpin refused, an unpin that reports success
//! and changes nothing, a lookup that fails. Its calls yield, as a network
//! call would, so two publishes started together really do interleave.

use std::collections::{HashMap, HashSet};
use std::path::Path;
use std::sync::Mutex;

use anyhow::{Result, bail};
use mediagram::channel_index::remote::{Candidate, ChannelRemote, Unpin};
use mediagram::index::{db, snapshot};
use rusqlite::Connection;

pub const CHAT_ID: i64 = -1_009_876_543_210;

pub struct Msg {
    pub id: i32,
    pub caption: String,
    pub document: Option<Vec<u8>>,
    pub pinned: bool,
    pub own_post: bool,
}

#[derive(Default)]
pub struct Channel {
    pub messages: Vec<Msg>,
    pub downloads: usize,
    pub sends: usize,
    pub unpin_fails: HashSet<i32>,
    pub unpin_lies: HashSet<i32>,
    pub captions_fail: bool,
    pub send_fails: bool,
}

impl Channel {
    pub fn post(
        &mut self,
        caption: String,
        document: Option<Vec<u8>>,
        pinned: bool,
        own_post: bool,
    ) -> i32 {
        let id = i32::try_from(self.messages.len()).unwrap() + 1;
        self.messages.push(Msg {
            id,
            caption,
            document,
            pinned,
            own_post,
        });
        id
    }

    /// Another machine publishing `snapshot`, pushed at `at`.
    pub fn publish(&mut self, snapshot: Vec<u8>, at: i64) -> i32 {
        self.post(
            mlib_spec::index_caption::render(at, 0),
            Some(snapshot),
            true,
            true,
        )
    }

    /// Another machine publishing `snapshot` under a caption claiming
    /// `schema` — for pinning the guard that refuses one newer than this
    /// build's own `mlib_spec::schema::SCHEMA_VERSION`, without a real
    /// migration to reach that schema.
    pub fn publish_with_schema(&mut self, snapshot: Vec<u8>, at: i64, schema: i64) -> i32 {
        let caption = format!(
            "{}\n{{\"pushed_at\":{at},\"schema\":{schema},\"sets\":0,\"uploader\":\"9.9.9\"}}",
            mlib_spec::index_caption::MARKER
        );
        self.post(caption, Some(snapshot), true, true)
    }

    /// Every pinned index message, oldest first.
    pub fn pinned_indexes(&self) -> Vec<i32> {
        self.messages
            .iter()
            .filter(|m| m.pinned && mlib_spec::index_caption::is_index(&m.caption))
            .map(|m| m.id)
            .collect()
    }

    fn get(&self, id: i32) -> Option<&Msg> {
        self.messages.iter().find(|m| m.id == id)
    }

    fn get_mut(&mut self, id: i32) -> Option<&mut Msg> {
        self.messages.iter_mut().find(|m| m.id == id)
    }
}

type Hook = Box<dyn FnOnce(&mut Channel) + Send>;

#[derive(Default)]
pub struct FakeChannel {
    state: Mutex<Channel>,
    hooks: Mutex<HashMap<usize, Hook>>,
    candidate_calls: Mutex<usize>,
}

impl FakeChannel {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn with<T>(&self, f: impl FnOnce(&mut Channel) -> T) -> T {
        f(&mut self.state.lock().unwrap())
    }

    /// Runs `hook` just before the `n`th look for the channel index
    /// (1-based) answers.
    pub fn on_look(&self, n: usize, hook: impl FnOnce(&mut Channel) + Send + 'static) {
        self.hooks.lock().unwrap().insert(n, Box::new(hook));
    }

    pub fn document(&self, id: i32) -> Vec<u8> {
        self.with(|c| {
            c.get(id)
                .and_then(|m| m.document.clone())
                .expect("a document")
        })
    }
}

impl ChannelRemote for FakeChannel {
    /// The pins plus every index snapshot, pinned or not: what the pinned
    /// search and the marker search return together.
    async fn candidates(&self) -> Result<Vec<Candidate>> {
        tokio::task::yield_now().await;
        let n = {
            let mut calls = self.candidate_calls.lock().unwrap();
            *calls += 1;
            *calls
        };
        if let Some(hook) = self.hooks.lock().unwrap().remove(&n) {
            self.with(hook);
        }
        Ok(self.with(|c| {
            c.messages
                .iter()
                .filter(|m| m.pinned || mlib_spec::index_caption::is_index(&m.caption))
                .map(|m| Candidate {
                    id: m.id,
                    caption: m.caption.clone(),
                    own_post: m.own_post,
                })
                .collect()
        }))
    }

    async fn download(&self, id: i32) -> Result<Option<Vec<u8>>> {
        self.with(|c| {
            c.downloads += 1;
            Ok(c.get(id).and_then(|m| m.document.clone()))
        })
    }

    async fn captions(&self, ids: &[i32]) -> Result<HashMap<i32, String>> {
        if self.with(|c| c.captions_fail) {
            bail!("the channel did not answer");
        }
        Ok(self.with(|c| {
            ids.iter()
                .filter_map(|id| c.get(*id).map(|m| (*id, m.caption.clone())))
                .collect()
        }))
    }

    fn chat_id(&self) -> i64 {
        CHAT_ID
    }

    async fn send_document(&self, bytes: &[u8], _name: &str, _mime: &str, caption: &str) -> Result<i32> {
        tokio::task::yield_now().await;
        if self.with(|c| c.send_fails) {
            bail!("the channel refused the document");
        }
        let bytes = bytes.to_vec();
        Ok(self.with(|c| {
            c.sends += 1;
            c.post(caption.to_string(), Some(bytes), false, true)
        }))
    }

    async fn pin(&self, id: i32) -> Result<()> {
        self.with(|c| match c.get_mut(id) {
            Some(m) => {
                m.pinned = true;
                Ok(())
            }
            None => bail!("no message {id} to pin"),
        })
    }

    async fn unpin(&self, id: i32) -> Result<Unpin> {
        self.with(|c| {
            if c.unpin_fails.contains(&id) {
                bail!("the channel refused to unpin {id}");
            }
            let lies = c.unpin_lies.contains(&id);
            match c.get_mut(id) {
                Some(m) if m.pinned => {
                    m.pinned = lies;
                    Ok(Unpin::Sent)
                }
                _ => Ok(Unpin::Gone),
            }
        })
    }

    async fn is_pinned(&self, id: i32) -> Result<bool> {
        Ok(self.with(|c| c.get(id).is_some_and(|m| m.pinned)))
    }
}

/// An index holding `sets`, each complete with one part posted to `channel`.
pub fn index_in(dir: &Path, channel: &FakeChannel, sets: &[&str]) -> Connection {
    let conn = db::open(dir).unwrap();
    for set in sets {
        let message =
            channel.with(|c| c.post(format!("part of {set}"), Some(vec![0]), false, true));
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, title)
             VALUES (?1, 'movie', 'mkv', 50, 1, 'complete', 1700000000, 4, ?1)",
            [set],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
             VALUES (?1, 0, 0, 50, ?2, ?3, ?3, 'deadbeef', 'done')",
            rusqlite::params![set, CHAT_ID, message],
        )
        .unwrap();
    }
    conn
}

/// Another machine's index holding `sets`, as the bytes it would publish.
pub fn snapshot_of(channel: &FakeChannel, sets: &[&str]) -> Vec<u8> {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_in(dir.path(), channel, sets);
    let path = dir.path().join("snapshot.db");
    snapshot::snapshot_to(&conn, &path).unwrap();
    std::fs::read(path).unwrap()
}

/// The set ids an index holds, sorted.
pub fn set_ids(conn: &Connection) -> Vec<String> {
    let mut stmt = conn
        .prepare("SELECT set_id FROM sets ORDER BY set_id")
        .unwrap();
    stmt.query_map([], |row| row.get(0))
        .unwrap()
        .map(Result::unwrap)
        .collect()
}

/// The set ids a published snapshot holds, sorted.
pub fn sets_in(document: &[u8]) -> Vec<String> {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("published.db");
    std::fs::write(&path, document).unwrap();
    set_ids(&mediagram::index::sqlite_init::open(&path).unwrap())
}
