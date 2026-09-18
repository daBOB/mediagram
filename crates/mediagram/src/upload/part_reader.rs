//! Reads exactly one byte-range window of a file as an `AsyncRead`, hashing
//! every byte as it passes through. Never buffers more than one read chunk
//! in memory, so uploading a multi-GB part costs a few KB of RAM.

use std::io;
use std::path::Path;
use std::pin::Pin;
use std::task::{Context, Poll};

use sha2::{Digest, Sha256};
use tokio::fs::File;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncSeekExt, ReadBuf, Take};

/// An `AsyncRead` over `[off, off + len)` of one file, feeding a running
/// sha256 with every byte returned. Call [`PartReader::finalize`] only
/// after the reader has been fully drained (reached EOF).
pub struct PartReader {
    inner: Take<File>,
    hasher: Sha256,
    bytes_read: u64,
    /// Shared with whoever wants to watch. Written on every poll, which is
    /// why it is an atomic and not a callback: this sits in the path every
    /// chunk takes, and must cost nothing.
    watcher: Option<std::sync::Arc<std::sync::atomic::AtomicU64>>,
}

impl PartReader {
    /// Opens `path` and seeks to `off`; reads beyond `off + len` return EOF.
    pub async fn open(path: &Path, off: u64, len: u64) -> io::Result<PartReader> {
        let mut file = File::open(path).await?;
        file.seek(io::SeekFrom::Start(off)).await?;
        Ok(PartReader {
            inner: file.take(len),
            hasher: Sha256::new(),
            bytes_read: 0,
            watcher: None,
        })
    }

    /// Reports progress into `counter` as bytes pass through.
    pub fn watched_by(mut self, counter: std::sync::Arc<std::sync::atomic::AtomicU64>) -> Self {
        self.watcher = Some(counter);
        self
    }

    /// Bytes handed to the consumer so far; equals the planned length only if
    /// the file still held the whole window.
    pub fn bytes_read(&self) -> u64 {
        self.bytes_read
    }

    /// Hex sha256 of every byte read so far.
    pub fn finalize(&self) -> String {
        hex::encode(self.hasher.clone().finalize())
    }
}

impl AsyncRead for PartReader {
    fn poll_read(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
        buf: &mut ReadBuf<'_>,
    ) -> Poll<io::Result<()>> {
        // `Take<File>` is `Unpin`, so projecting fields out of `Self` is safe.
        let this = self.get_mut();
        let before = buf.filled().len();
        let result = Pin::new(&mut this.inner).poll_read(cx, buf);
        if let Poll::Ready(Ok(())) = &result {
            this.hasher.update(&buf.filled()[before..]);
            this.bytes_read += (buf.filled().len() - before) as u64;
            if let Some(watcher) = &this.watcher {
                watcher.store(this.bytes_read, std::sync::atomic::Ordering::Relaxed);
            }
        }
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sha2::Sha256 as DirectSha256;
    use tokio::io::AsyncWriteExt;

    async fn write_fixture(bytes: &[u8]) -> tempfile::TempDir {
        let dir = tempfile::tempdir().unwrap();
        let mut f = tokio::fs::File::create(dir.path().join("src.bin"))
            .await
            .unwrap();
        f.write_all(bytes).await.unwrap();
        f.flush().await.unwrap();
        dir
    }

    #[tokio::test]
    async fn hash_matches_direct_sha256_of_the_same_window() {
        let data: Vec<u8> = (0..5 * 1024 * 1024).map(|i| (i % 251) as u8).collect();
        let dir = write_fixture(&data).await;
        let path = dir.path().join("src.bin");

        let (off, len) = (1_048_576u64, 2_097_152u64);
        let mut reader = PartReader::open(&path, off, len).await.unwrap();
        let mut out = Vec::new();
        tokio::io::AsyncReadExt::read_to_end(&mut reader, &mut out)
            .await
            .unwrap();

        assert_eq!(out.len(), len as usize);
        assert_eq!(out, &data[off as usize..(off + len) as usize]);

        let mut expected = DirectSha256::new();
        expected.update(&out);
        assert_eq!(reader.finalize(), hex::encode(expected.finalize()));
    }

    #[tokio::test]
    async fn reads_past_len_return_zero() {
        let dir = write_fixture(&[1, 2, 3, 4, 5, 6, 7, 8]).await;
        let path = dir.path().join("src.bin");

        let mut reader = PartReader::open(&path, 2, 3).await.unwrap();
        let mut out = Vec::new();
        tokio::io::AsyncReadExt::read_to_end(&mut reader, &mut out)
            .await
            .unwrap();
        assert_eq!(out, vec![3, 4, 5]);

        // Draining past `len` keeps returning Ok(0), never an error or more bytes.
        let mut buf = [0u8; 16];
        let n = reader.read(&mut buf).await.unwrap();
        assert_eq!(n, 0);
    }
}
