//! Reading a course folder into an ordered list of lessons.
//!
//! Real courses are not two levels deep. One looked like
//! `Course / Ausbildung / 1. Grundlagen / 3. Signal / 14. Exkurs / *.mp4`,
//! with some sections holding videos directly and others nesting twice more.
//! So a chapter is not "a subdirectory of the root": it is **the directory
//! that actually holds the videos**, wherever it sits. That is the grouping a
//! person made, and it is the only one that survives arbitrary depth.
//!
//! A chapter number is a single integer, so the chapter's path carries the
//! hierarchy that number cannot.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result};

use crate::course::plan::{assign_unique_numbers, is_video};

/// One lesson: where its file is, and where it sits in the course.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Lesson {
    pub path: PathBuf,
    pub chapter: u32,
    pub chapter_title: Option<String>,
    pub lesson: u32,
    pub title: Option<String>,
}

/// Walks a course folder. Every directory holding video files becomes a
/// chapter, numbered in path order; the files inside it become its lessons.
///
/// Lesson numbers are unique within a chapter by construction. A file's own
/// leading number is honoured when it can be, but two files numbered `1` in
/// one folder would otherwise collapse onto a single identity and the second
/// would be skipped forever as "already uploaded".
pub fn walk_course(root: &Path) -> Result<Vec<Lesson>> {
    if !root.is_dir() {
        anyhow::bail!("{} is not a directory", root.display());
    }

    let mut by_folder: BTreeMap<PathBuf, Vec<String>> = BTreeMap::new();
    collect(root, root, &mut by_folder)?;

    let folders: Vec<(String, PathBuf)> = by_folder
        .keys()
        .map(|dir| (chapter_label(root, dir), dir.clone()))
        .collect();

    let mut lessons = Vec::new();
    // Unique across the course: sections routinely number their chapters
    // from 1 each, so the declared numbers collide constantly.
    for (chapter, inferred_title, dir) in assign_unique_numbers(&folders) {
        let files = by_folder.get(&dir).cloned().unwrap_or_default();
        let chapter_title = chapter_title(root, &dir).or(inferred_title);
        for (lesson, title, file) in number_lessons(&files) {
            lessons.push(Lesson {
                path: dir.join(&file),
                chapter,
                chapter_title: chapter_title.clone(),
                lesson,
                title,
            });
        }
    }
    Ok(lessons)
}

/// Lesson numbers within one chapter, guaranteed distinct.
fn number_lessons(files: &[String]) -> Vec<(u32, Option<String>, String)> {
    let entries: Vec<(String, String)> = files
        .iter()
        .map(|name| (crate::course::plan::stem(name).to_string(), name.clone()))
        .collect();
    assign_unique_numbers(&entries)
}

/// Video file names in each directory that holds any, keyed by directory.
fn collect(root: &Path, dir: &Path, out: &mut BTreeMap<PathBuf, Vec<String>>) -> Result<()> {
    let entries = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?;
    let mut here = Vec::new();
    let mut subdirs = Vec::new();
    for entry in entries {
        let entry = entry.context("reading a course entry")?;
        let file_type = entry.file_type().context("typing a course entry")?;
        let name = entry.file_name().to_string_lossy().to_string();
        if file_type.is_dir() {
            subdirs.push(entry.path());
        } else if file_type.is_file() && is_video(&name) {
            here.push(name);
        }
    }
    if !here.is_empty() {
        here.sort();
        out.insert(dir.to_path_buf(), here);
    }
    subdirs.sort();
    for sub in subdirs {
        collect(root, &sub, out)?;
    }
    let _ = root;
    Ok(())
}

/// The name a chapter's number and inferred title are read from: its own
/// folder name, or the course name when the videos sit in the root.
fn chapter_label(root: &Path, dir: &Path) -> String {
    if dir == root {
        return String::new();
    }
    dir.file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default()
}

/// The chapter's path relative to the course root, which is what carries the
/// section it belongs to. `None` for videos sitting in the root itself.
///
/// Each segment is cleaned the way a lesson title is: the leading number is
/// dropped because the chapter number already encodes order, and repeating it
/// in the title would just be noise.
fn chapter_title(root: &Path, dir: &Path) -> Option<String> {
    let relative = dir.strip_prefix(root).ok()?;
    let parts: Vec<String> = relative
        .components()
        .filter_map(|c| {
            let raw = c.as_os_str().to_string_lossy().to_string();
            let (_, cleaned) = crate::course::plan::split_number_and_title(&raw);
            cleaned.or(Some(raw)).filter(|t| !t.is_empty())
        })
        .collect();
    (!parts.is_empty()).then(|| parts.join(" / "))
}
