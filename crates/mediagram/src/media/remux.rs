// Not wired into any command yet; the add command calls this before
// splitting an MP4 source that fails the faststart check.

//! Remuxes an MP4 source so `moov` precedes `mdat` (`ffmpeg -movflags
//! +faststart`), without re-encoding. Splitting requires part 0 to hold the
//! full `moov` atom, so any file that fails [`mp4_atoms::needs_faststart`]
//! must be remuxed first.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

use crate::media::mp4_atoms;

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
    let dest = dest_dir.join(format!("{stem}.faststart.mp4"));

    let output = tokio::process::Command::new("ffmpeg")
        .args(["-v", "error", "-i"])
        .arg(src)
        .args(["-c", "copy", "-movflags", "+faststart", "-y"])
        .arg(&dest)
        .output()
        .await
        .with_context(|| format!("running ffmpeg faststart remux on {}", src.display()))?;

    if !output.status.success() {
        bail!(
            "ffmpeg faststart remux failed for {}: {}",
            src.display(),
            String::from_utf8_lossy(&output.stderr).trim()
        );
    }

    if mp4_atoms::needs_faststart(&dest)? {
        bail!("remuxed {} still has moov after mdat", dest.display());
    }

    Ok(dest)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::media::test_fixtures::{
        ffmpeg_available, make_faststart_mp4, make_trailing_moov_mp4,
    };

    #[tokio::test]
    async fn remuxes_trailing_moov_fixture() {
        if !ffmpeg_available() {
            eprintln!("skipping remuxes_trailing_moov_fixture: ffmpeg not on PATH");
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let src = make_trailing_moov_mp4(dir.path());

        let out = ensure_faststart(&src, None, false).await.unwrap();
        assert_ne!(out, src);
        assert!(!mp4_atoms::needs_faststart(&out).unwrap());
    }

    #[tokio::test]
    async fn leaves_faststart_fixture_untouched() {
        if !ffmpeg_available() {
            eprintln!("skipping leaves_faststart_fixture_untouched: ffmpeg not on PATH");
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let src = make_faststart_mp4(dir.path());

        let out = ensure_faststart(&src, None, false).await.unwrap();
        assert_eq!(out, src);
    }

    #[tokio::test]
    async fn no_remux_flag_bypasses_even_when_needed() {
        if !ffmpeg_available() {
            eprintln!("skipping no_remux_flag_bypasses_even_when_needed: ffmpeg not on PATH");
            return;
        }
        let dir = tempfile::tempdir().unwrap();
        let src = make_trailing_moov_mp4(dir.path());

        let out = ensure_faststart(&src, None, true).await.unwrap();
        assert_eq!(out, src);
    }
}
