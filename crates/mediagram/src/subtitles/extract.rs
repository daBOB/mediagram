//! Reading subtitle tracks out to WebVTT with ffmpeg.
//!
//! Outputs go into a directory the caller owns and deletes, never beside the
//! source: a crash would otherwise leave `.vtt` files that the sidecar rule
//! adopts as the video's own.

use std::collections::HashMap;
use std::ffi::OsString;
use std::path::Path;

use anyhow::{Context, Result, bail};
use mlib_spec::subtitle_bundle::MAX_TRACK_BYTES;

use crate::media::probe::network_args;

/// Extracts the embedded streams `indexes` of `target` (a path or a URL) in
/// one pass over the input.
///
/// `alone` says a failed or suspect pass may be redone stream by stream,
/// each a full read of the input: worth it for a local file, not for a URL
/// whose every read is a download; a set missing a track is healed later.
pub async fn embedded(
    target: &Path,
    indexes: &[u32],
    dir: &Path,
    alone: bool,
) -> HashMap<u32, String> {
    if indexes.is_empty() {
        return HashMap::new();
    }
    let mut failed = Vec::new();
    match ffmpeg(target, indexes, dir, false).await {
        Ok(said) if !bad_utf8(&said) => {}
        Ok(_) if !alone => tracing::warn!("subtitles: a track is not UTF-8 and was left as read"),
        Err(err) if !alone => {
            // With `-xerror` a track that is not UTF-8 fails the pass. Which
            // track is unknown unless there is only one, and reading the
            // others as Windows-1252 would garble them, so only a lone track
            // gets the one extra read.
            let retried = match indexes {
                [_] if bad_utf8(&format!("{err:#}")) => ffmpeg(target, indexes, dir, true).await,
                _ => Err(err),
            };
            if let Err(err) = retried {
                tracing::warn!("subtitles: extraction failed: {err:#}");
                return HashMap::new();
            }
        }
        outcome => {
            if let Err(err) = outcome {
                tracing::warn!("subtitles: one pass failed ({err:#}); trying each track alone");
            }
            failed = each_alone(target, indexes, dir).await;
        }
    }
    indexes
        .iter()
        .filter(|index| !failed.contains(index))
        .filter_map(|index| Some((*index, read_output(&dir.join(format!("{index}.vtt")))?)))
        .collect()
}

/// ffmpeg exits 0 on text it cannot decode as UTF-8 and drops the line.
fn bad_utf8(stderr: &str) -> bool {
    stderr.contains("Invalid UTF-8")
}

/// Each stream on its own, so one that ffmpeg says is not UTF-8 can be read
/// as Windows-1252 without garbling the others. Returns the streams that
/// could not be extracted.
async fn each_alone(target: &Path, indexes: &[u32], dir: &Path) -> Vec<u32> {
    let mut failed = Vec::new();
    for index in indexes {
        let mut outcome = ffmpeg(target, &[*index], dir, false).await;
        // A track that is mostly such lines makes ffmpeg fail outright; its
        // error carries the same words.
        let not_utf8 = match &outcome {
            Ok(said) => bad_utf8(said),
            Err(err) => bad_utf8(&format!("{err:#}")),
        };
        if not_utf8 {
            outcome = ffmpeg(target, &[*index], dir, true).await;
        }
        if let Err(err) = outcome {
            tracing::warn!("subtitles: stream {index} not extracted: {err:#}");
            failed.push(*index);
        }
    }
    failed
}

/// Converts a SubRip file. Files that are not UTF-8 are read as CP1252,
/// which is what releases that are not UTF-8 are in.
pub async fn srt_to_vtt(srt: &Path, dir: &Path, n: usize) -> Result<String> {
    let bytes = std::fs::read(srt).with_context(|| format!("reading {}", srt.display()))?;
    let charenc: &[&str] = if std::str::from_utf8(&bytes).is_ok() {
        &[]
    } else {
        &["-sub_charenc", "CP1252"]
    };
    let out = dir.join(format!("sidecar-{n}.vtt"));
    let mut args: Vec<OsString> = base_args();
    args.extend(charenc.iter().map(OsString::from));
    args.extend([
        "-i".into(),
        srt.into(),
        "-f".into(),
        "webvtt".into(),
        out.clone().into(),
    ]);
    run(args)
        .await
        .with_context(|| format!("converting {}", srt.display()))?;
    read_output(&out).with_context(|| format!("{} gave no usable WebVTT", srt.display()))
}

/// One ffmpeg run over `target`; `cp1252` reads its text as Windows-1252.
/// Returns what ffmpeg said on stderr.
async fn ffmpeg(target: &Path, indexes: &[u32], dir: &Path, cp1252: bool) -> Result<String> {
    let mut args = base_args();
    let network = network_args(target);
    args.extend(network.iter().map(OsString::from));
    // A read that ends early (the server lost the channel mid-stream) makes
    // ffmpeg exit 0 with the cues seen so far; over a network that must be a
    // failure, not a short track.
    if !network.is_empty() {
        args.push("-xerror".into());
    }
    if cp1252 {
        args.extend(["-sub_charenc".into(), "CP1252".into()]);
    }
    args.extend(["-i".into(), target.into()]);
    for index in indexes {
        args.extend([
            "-map".into(),
            format!("0:{index}").into(),
            "-c:s".into(),
            "webvtt".into(),
            "-f".into(),
            "webvtt".into(),
            dir.join(format!("{index}.vtt")).into(),
        ]);
    }
    run(args).await
}

fn base_args() -> Vec<OsString> {
    ["-v", "error", "-nostdin", "-y"]
        .map(OsString::from)
        .to_vec()
}

async fn run(args: Vec<OsString>) -> Result<String> {
    let output = tokio::process::Command::new("ffmpeg")
        .args(args)
        // Its own process group and gone with us: Ctrl-C is for mediagram,
        // which finishes the set in hand, not for the extraction under it.
        .process_group(0)
        .kill_on_drop(true)
        .output()
        .await
        .context("running ffmpeg")?;
    if !output.status.success() {
        bail!(
            "ffmpeg exited with {}: {}",
            output.status,
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    let said = String::from_utf8_lossy(&output.stderr).trim().to_string();
    if !said.is_empty() {
        tracing::warn!("subtitles: ffmpeg said: {said}");
    }
    Ok(said)
}

/// Whether stream `index`'s output came out past what a bundle accepts: a
/// property of the track, not of the read, so a retry would find it again.
pub fn too_large(dir: &Path, index: u32) -> bool {
    std::fs::metadata(dir.join(format!("{index}.vtt")))
        .is_ok_and(|m| m.len() > MAX_TRACK_BYTES as u64)
}

/// The track's text, unless it is missing, not UTF-8, or past what a
/// bundle accepts (which would refuse the whole bundle).
fn read_output(path: &Path) -> Option<String> {
    let bytes = std::fs::read(path).ok()?;
    if bytes.len() > MAX_TRACK_BYTES {
        tracing::warn!(
            "subtitles: a track is {} bytes, too large to keep",
            bytes.len()
        );
        return None;
    }
    String::from_utf8(bytes).ok()
}

#[cfg(test)]
#[path = "extract_tests.rs"]
mod tests;
