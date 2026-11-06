//! Where `prepare` writes: the working file beside a destination, and the
//! destination a source maps to under `--out`. Kept apart from the command
//! because both guard an irreversible step — a rename over a file.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};

/// Suffix of the file `prepare` writes beside a destination before renaming
/// it over it. Shares the `.prepared.` marker so a crashed run leaves
/// something recognisable rather than a plausible-looking video.
pub const PREPARE_WORKING_SUFFIX: &str = ".prepared.mkv";

/// The working file for `destination`, in the same directory so the rename
/// that finishes the job is atomic.
pub fn working_path(destination: &Path) -> PathBuf {
    let stem = destination
        .file_stem()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_else(|| "output".to_string());
    destination.with_file_name(format!("{stem}{PREPARE_WORKING_SUFFIX}"))
}

/// Where `--replace` puts a source's replacement: over the source, or beside
/// it as `.mp4` when converting, because the uploader reads the container
/// from the extension and would send MP4 bytes named `.mkv` as Matroska.
///
/// An `.mp4` already there may be another version of the same title, so it
/// is refused rather than overwritten. Checked per file just before its
/// rewrite, which also catches one written earlier in the same run.
pub fn replaced(file: &Path, to_mp4: bool) -> Result<PathBuf> {
    let already_mp4 = file
        .extension()
        .is_some_and(|ext| ext.eq_ignore_ascii_case("mp4"));
    if !to_mp4 || already_mp4 {
        return Ok(file.to_path_buf());
    }
    let dest = file.with_extension("mp4");
    if dest.exists() {
        bail!(
            "{} already exists; move one of the two aside",
            dest.display()
        );
    }
    Ok(dest)
}

/// Where a source file lands under `--out`, keeping the tree it came from.
///
/// A file named directly has no tree to mirror — stripping the root off it
/// leaves nothing — so it lands at the top of the output directory under its
/// own name. Without that case the output directory is itself renamed into
/// the result.
pub fn mirrored(file: &Path, root: &Path, out: &Path, to_mp4: bool) -> Result<PathBuf> {
    let relative = match file.strip_prefix(root) {
        Ok(rest) if !rest.as_os_str().is_empty() => rest.to_path_buf(),
        _ => PathBuf::from(
            file.file_name()
                .with_context(|| format!("{} has no file name", file.display()))?,
        ),
    };
    let mut dest = out.join(relative);
    if to_mp4 {
        dest.set_extension("mp4");
    }
    if dest == file {
        bail!(
            "{} would be written over its own source; give --out a different directory",
            dest.display()
        );
    }
    Ok(dest)
}
