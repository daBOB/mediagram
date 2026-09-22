//! Runs `ffprobe` on a media file and turns its JSON report into the
//! technical fields a caption record needs: container, duration, codecs,
//! resolution/quality, HDR variant, and audio/subtitle language lists.

use std::path::Path;

use anyhow::{Context, Result, bail};
use serde::Deserialize;

use crate::media::classify;

/// Technical metadata extracted from one media file.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MediaInfo {
    pub container: String,
    pub size: u64,
    pub duration_s: Option<u32>,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub quality: Option<String>,
    pub hdr: String,
    pub alang: Vec<String>,
    pub slang: Vec<String>,
}

/// Runs `ffprobe -v error -print_format json -show_format -show_streams`
/// on `path` and classifies the result into a [`MediaInfo`].
pub async fn inspect(path: &Path) -> Result<MediaInfo> {
    let output = tokio::process::Command::new("ffprobe")
        .args([
            "-v",
            "error",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
        ])
        .arg(path)
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

    let parsed: FfprobeOutput = serde_json::from_slice(&output.stdout)
        .with_context(|| format!("parsing ffprobe json for {}", path.display()))?;

    let size = tokio::fs::metadata(path)
        .await
        .with_context(|| format!("stat {}", path.display()))?
        .len();

    let video = parsed
        .streams
        .iter()
        .find(|s| s.codec_type.as_deref() == Some("video"));
    let audio = parsed
        .streams
        .iter()
        .find(|s| s.codec_type.as_deref() == Some("audio"));

    let duration_s = parsed
        .format
        .duration
        .as_deref()
        .and_then(|d| d.parse::<f64>().ok())
        .map(|d| d.round() as u32);

    let side_data_types: Vec<&str> = video
        .and_then(|v| v.side_data_list.as_ref())
        .map(|list| {
            list.iter()
                .filter_map(|sd| sd.side_data_type.as_deref())
                .collect()
        })
        .unwrap_or_default();

    let hdr = video
        .map(|v| classify::hdr_from_stream(v.color_transfer.as_deref(), &side_data_types))
        .unwrap_or("SDR")
        .to_string();

    Ok(MediaInfo {
        container: classify::container_from_ext(path),
        size,
        duration_s,
        vcodec: video.and_then(|v| v.codec_name.clone()),
        acodec: audio.and_then(|s| s.codec_name.clone()),
        width: video.and_then(|v| v.width),
        height: video.and_then(|v| v.height),
        // Both dimensions: `quality_from_frame` reads the format from whichever
        // of them carries it, and height alone undersells a scope ratio.
        quality: video
            .and_then(|v| Some(classify::quality_from_frame(v.width?, v.height?).to_string())),
        hdr,
        alang: collect_langs(&parsed.streams, "audio"),
        slang: collect_langs(&parsed.streams, "subtitle"),
    })
}

/// Collects the distinct, mapped language codes for every stream of
/// `codec_type`, preserving first-seen order.
fn collect_langs(streams: &[FfprobeStream], codec_type: &str) -> Vec<String> {
    let mut out = Vec::new();
    for s in streams {
        if s.codec_type.as_deref() != Some(codec_type) {
            continue;
        }
        let tag = s.tags.as_ref().and_then(|t| t.language.as_deref());
        if let Some(code) = classify::lang_code(tag)
            && !out.contains(&code)
        {
            out.push(code);
        }
    }
    out
}

#[derive(Deserialize, Debug)]
struct FfprobeOutput {
    format: FfprobeFormat,
    #[serde(default)]
    streams: Vec<FfprobeStream>,
}

#[derive(Deserialize, Debug)]
struct FfprobeFormat {
    duration: Option<String>,
}

#[derive(Deserialize, Debug)]
struct FfprobeStream {
    codec_type: Option<String>,
    codec_name: Option<String>,
    width: Option<u32>,
    height: Option<u32>,
    color_transfer: Option<String>,
    tags: Option<FfprobeTags>,
    side_data_list: Option<Vec<FfprobeSideData>>,
}

#[derive(Deserialize, Debug)]
struct FfprobeTags {
    language: Option<String>,
}

#[derive(Deserialize, Debug)]
struct FfprobeSideData {
    side_data_type: Option<String>,
}
