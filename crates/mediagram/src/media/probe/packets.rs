//! The last packets of a file, for the end of its picture and sound.

use std::path::Path;

use anyhow::{Context, Result};
use serde::Deserialize;

use super::ffprobe;

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
