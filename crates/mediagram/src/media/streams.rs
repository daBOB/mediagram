//! A file's streams, as `ffprobe` reports them.
//!
//! The model the prepare pipeline decides over and the direct-play policy
//! reads. Parsing is kept apart from those decisions so they stay pure, and
//! is tested against real output, which is where a parser assuming every
//! field is present gets caught.

use std::path::Path;

use anyhow::{Result, bail};

use super::probe::{self, Report};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum StreamKind {
    Video,
    Audio,
    Subtitle,
    Other,
}

/// One stream as `ffprobe` reports it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Stream {
    pub index: u32,
    pub kind: StreamKind,
    pub language: Option<String>,
    pub bit_rate: Option<u64>,
    /// ffprobe's `codec_name`, which decides whether a browser can open the
    /// result without the player converting it first.
    pub codec: Option<String>,
}

/// What one probe tells us about a file.
#[derive(Debug, Clone, PartialEq)]
pub struct Probed {
    pub streams: Vec<Stream>,
    pub duration: f64,
    pub size: Option<u64>,
}

/// Parses `ffprobe -of json` output.
pub fn parse_probe(json: &str) -> Result<Probed> {
    from_report(probe::parse(json.as_bytes())?)
}

/// Probes a file on disk.
pub async fn probe(path: &Path) -> Result<Probed> {
    from_report(probe::run(path).await?)
}

fn from_report(report: Report) -> Result<Probed> {
    if report.streams.is_empty() {
        bail!("ffprobe reported no streams");
    }
    let (duration, size) = (report.duration().unwrap_or(0.0), report.size());
    let streams = report
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
            language: s.language().map(str::to_string),
            bit_rate: s.bit_rate.as_deref().and_then(|b| b.parse().ok()),
            codec: s.codec_name,
        })
        .collect();
    Ok(Probed { streams, duration, size })
}
