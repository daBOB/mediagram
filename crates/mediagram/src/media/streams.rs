//! Reading a file's streams with `ffprobe`.
//!
//! Kept apart from [`super::prepare_plan`] so the decision logic stays pure
//! and the parsing can be tested against real output: a measured episode has
//! 52 streams, 43 of them with no declared bitrate, which is the shape that
//! catches a parser assuming every field is present.

use anyhow::{Context, Result, bail};
use serde::Deserialize;
use tokio::process::Command;

use super::prepare_plan::{Stream, StreamKind};

/// What one probe tells us about a file.
#[derive(Debug, Clone, PartialEq)]
pub struct Probed {
    pub streams: Vec<Stream>,
    pub duration: f64,
    pub size: Option<u64>,
}

#[derive(Deserialize)]
struct RawProbe {
    #[serde(default)]
    streams: Vec<RawStream>,
    #[serde(default)]
    format: Option<RawFormat>,
}

#[derive(Deserialize)]
struct RawStream {
    index: u32,
    codec_type: Option<String>,
    codec_name: Option<String>,
    // ffprobe reports numbers as strings.
    bit_rate: Option<String>,
    #[serde(default)]
    tags: Option<RawTags>,
}

#[derive(Deserialize)]
struct RawTags {
    language: Option<String>,
}

#[derive(Deserialize)]
struct RawFormat {
    duration: Option<String>,
    size: Option<String>,
}

/// Parses `ffprobe -of json` output.
pub fn parse_probe(json: &str) -> Result<Probed> {
    let raw: RawProbe = serde_json::from_str(json).context("ffprobe output is not valid JSON")?;
    if raw.streams.is_empty() {
        bail!("ffprobe reported no streams");
    }
    let streams = raw
        .streams
        .into_iter()
        .map(|s| Stream {
            index: s.index,
            kind: match s.codec_type.as_deref() {
                Some("video") => StreamKind::Video,
                Some("audio") => StreamKind::Audio,
                Some("subtitle") => StreamKind::Subtitle,
                _ => StreamKind::Other,
            },
            language: s.tags.and_then(|t| t.language),
            bit_rate: s.bit_rate.and_then(|b| b.parse().ok()),
            codec: s.codec_name,
        })
        .collect();
    let format = raw.format;
    Ok(Probed {
        streams,
        duration: format
            .as_ref()
            .and_then(|f| f.duration.as_ref())
            .and_then(|d| d.parse().ok())
            .unwrap_or(0.0),
        size: format
            .as_ref()
            .and_then(|f| f.size.as_ref())
            .and_then(|s| s.parse().ok()),
    })
}

/// Probes a file on disk.
pub async fn probe(path: &std::path::Path) -> Result<Probed> {
    let output = Command::new("ffprobe")
        .args([
            "-v",
            "error",
            "-show_entries",
            "stream=index,codec_type,codec_name,bit_rate:stream_tags=language",
            "-show_entries",
            "format=duration,size",
            "-of",
            "json",
        ])
        .arg(path)
        .output()
        .await
        .with_context(|| format!("running ffprobe on {}", path.display()))?;
    if !output.status.success() {
        bail!(
            "ffprobe failed on {}: {}",
            path.display(),
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    parse_probe(&String::from_utf8_lossy(&output.stdout))
        .with_context(|| format!("probing {}", path.display()))
}
