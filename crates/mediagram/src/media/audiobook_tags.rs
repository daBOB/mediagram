//! What an audiobook MP3 says about itself: its title and genre tags and the
//! picture embedded as cover art. The file is the only source, so a re-run
//! reads the same answer and the book is found again by that title.

use std::path::Path;

use anyhow::{Context, Result, bail};
use tokio::process::Command;

use crate::media::probe;

/// The cover art stream inside an MP3, and the image type it holds.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CoverStream {
    pub index: u32,
    pub mime: &'static str,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AudiobookTags {
    pub title: String,
    pub genre: Option<String>,
    pub cover: Option<CoverStream>,
}

/// Reads the tags of `path`; the title falls back to its file name.
pub async fn read(path: &Path) -> Result<AudiobookTags> {
    let report = probe::run(path).await?;
    let tag = |pick: fn(&probe::RawFormatTags) -> &Option<String>| {
        report
            .format
            .as_ref()
            .and_then(|f| f.tags.as_ref())
            .and_then(|t| pick(t).as_deref())
            .map(str::trim)
            .filter(|v| !v.is_empty())
            .map(str::to_string)
    };
    let title = tag(|t| &t.title).unwrap_or_else(|| {
        let name = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or_default();
        title_from_file_name(crate::media::file_names::stem(name))
    });
    // An image type this uploader does not recognise is no cover, not an
    // error: the book still uploads.
    let cover = report
        .streams
        .iter()
        .filter(|s| s.is_cover())
        .find_map(|s| {
            let mime = match s.codec_name.as_deref()? {
                "mjpeg" => "image/jpeg",
                "png" => "image/png",
                _ => return None,
            };
            Some(CoverStream {
                index: s.index,
                mime,
            })
        });
    Ok(AudiobookTags {
        title,
        genre: tag(|t| &t.genre),
        cover,
    })
}

/// The bytes of the cover image, copied out of the file without re-encoding.
pub async fn cover_bytes(path: &Path, cover: &CoverStream) -> Result<Vec<u8>> {
    let out = Command::new("ffmpeg")
        .args(["-v", "error", "-i"])
        .arg(path)
        .args(["-map", &format!("0:{}", cover.index)])
        .args(["-c", "copy", "-f", "image2pipe", "-"])
        .output()
        .await
        .context("running ffmpeg")?;
    if !out.status.success() || out.stdout.is_empty() {
        bail!(
            "reading the cover of {}: {}",
            path.display(),
            String::from_utf8_lossy(&out.stderr).trim()
        );
    }
    Ok(out.stdout)
}

/// The file stem without a trailing ` [digits]` (a store's book id). Never
/// number-split: a leading number belongs to the title.
pub fn title_from_file_name(stem: &str) -> String {
    let stem = stem.trim();
    if let Some(open) = stem.rfind(" [")
        && let Some(id) = stem[open + 2..].strip_suffix(']')
        && !id.is_empty()
        && id.bytes().all(|b| b.is_ascii_digit())
    {
        return stem[..open].trim_end().to_string();
    }
    stem.to_string()
}

#[cfg(test)]
#[path = "audiobook_tags_tests.rs"]
mod tests;
