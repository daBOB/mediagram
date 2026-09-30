//! Recording a set's subtitle bundle in the index: where the bundle message
//! lives and which tracks it holds, so a reader can offer a track before it
//! fetches the file.

use anyhow::{Context, Result};
use mlib_spec::subtitle_bundle::BundleTrack;
use rusqlite::{Connection, OptionalExtension, params};

use crate::index::pins;

/// Where a bundle was sent, and what it must hash to when read back.
pub struct FileRef {
    pub chat_id: i64,
    pub message_id: i32,
    pub bytes: u64,
    pub sha256: String,
}

/// Replaces the set's bundle record in one transaction. The bundle holds what
/// the inline subtitle rows did, so those go, and the publish this owes is
/// recorded with it: a crash after this leaves it owed.
pub fn record(
    conn: &Connection,
    set_id: &str,
    file: &FileRef,
    tracks: &[BundleTrack],
) -> Result<()> {
    let tx = conn
        .unchecked_transaction()
        .context("starting the subtitle transaction")?;
    tx.execute("DELETE FROM subtitle_tracks WHERE set_id = ?1", [set_id])
        .context("clearing the old tracks")?;
    tx.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6)
         ON CONFLICT(set_id) DO UPDATE SET chat_id = excluded.chat_id,
           message_id = excluded.message_id, bytes = excluded.bytes,
           sha256 = excluded.sha256, uploaded_at = excluded.uploaded_at",
        params![
            set_id,
            file.chat_id,
            file.message_id,
            file.bytes,
            file.sha256,
            crate::clock::now_unix()
        ],
    )
    .with_context(|| format!("recording the bundle of {set_id}"))?;
    for (position, track) in tracks.iter().enumerate() {
        tx.execute(
            "INSERT INTO subtitle_tracks(set_id, track, lang, forced, sdh, label)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6)",
            params![
                set_id,
                position as i64,
                track.lang,
                track.forced,
                track.sdh,
                track.label
            ],
        )
        .context("recording a track")?;
    }
    tx.execute(
        "DELETE FROM assets WHERE set_id = ?1 AND kind = 'subtitle'",
        [set_id],
    )
    .context("dropping the inline subtitles")?;
    pins::owe_publish(&tx)?;
    tx.commit().context("committing the subtitle record")
}

/// The message holding the set's bundle, if it has one.
pub fn bundle_message(conn: &Connection, set_id: &str) -> Result<Option<i64>> {
    conn.query_row(
        "SELECT message_id FROM subtitle_files WHERE set_id = ?1",
        [set_id],
        |row| row.get(0),
    )
    .optional()
    .with_context(|| format!("reading the bundle message of {set_id}"))
}

#[cfg(test)]
#[path = "subtitles_tests.rs"]
mod tests;
