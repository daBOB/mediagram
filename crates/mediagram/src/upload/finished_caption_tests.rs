//! The caption text every transport sends once a part's bytes are through.

use tokio::io::AsyncReadExt;

use super::*;
use crate::test_fakes::upload::{hex_sha256, sample_caption};

/// `bytes` written to a file, read through a reader planned for `len` bytes
/// until it reports EOF, as a transport drains it.
async fn drained(bytes: &[u8], len: u64) -> (tempfile::TempDir, PartReader) {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("part.bin");
    std::fs::write(&path, bytes).unwrap();
    let mut reader = PartReader::open(&path, 0, len).await.unwrap();
    reader.read_to_end(&mut Vec::new()).await.unwrap();
    (dir, reader)
}

#[tokio::test]
async fn a_part_that_came_up_short_is_refused() {
    let (_dir, reader) = drained(b"12345", 10).await;
    let caption = sample_caption("setA", 10, 1);

    let err = finished_caption_text(&caption, "The Matrix", &reader, 10).unwrap_err();

    assert!(
        err.to_string().contains("source shrank during upload"),
        "{err:#}"
    );
}

#[tokio::test]
async fn the_caption_carries_the_hash_of_the_bytes_read() {
    let (_dir, reader) = drained(b"12345", 5).await;
    let caption = sample_caption("setA", 5, 1);

    let text = finished_caption_text(&caption, "The Matrix", &reader, 5).unwrap();

    let parsed = mlib_spec::parse(&text).unwrap();
    assert_eq!(parsed.part.sha256, reader.finalize());
    assert_eq!(parsed.part.sha256, hex_sha256(b"12345"));
    assert_eq!(parsed.set, "setA");
}
