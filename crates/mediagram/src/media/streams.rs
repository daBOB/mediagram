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
    /// The track's own name, where a release says what it is (`German
    /// (Forced)`, `English (SDH)`) when its flags do not.
    pub title: Option<String>,
    /// The muxer's `default`, `forced` and `hearing_impaired` flags.
    pub default: bool,
    pub forced: bool,
    pub hearing_impaired: bool,
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
        .map(|s| {
            let flags = s.disposition.as_ref();
            let flag = |pick: fn(&probe::RawDisposition) -> u8| flags.is_some_and(|d| pick(d) == 1);
            Stream {
                index: s.index,
                kind: match s.codec_type.as_deref() {
                    Some("video") => StreamKind::Video,
                    Some("audio") => StreamKind::Audio,
                    Some("subtitle") => StreamKind::Subtitle,
                    _ => StreamKind::Other,
                },
                language: s.language().map(str::to_string),
                bit_rate: s.bit_rate.as_deref().and_then(|b| b.parse().ok()),
                title: s.tags.as_ref().and_then(|t| t.title.clone()),
                default: flag(|d| d.default),
                forced: flag(|d| d.forced),
                hearing_impaired: flag(|d| d.hearing_impaired),
                codec: s.codec_name,
            }
        })
        .collect();
    Ok(Probed {
        streams,
        duration,
        size,
    })
}
