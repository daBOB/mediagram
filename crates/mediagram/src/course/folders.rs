//! Reading a course folder off disk: which video and document files each
//! directory under it holds.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

use crate::media::file_names::{is_document, is_video};

/// The files of interest in one directory.
#[derive(Debug, Default, Clone)]
pub(super) struct Folder {
    pub(super) videos: Vec<String>,
    pub(super) documents: Vec<String>,
}

/// Video and document file names in each directory holding either, keyed by
/// directory.
pub(super) fn collect(dir: &Path, out: &mut BTreeMap<PathBuf, Folder>) -> Result<()> {
    let entries = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?;
    let mut here = Folder::default();
    let mut subdirs = Vec::new();
    for entry in entries {
        let entry = entry.context("reading a course entry")?;
        let file_type = entry.file_type().context("typing a course entry")?;
        let name = entry.file_name().to_string_lossy().to_string();
        if file_type.is_dir() {
            subdirs.push(entry.path());
        } else if file_type.is_file() {
            if is_video(&name) {
                here.videos.push(name);
            } else if is_document(&name) {
                here.documents.push(name);
            }
        }
    }
    if !here.videos.is_empty() || !here.documents.is_empty() {
        here.videos.sort();
        here.documents.sort();
        out.insert(dir.to_path_buf(), here);
    }
    subdirs.sort();
    for sub in subdirs {
        collect(&sub, out)?;
    }
    Ok(())
}
