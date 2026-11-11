//! A title's image file and its type. The file may sit where the host cannot
//! read it, in another app's storage; the embedder then supplies a copy.

use std::future::Future;
use std::path::{Path, PathBuf};
use std::pin::Pin;
use std::sync::Arc;

use crate::titles::{ImageKind, TitlePool};

/// Maps a `GameTitle.key` and which image to a readable copy of that image,
/// `None` when the embedder has none.
pub type ImageLookup =
    Arc<dyn Fn(String, ImageKind) -> Pin<Box<dyn Future<Output = Option<PathBuf>> + Send>> + Send + Sync>;

/// The title's own file, else the embedder's copy of it. A title without
/// that image has nothing to copy, so the embedder is not asked.
pub(crate) async fn title_image(
    pool: &TitlePool,
    supply: Option<&ImageLookup>,
    i: usize,
    kind: ImageKind,
) -> Option<(PathBuf, Vec<u8>)> {
    let own = pool.image_path(i, kind)?;
    if let Ok(bytes) = tokio::fs::read(own).await {
        return Some((own.to_path_buf(), bytes));
    }
    let copy = supply?(pool.key_of(i)?.to_string(), kind).await?;
    let bytes = tokio::fs::read(&copy).await.ok()?;
    Some((copy, bytes))
}

/// By the file's extension, else by its first bytes: a copy the embedder
/// supplies may be named without one.
pub(crate) fn content_type(path: &Path, bytes: &[u8]) -> Option<&'static str> {
    match path.extension().and_then(|e| e.to_str()) {
        Some("png") => Some("image/png"),
        Some("jpg" | "jpeg") => Some("image/jpeg"),
        Some("webp") => Some("image/webp"),
        _ if bytes.starts_with(b"\x89PNG") => Some("image/png"),
        _ if bytes.starts_with(&[0xFF, 0xD8, 0xFF]) => Some("image/jpeg"),
        _ if bytes.starts_with(b"RIFF") && bytes.get(8..12) == Some(b"WEBP".as_slice()) => Some("image/webp"),
        _ => None,
    }
}
