//! Batched Telegram message retrieval and streaming SHA-256 hashing.

use std::collections::HashMap;

use anyhow::{Context, Result, bail};
use grammers_client::Client;
use grammers_client::client::DownloadIter;
use grammers_client::media::Document;
use grammers_client::message::Message;
use grammers_session::types::PeerRef;
use sha2::{Digest, Sha256};

use crate::telegram::retry::with_retry;

/// Telegram returns at most 100 messages per `get_messages_by_id` call.
const MESSAGE_BATCH: usize = 100;

/// Fetches every message id in `ids` (batched to Telegram's 100-per-call
/// limit) and returns those that still exist, keyed by id. A plain read, so
/// ordinary retry applies.
pub async fn fetch_messages(
    client: &Client,
    channel: PeerRef,
    ids: &[i32],
    max_attempts: u32,
) -> Result<HashMap<i32, Message>> {
    let mut map = HashMap::with_capacity(ids.len());
    for chunk in ids.chunks(MESSAGE_BATCH) {
        let chunk_vec = chunk.to_vec();
        let results = with_retry(max_attempts, || {
            let chunk_vec = chunk_vec.clone();
            async move { client.get_messages_by_id(channel, &chunk_vec).await }
        })
        .await
        .context("fetching part messages by id")?;
        for (id, message) in chunk.iter().zip(results) {
            if let Some(message) = message {
                map.insert(*id, message);
            }
        }
    }
    Ok(map)
}

/// Downloads `document` chunk by chunk via `iter_download` and returns the
/// lowercase hex SHA-256 of the concatenated bytes. Nothing beyond the
/// current chunk is ever held in memory, so this is safe on multi-gigabyte
/// parts.
///
/// No retry here: `grammers-client` 0.10's `DownloadIter::next` leaves its
/// internal state emptied after any failed chunk request (`files.rs`), so a
/// retry would silently resume as an *end of stream* instead of re-fetching
/// the chunk — that would produce a wrong, truncated hash instead of a loud
/// error. A transient failure therefore surfaces as an error here, and the
/// caller re-runs `verify --full` to try the part again from the start.
pub async fn hash_document(
    client: &Client,
    document: &Document,
    expected_len: u64,
) -> Result<String> {
    hash_chunks(client.iter_download(document), expected_len).await
}

/// One chunk at a time, with failures propagated instead of retried as EOF.
pub(super) trait ChunkSource {
    async fn next(&mut self) -> Result<Option<Vec<u8>>>;
}

impl ChunkSource for DownloadIter {
    async fn next(&mut self) -> Result<Option<Vec<u8>>> {
        DownloadIter::next(self)
            .await
            .context("downloading part chunk for hashing")
    }
}

pub(super) async fn hash_chunks(mut chunks: impl ChunkSource, expected_len: u64) -> Result<String> {
    let mut hasher = Sha256::new();
    let mut hashed = 0u64;
    while let Some(chunk) = chunks.next().await? {
        hashed = hashed.saturating_add(chunk.len() as u64);
        hasher.update(&chunk);
    }
    // grammers ends the stream on any short chunk and advances its offset by
    // the request limit rather than the bytes received, so a truncated
    // download would otherwise be reported as a hash mismatch, i.e. as data
    // corruption on Telegram. Fail honestly instead.
    if hashed != expected_len {
        bail!("download ended after {hashed} of {expected_len} bytes");
    }
    Ok(hex::encode(hasher.finalize()))
}
