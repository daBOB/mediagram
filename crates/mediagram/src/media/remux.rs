//! Remuxes an MP4 source so `moov` precedes `mdat` (`ffmpeg -movflags
//! +faststart`), without re-encoding. Splitting requires part 0 to hold the
//! full `moov` atom, so any file that fails [`mp4_atoms::needs_faststart`]
//! must be remuxed first.
//!
//! ffmpeg with no `-map` keeps only one video and one audio stream and drops
//! every subtitle, so the map is built from a probe instead: every video
//! (not cover art), every audio, and only the subtitle codecs the mp4 muxer
//! accepts copied in. An explicit map can still be refused — a stream a
//! blanket copy would have silently dropped fails hard once it is named —
//! so a refused mapped remux is retried once with today's unmapped
//! arguments rather than failing the whole upload.

use std::ffi::OsStr;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

use crate::media::file_names::REMUX_MARKER;
use crate::media::mp4_atoms;
use crate::media::streams::{self, Stream, StreamKind};

/// Subtitle codecs the mp4 muxer accepts copied in without re-encoding.
const MP4_SUBTITLE_CODECS: [&str; 2] = ["mov_text", "dvd_subtitle"];

/// Today's unmapped remux arguments: ffmpeg's own stream selection, which
/// keeps one video and one audio stream and drops every subtitle. Used as
/// the one-time fallback when an explicit map is refused.
const FALLBACK_ARGS: [&str; 4] = ["-c", "copy", "-movflags", "+faststart"];

/// Builds an explicit `-map` set so a faststart remux keeps every stream the
/// mp4 muxer can hold: every video stream that is not an attached picture
/// (`0:V?`), every audio stream (`0:a?`), and one `-map 0:<index>` per
/// subtitle stream whose codec the mp4 muxer accepts. Data and attachment
/// streams are never mapped.
pub fn faststart_args(streams: &[Stream]) -> Vec<String> {
    let mut args: Vec<String> = ["-map", "0:V?", "-map", "0:a?"]
        .into_iter()
        .map(String::from)
        .collect();
    for stream in streams {
        if stream.kind == StreamKind::Subtitle
            && stream
                .codec
                .as_deref()
                .is_some_and(|codec| MP4_SUBTITLE_CODECS.contains(&codec))
        {
            args.push("-map".to_string());
            args.push(format!("0:{}", stream.index));
        }
    }
    args.extend(FALLBACK_ARGS.into_iter().map(String::from));
    args
}

/// Ensures `src` is faststart-safe for splitting. Returns `src` unchanged
/// when no remux is needed or `no_remux` is set; otherwise remuxes into
/// `<tmp_dir or src's dir>/<stem>.faststart.mp4` and returns that path.
/// Errors if the remuxed output still fails the faststart check.
pub async fn ensure_faststart(
    src: &Path,
    tmp_dir: Option<&Path>,
    no_remux: bool,
) -> Result<PathBuf> {
    if no_remux || !mp4_atoms::needs_faststart(src)? {
        return Ok(src.to_path_buf());
    }

    let dest_dir = match tmp_dir {
        Some(d) => d,
        None => src.parent().unwrap_or_else(|| Path::new(".")),
    };
    let stem = src
        .file_stem()
        .and_then(|s| s.to_str())
        .with_context(|| format!("{} has no usable file stem", src.display()))?;
    let dest = dest_dir.join(format!("{stem}{REMUX_MARKER}mp4"));

    let mapped = match streams::probe(src).await {
        Ok(probed) => Some(faststart_args(&probed.streams)),
        Err(err) => {
            tracing::warn!(
                source = %src.display(),
                error = %err,
                "could not probe streams for a mapped faststart remux; falling back to today's arguments"
            );
            None
        }
    };

    let mapped_result = match &mapped {
        Some(args) => Some(run_ffmpeg(src, &dest, args).await),
        None => None,
    };

    let mapped_ok = matches!(mapped_result, Some(Ok(())));
    if let Some(Err(err)) = mapped_result {
        tracing::warn!(
            source = %src.display(),
            error = %err,
            "mapped faststart remux was refused; some streams may be dropped, retrying with today's arguments"
        );
    }
    if !mapped_ok {
        run_ffmpeg(src, &dest, &FALLBACK_ARGS).await?;
    }

    if mp4_atoms::needs_faststart(&dest)? {
        bail!("remuxed {} still has moov after mdat", dest.display());
    }

    Ok(dest)
}

/// Runs one ffmpeg faststart pass, removing a partial output on failure.
async fn run_ffmpeg<S: AsRef<OsStr>>(src: &Path, dest: &Path, stream_args: &[S]) -> Result<()> {
    let output = tokio::process::Command::new("ffmpeg")
        .args(["-v", "error", "-i"])
        .arg(src)
        .args(stream_args)
        .arg("-y")
        .arg(dest)
        .output()
        .await
        .with_context(|| format!("running ffmpeg faststart remux on {}", src.display()))?;

    if !output.status.success() {
        let _ = std::fs::remove_file(dest);
        bail!(
            "ffmpeg faststart remux failed for {}: {}",
            src.display(),
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }
    Ok(())
}

#[cfg(test)]
#[path = "remux_tests.rs"]
mod tests;
