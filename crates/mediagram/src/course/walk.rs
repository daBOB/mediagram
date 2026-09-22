//! Reading a course folder into an ordered list of lessons and documents.
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
//!
//! A course is not only video. Handouts sit beside lessons and workbooks sit
//! in folders holding no video at all, and both are walked here alongside the
//! lessons — see `number_chapters` for the one rule that keeps adding them
//! from disturbing what is already uploaded.

use std::collections::BTreeMap;
use std::path::{Path, PathBuf};

use anyhow::Result;

use crate::course::folders::{Folder, collect};
use crate::course::naming::{chapter_label, chapter_title, number_files, relative_path};
use crate::course::plan::assign_unique_numbers;
use crate::media::file_names::split_number_and_title;

/// One lesson: where its file is, and where it sits in the course.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Lesson {
    pub path: PathBuf,
    /// Folders between the course root and this lesson, `/`-separated, empty
    /// for a lesson sitting at the root.
    ///
    /// Chapter numbers are made unique across the course, because sections
    /// number their own chapters from 1 and the identity has to stay
    /// distinct. That renumbering is correct and also erases the shape, so
    /// the path is what a player rebuilds the tree from.
    pub rel_path: String,
    pub chapter: u32,
    pub chapter_title: Option<String>,
    pub lesson: u32,
    pub title: Option<String>,
}

/// One document: a handout beside a lesson, or a workbook in a folder that
/// holds no video.
///
/// Carries the same fields a lesson does because it is filed the same way:
/// numbered inside its chapter, so a course page that orders a level by the
/// number each entry leads with puts `03 Signal.pdf` on the row beside
/// `03 Signal.mp4` without anything having to pair the two.
///
/// `number` is its own sequence, independent of the lesson numbers in the
/// same chapter. A document and a lesson sharing a number is the intended
/// case, not a collision: they are different kinds and the index keys them
/// apart.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Document {
    pub path: PathBuf,
    pub rel_path: String,
    pub chapter: u32,
    pub chapter_title: Option<String>,
    pub number: u32,
    pub title: Option<String>,
}

/// What a walk found, in upload order.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Course {
    pub lessons: Vec<Lesson>,
    pub documents: Vec<Document>,
}

impl Course {
    /// True when there is nothing at all to upload.
    pub fn is_empty(&self) -> bool {
        self.lessons.is_empty() && self.documents.is_empty()
    }
}

/// Walks a course folder. Every directory holding video files becomes a
/// chapter, numbered in path order; the files inside it become its lessons,
/// and any documents beside them become its documents.
///
/// Lesson numbers are unique within a chapter by construction. A file's own
/// leading number is honoured when it can be, but two files numbered `1` in
/// one folder would otherwise collapse onto a single identity and the second
/// would be skipped forever as "already uploaded".
pub fn walk_course(root: &Path) -> Result<Course> {
    if !root.is_dir() {
        anyhow::bail!("{} is not a directory", root.display());
    }

    let mut by_folder: BTreeMap<PathBuf, Folder> = BTreeMap::new();
    collect(root, &mut by_folder)?;

    let mut course = Course::default();
    for (chapter, inferred_title, dir) in number_chapters(root, &by_folder) {
        let Some(folder) = by_folder.get(&dir) else {
            continue;
        };
        let chapter_title = chapter_title(root, &dir).or(inferred_title);
        let rel_path = relative_path(root, &dir);
        for (lesson, title, file) in number_files(&folder.videos) {
            course.lessons.push(Lesson {
                path: dir.join(&file),
                rel_path: rel_path.clone(),
                chapter,
                chapter_title: chapter_title.clone(),
                lesson,
                title,
            });
        }
        for (number, title, file) in number_files(&folder.documents) {
            course.documents.push(Document {
                path: dir.join(&file),
                rel_path: rel_path.clone(),
                chapter,
                chapter_title: chapter_title.clone(),
                number,
                title,
            });
        }
    }
    Ok(course)
}

/// Chapter numbers, and the rule that keeps a course already uploaded from
/// being uploaded a second time.
///
/// `assign_unique_numbers` renumbers a whole group `1..n` the moment two
/// declared numbers collide, which in a real course they always do. So the
/// numbering must be computed from **the video-holding folders and only
/// those**, exactly as it was before documents were walked at all. A folder
/// holding nothing but PDFs that joined this call would shift every chapter
/// number after it — and chapter plus lesson *is* a lesson's identity, so a
/// re-run of `add-course` would match nothing and upload the whole course
/// again.
///
/// Document-only folders therefore take numbers continuing after the last
/// video chapter. Nothing is lost by putting them last: a player places a
/// folder by its `path`, not by this number.
fn number_chapters(
    root: &Path,
    by_folder: &BTreeMap<PathBuf, Folder>,
) -> Vec<(u32, Option<String>, PathBuf)> {
    let with_video: Vec<(String, PathBuf)> = by_folder
        .iter()
        .filter(|(_, folder)| !folder.videos.is_empty())
        .map(|(dir, _)| (chapter_label(root, dir), dir.clone()))
        .collect();
    let mut chapters = assign_unique_numbers(&with_video);

    let mut next = chapters.iter().map(|(n, _, _)| *n).max().unwrap_or(0);
    for (dir, folder) in by_folder {
        if !folder.videos.is_empty() {
            continue;
        }
        next += 1;
        let (_, inferred) = split_number_and_title(&chapter_label(root, dir));
        chapters.push((next, inferred, dir.clone()));
    }
    chapters
}

