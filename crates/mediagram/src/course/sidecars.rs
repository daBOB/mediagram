//! The files sitting next to a lesson's video.
//!
//! A transcription leaves several forms of the same words beside each video:
//! `.vtt`, `.srt`, `.txt`, and Whisper's `.json` and `.tsv` working files.
//! Only the `.vtt` travels. It is what a browser's `<track>` element wants,
//! the `.srt` and `.txt` say the same thing in formats nothing here reads,
//! and the working files come to 16 MB per course.
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
    /// WebVTT, as the `<track>` element wants it.
    pub subtitle: Option<String>,
    pub summary: Option<String>,
}

/// Reads the sidecars beside `video`, if any.
///
/// Missing files are ordinary: most lessons have no summary, and a course
/// that was never transcribed has no subtitles. Only a file that exists and
/// cannot be used is worth a word, and that word is a warning rather than an
/// error — a lesson is still worth uploading without its subtitle.
pub fn find_sidecars(video: &Path) -> Sidecars {
    let Some(stem) = base_stem(video) else {
        return Sidecars::default();
    };
    let folder = video.parent().unwrap_or(Path::new("."));

    let subtitle = read_text(&folder.join(format!("{stem}.vtt")));
    let summary = SUMMARY_SUFFIXES
        .iter()
        .find_map(|suffix| read_text(&folder.join(format!("{stem}{suffix}"))));

    Sidecars { subtitle, summary }
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

/// Stores the subtitle and summary sitting beside a video, if any.
///
/// Read from the source the user named rather than from a faststart remux:
/// the remux is a temporary file `add` wrote, and the sidecars belong
/// to the original.
///
/// A missing sidecar is the ordinary case and says nothing. A present one
/// that cannot be stored is worth a warning, and no more: a lesson without
/// its subtitle is still worth having.
pub fn store_sidecars(
    conn: &rusqlite::Connection,
    set_id: &str,
    source: &Path,
    caption: &mlib_spec::Caption,
) -> anyhow::Result<()> {
    let found = find_sidecars(source);

    if let Some(subtitle) = &found.subtitle {
        // The subtitle is the audio written down, so it is in the audio's
        // language; `und` when the file never said.
        let lang = caption.alang.first().map(String::as_str).unwrap_or("und");
        if let Err(err) = assets::put(conn, set_id, assets::Kind::Subtitle, lang, subtitle) {
            tracing::warn!("subtitle for {set_id} not stored: {err:#}");
        }
    }
    if let Some(summary) = &found.summary
        && let Err(err) = assets::put(conn, set_id, assets::Kind::Summary, "", summary)
    {
        tracing::warn!("summary for {set_id} not stored: {err:#}");
    }
    Ok(())
}
