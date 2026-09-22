//! Finding the video files under a path the person named.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

use crate::course::plan::is_video;
use crate::media::prepare_paths::PREPARE_WORKING_SUFFIX;

/// `path` itself when it is a file; otherwise every video file under it,
/// recursively and sorted.
///
/// Skips the working files a crashed `prepare` leaves behind: they look like
/// videos, and a show walk would file them as a second copy of an episode.
pub fn collect_videos(path: &Path) -> Result<Vec<PathBuf>> {
    if path.is_file() {
        return Ok(vec![path.to_path_buf()]);
    }
    let mut out = Vec::new();
    walk(path, &mut out)?;
    out.sort();
    Ok(out)
}

fn walk(dir: &Path, out: &mut Vec<PathBuf>) -> Result<()> {
    let entries = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?;
    for entry in entries {
        let entry = entry.context("reading a directory entry")?;
        let file_type = entry.file_type().context("typing a directory entry")?;
        if file_type.is_dir() {
            walk(&entry.path(), out)?;
        } else if file_type.is_file() {
            let name = entry.file_name().to_string_lossy().to_string();
            if is_video(&name) && !name.ends_with(PREPARE_WORKING_SUFFIX) {
                out.push(entry.path());
            }
        }
    }
    Ok(())
}
