//! Running `ffprobe` and reading its JSON report.
//!
//! One invocation and one model for every question asked of a file: what a
//! caption records about it ([`super::inspect`]) and which streams it holds
//! ([`super::streams`]), so the two can never read one answer differently.
//!
//! Every field is optional because ffprobe omits what it cannot tell: a
//! measured episode has 52 streams, 43 of them with no declared bitrate.

use std::path::Path;

use anyhow::{Context, Result, bail};
use serde::Deserialize;

/// What `ffprobe -show_format -show_streams` says about one file.
#[derive(Deserialize, Debug)]
pub(crate) struct Report {
    #[serde(default)]
    pub streams: Vec<RawStream>,
    #[serde(default)]
    pub format: Option<RawFormat>,
}

#[derive(Deserialize, Debug)]
pub(crate) struct RawStream {
    pub index: u32,
    pub codec_type: Option<String>,
    pub codec_name: Option<String>,
    // ffprobe reports numbers as strings.
    pub bit_rate: Option<String>,
    /// Seconds, where the container records it per stream (MP4 does,
    /// Matroska leaves it to the `DURATION` tag).
    pub duration: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub color_transfer: Option<String>,
    pub tags: Option<RawTags>,
    pub disposition: Option<RawDisposition>,
    pub side_data_list: Option<Vec<RawSideData>>,
}

#[derive(Deserialize, Debug)]
pub(crate) struct RawTags {
    pub language: Option<String>,
    pub title: Option<String>,
    /// Matroska's per-track length, `HH:MM:SS.nnnnnnnnn`.
    #[serde(rename = "DURATION")]
    pub duration: Option<String>,
}

/// The flags a muxer can set on a stream; ffprobe prints each as 0 or 1.
#[derive(Deserialize, Debug, Default)]
pub(crate) struct RawDisposition {
    #[serde(default)]
    pub default: u8,
    #[serde(default)]
    pub forced: u8,
    #[serde(default)]
    pub hearing_impaired: u8,
}

#[derive(Deserialize, Debug)]
pub(crate) struct RawSideData {
    pub side_data_type: Option<String>,
}

#[derive(Deserialize, Debug)]
pub(crate) struct RawFormat {
    pub duration: Option<String>,
    pub size: Option<String>,
}

impl RawStream {
    pub fn language(&self) -> Option<&str> {
        self.tags.as_ref().and_then(|t| t.language.as_deref())
    }

    /// The stream's own length in seconds, when the container says.
    pub fn duration(&self) -> Option<f64> {
        if let Some(seconds) = self.duration.as_deref().and_then(|d| d.parse().ok()) {
            return Some(seconds);
        }
        let tag = self.tags.as_ref()?.duration.as_deref()?;
        let mut parts = tag.splitn(3, ':');
        let (h, m, s) = (parts.next()?, parts.next()?, parts.next()?);
        Some(
            h.parse::<f64>().ok()? * 3600.0
                + m.parse::<f64>().ok()? * 60.0
                + s.parse::<f64>().ok()?,
        )
    }
}

impl Report {
    /// The container's duration in seconds, when it declares one.
    pub fn duration(&self) -> Option<f64> {
        self.format.as_ref()?.duration.as_ref()?.parse().ok()
    }

    /// The container's size in bytes, when it declares one.
    pub fn size(&self) -> Option<u64> {
        self.format.as_ref()?.size.as_ref()?.parse().ok()
    }
}

/// Parses `ffprobe -of json` output.
pub(crate) fn parse(json: &[u8]) -> Result<Report> {
    serde_json::from_slice(json).context("ffprobe output is not valid JSON")
}

/// How long a read from a network input may stall before ffprobe or ffmpeg
/// gives up, in microseconds. Without it a server that stops answering
/// stalls the caller for good.
const NETWORK_RW_TIMEOUT_US: &str = "60000000";

/// The options that bound a stalled network input; none for a file.
pub(crate) fn network_args(target: &Path) -> &'static [&'static str] {
    let target = target.to_string_lossy();
    if target.starts_with("http://") || target.starts_with("https://") {
        &["-rw_timeout", NETWORK_RW_TIMEOUT_US]
    } else {
        &[]
    }
}

/// Probes a file on disk, or a URL.
pub(crate) async fn run(path: &Path) -> Result<Report> {
    let json = ffprobe(path, &["-show_format", "-show_streams"]).await?;
    parse(&json).with_context(|| format!("probing {}", path.display()))
}

#[derive(Deserialize)]
struct Packets {
    #[serde(default)]
    packets: Vec<RawPacket>,
}

#[derive(Deserialize)]
struct RawPacket {
    codec_type: Option<String>,
    pts_time: Option<String>,
    duration_time: Option<String>,
}

/// Where the picture and sound end, from the packets after the keyframe before
/// `near`: for streams with no length, where the container's may count subtitles.
pub(crate) async fn media_end(path: &Path, near: f64) -> Result<Option<f64>> {
    // shortcut: the last pts, not a length; subtract format start_time if a file not starting at 0 turns up.
    let start = format!("{near}%");
    let entries = "packet=codec_type,pts_time,duration_time";
    let json = ffprobe(path, &["-read_intervals", &start, "-show_entries", entries]).await?;
    let packets: Packets = serde_json::from_slice(&json)
        .with_context(|| format!("reading the last packets of {}", path.display()))?;
    let seconds = |s: &Option<String>| s.as_deref().and_then(|s| s.parse::<f64>().ok());
    Ok(packets
        .packets
        .iter()
        .filter(|p| matches!(p.codec_type.as_deref(), Some("video" | "audio")))
        .filter_map(|p| Some(seconds(&p.pts_time)? + seconds(&p.duration_time).unwrap_or(0.0)))
        .reduce(f64::max))
}

/// Runs `ffprobe` with `args` on `path` and returns its JSON.
async fn ffprobe(path: &Path, args: &[&str]) -> Result<Vec<u8>> {
    let output = tokio::process::Command::new("ffprobe")
        .args(network_args(path))
        .args(["-v", "error", "-print_format", "json"])
        .args(args)
        .arg(path)
        // Ctrl-C is for mediagram, not for the probe under it.
        .process_group(0)
        .kill_on_drop(true)
        .output()
        .await
        .with_context(|| format!("running ffprobe on {}", path.display()))?;
    if !output.status.success() {
        bail!(
            "ffprobe exited with {} for {}: {}",
            output.status,
            path.display(),
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    Ok(output.stdout)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_a_network_input_gets_a_read_timeout() {
        assert_eq!(
            network_args(Path::new("http://127.0.0.1:8770/file")),
            ["-rw_timeout", NETWORK_RW_TIMEOUT_US]
        );
        assert!(network_args(Path::new("/media/film.mkv")).is_empty());
        assert!(network_args(Path::new("file:///media/film.mkv")).is_empty());
    }
}
