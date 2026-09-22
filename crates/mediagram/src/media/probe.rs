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
    pub width: Option<u32>,
    pub height: Option<u32>,
    pub color_transfer: Option<String>,
    pub tags: Option<RawTags>,
    pub side_data_list: Option<Vec<RawSideData>>,
}

#[derive(Deserialize, Debug)]
pub(crate) struct RawTags {
    pub language: Option<String>,
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

/// Probes a file on disk.
pub(crate) async fn run(path: &Path) -> Result<Report> {
    let output = tokio::process::Command::new("ffprobe")
        .args(["-v", "error", "-print_format", "json", "-show_format", "-show_streams"])
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
    parse(&output.stdout).with_context(|| format!("probing {}", path.display()))
}
