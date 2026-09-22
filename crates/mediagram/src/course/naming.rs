//! How a course folder's pieces are named and numbered: a chapter's label
//! and title, a folder's path inside the course, and distinct numbers for
//! the files in one folder.

use std::path::Path;

use crate::course::plan::assign_unique_numbers;
use crate::media::file_names::{split_number_and_title, stem};

/// The folders between the course root and `dir`, as the caption spells them.
///
/// Always `/`-separated: this is a label inside a course, not a path on the
/// machine that happened to upload it, and it has to mean the same thing
/// wherever it is read.
pub(super) fn relative_path(root: &Path, dir: &Path) -> String {
    dir.strip_prefix(root)
        .unwrap_or(dir)
        .components()
        .map(|component| component.as_os_str().to_string_lossy().to_string())
        .collect::<Vec<_>>()
        .join("/")
}

/// Numbers for one folder's files of one kind, guaranteed distinct.
pub(super) fn number_files(files: &[String]) -> Vec<(u32, Option<String>, String)> {
    let entries: Vec<(String, String)> = files
        .iter()
        .map(|name| (stem(name).to_string(), name.clone()))
        .collect();
    assign_unique_numbers(&entries)
}

/// The name a chapter's number and inferred title are read from: its own
/// folder name, or the course name when the videos sit in the root.
pub(super) fn chapter_label(root: &Path, dir: &Path) -> String {
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
pub(super) fn chapter_title(root: &Path, dir: &Path) -> Option<String> {
    let relative = dir.strip_prefix(root).ok()?;
    let parts: Vec<String> = relative
        .components()
        .filter_map(|c| {
            let raw = c.as_os_str().to_string_lossy().to_string();
            let (_, cleaned) = split_number_and_title(&raw);
            cleaned.or(Some(raw)).filter(|t| !t.is_empty())
        })
        .collect();
    (!parts.is_empty()).then(|| parts.join(" / "))
}
