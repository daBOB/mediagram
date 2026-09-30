//! The summary sitting next to a lesson's video.
//!
//! A transcription leaves several forms of the same words beside each video:
//! `.vtt`, `.srt`, `.txt`, and Whisper's `.json` and `.tsv` working files.
//! The subtitles travel as a bundle (`crate::subtitles`); the `.txt` and the
//! working files stay behind.
//!
//! A summary is optional and is not something a transcriber produces, so it
//! carries its own suffix rather than competing with the transcript for
//! `.txt`.

use std::path::Path;

use crate::index::assets::{self, MAX_ASSET_BYTES};

/// Suffixes a summary may use, in the order they are looked for.
const SUMMARY_SUFFIXES: &[&str] = &[".summary.md", ".summary.txt"];

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Sidecars {
    pub summary: Option<String>,
}

/// Reads the sidecars beside `video`, if any.
///
/// Missing files are ordinary: most lessons have no summary. Only a file
/// that exists and cannot be used is worth a word, and that word is a
/// warning rather than an error — a lesson is still worth uploading without
/// its summary.
pub fn find_sidecars(video: &Path) -> Sidecars {
    let Some(stem) = base_stem(video) else {
        return Sidecars::default();
    };
    let folder = video.parent().unwrap_or(Path::new("."));

    let summary = SUMMARY_SUFFIXES
        .iter()
        .find_map(|suffix| read_text(&folder.join(format!("{stem}{suffix}"))));

    Sidecars { summary }
}

/// The stem a lesson's sidecars are named after.
///
/// A faststart remux is written as `name.faststart.mp4` beside the original,
/// and its sidecars still sit under `name`.
fn base_stem(video: &Path) -> Option<String> {
    let stem = video.file_stem()?.to_string_lossy().to_string();
    Some(
        stem.strip_suffix(".faststart")
            .map(str::to_string)
            .unwrap_or(stem),
    )
}

/// Reads a file as text, or `None` if it is missing, too large, or not UTF-8.
///
/// Size is checked before reading: the point of the limit is not to load the
/// file in the first place.
fn read_text(path: &Path) -> Option<String> {
    let metadata = match std::fs::metadata(path) {
        Ok(metadata) => metadata,
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return None,
        Err(err) => {
            tracing::warn!("skipping {}: {err}", path.display());
            return None;
        }
    };
    if metadata.len() > MAX_ASSET_BYTES as u64 {
        tracing::warn!(
            "skipping {}: {} bytes, over the {MAX_ASSET_BYTES}-byte limit",
            path.display(),
            metadata.len()
        );
        return None;
    }
    match std::fs::read_to_string(path) {
        Ok(text) => Some(text),
        Err(err) => {
            tracing::warn!("skipping {}: {err}", path.display());
            None
        }
    }
}

/// Stores the summary sitting beside a video, if any.
///
/// Read from the source the user named rather than from a faststart remux:
/// the remux is a temporary file `add` wrote, and the sidecars belong
/// to the original.
///
/// A missing sidecar is the ordinary case and says nothing. A present one
/// that cannot be stored is worth a warning, and no more.
pub fn store_sidecars(
    conn: &rusqlite::Connection,
    set_id: &str,
    source: &Path,
) -> anyhow::Result<()> {
    if let Some(summary) = find_sidecars(source).summary
        && let Err(err) = assets::put(conn, set_id, assets::Kind::Summary, "", &summary)
    {
        tracing::warn!("summary for {set_id} not stored: {err:#}");
    }
    Ok(())
}
