//! Streaming SHA-256 hashing of a part's downloaded chunks.

use anyhow::{Context, Result, bail};
use grammers_client::client::DownloadIter;
use sha2::{Digest, Sha256};

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

/// Returns the lowercase hex SHA-256 of every chunk in `chunks`. Nothing
/// beyond the current chunk is ever held in memory, so this is safe on
/// multi-gigabyte parts.
///
/// No retry here: `grammers-client` 0.10's `DownloadIter::next` leaves its
/// internal state emptied after any failed chunk request (`files.rs`), so a
/// retry would silently resume as an *end of stream* instead of re-fetching
/// the chunk — that would produce a wrong, truncated hash instead of a loud
/// error. A transient failure therefore surfaces as an error here, and the
/// caller re-runs `verify --full` to try the part again from the start.
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
