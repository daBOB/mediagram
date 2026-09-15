//! Reading a course folder into an ordered list of lessons.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

use crate::course::plan::{assign_numbers, is_video};

/// One lesson: where its file is, and where it sits in the course.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Lesson {
    pub path: PathBuf,
    pub chapter: u32,
    pub chapter_title: Option<String>,
    pub lesson: u32,
    pub title: Option<String>,
}

/// Walks a course folder. Each immediate subdirectory is a chapter; video
/// files directly inside the root form chapter 1 when there are no
/// subdirectories. Nesting deeper than one level is followed, and its files
/// belong to the top-level chapter that contains them.
pub fn walk_course(root: &Path) -> Result<Vec<Lesson>> {
    let entries = std::fs::read_dir(root)
        .with_context(|| format!("reading course folder {}", root.display()))?;

    let mut chapter_dirs: Vec<String> = Vec::new();
    let mut root_files: Vec<String> = Vec::new();
    for entry in entries {
        let entry = entry.context("reading a course entry")?;
        let name = entry.file_name().to_string_lossy().to_string();
        if entry.file_type().context("typing a course entry")?.is_dir() {
            chapter_dirs.push(name);
        } else if is_video(&name) {
            root_files.push(name);
        }
    }

    let mut lessons = Vec::new();
    if chapter_dirs.is_empty() {
        // A flat course: everything is chapter one.
        for (lesson, title, name) in assign_numbers(&by_stem(root_files)) {
            lessons.push(Lesson {
                path: root.join(&name),
                chapter: 1,
                chapter_title: None,
                lesson,
                title,
            });
        }
        return Ok(lessons);
    }

    for (chapter, chapter_title, dir_name) in assign_numbers(&paired(chapter_dirs)) {
        let dir = root.join(&dir_name);
        let files = collect_videos(&dir, &dir)?;
        for (lesson, title, relative) in assign_numbers(&by_stem(files)) {
            lessons.push(Lesson {
                path: dir.join(&relative),
                chapter,
                chapter_title: chapter_title.clone(),
                lesson,
                title,
            });
        }
    }
    Ok(lessons)
}

/// Files paired with the stem their number and title are read from. A nested
/// file keeps its relative path as the payload but is read by its own name.
fn by_stem(mut names: Vec<String>) -> Vec<(String, String)> {
    names.sort();
    names
        .into_iter()
        .map(|name| {
            let own = name.rsplit(['/', '\\']).next().unwrap_or(&name).to_string();
            (crate::course::plan::stem(&own).to_string(), name)
        })
        .collect()
}

/// Directories are read by their own name.
fn paired(mut names: Vec<String>) -> Vec<(String, String)> {
    names.sort();
    names.into_iter().map(|n| (n.clone(), n)).collect()
}

/// Video file names under `dir`, relative to it, including nested ones.
fn collect_videos(root: &Path, dir: &Path) -> Result<Vec<String>> {
    let mut out = Vec::new();
    let entries = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?;
    for entry in entries {
        let entry = entry.context("reading a chapter entry")?;
        let path = entry.path();
        if entry
            .file_type()
            .context("typing a chapter entry")?
            .is_dir()
        {
            out.extend(collect_videos(root, &path)?);
        } else if is_video(&entry.file_name().to_string_lossy()) {
            let relative = path
                .strip_prefix(root)
                .context("chapter file outside its chapter")?;
            out.push(relative.to_string_lossy().to_string());
        }
    }
    Ok(out)
}
