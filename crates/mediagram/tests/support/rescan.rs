//! The channel messages a rescan reads, without a channel.
//!
//! A rescan's whole input is a list of `Seen` captions, so a fixture is one
//! caption template plus a way to stamp a part block onto it.

use mediagram::index::db;
use mediagram::index::rescan::Seen;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

pub const CHAT_ID: i64 = -1001234567890;

/// When message 0 was sent. [`part_seen`] dates each message this many
/// seconds plus its id, so a later message is a later send, as on a channel.
pub const SENT_BASE: i64 = 1_790_000_000;

pub fn open_db() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

/// One caption template shared by every part of a set.
pub fn template(set_id: &str, part_count: u32, total: u64) -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(42),
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("Dune: Part Two".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: Some("1080p".into()),
        hdr: Some("SDR".into()),
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("aac".into()),
        alang: vec!["en".into()],
        slang: vec![],
        dur: Some(9000),
        variant: None,
        set: set_id.into(),
        part: Part {
            i: 0,
            n: part_count,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    }
}

/// One channel message caption for part `idx`, `len` bytes at `off`.
pub fn part_seen(
    template: &Caption,
    idx: u32,
    off: u64,
    len: u64,
    sha: &str,
    message_id: i64,
    doc_id: i64,
) -> Seen {
    let caption = template.with_part(Part {
        i: idx,
        n: template.part.n,
        off,
        len,
        sha256: sha.into(),
    });
    let text = mlib_spec::to_text(&caption, "").unwrap();
    Seen {
        message_id,
        doc_id: Some(doc_id),
        caption: text,
        sent_at: SENT_BASE + message_id,
    }
}
