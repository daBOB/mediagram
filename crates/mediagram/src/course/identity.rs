//! What identifies a course and its lessons: the title the course is filed
//! under, the collection id that groups its lessons, and the rule that no two
//! lessons may share a chapter and number.

use std::path::Path;

use anyhow::{Context, Result, bail};

use crate::course::walk::Lesson;

/// The title a course is filed under: the one given, else its folder's name.
pub fn course_title(given: Option<&str>, dir: &Path) -> Result<String> {
    if let Some(title) = given {
        return Ok(title.to_string());
    }
    dir.canonicalize()
        .unwrap_or_else(|_| dir.to_path_buf())
        .file_name()
        .and_then(|n| n.to_str())
        .map(|n| n.to_string())
        .with_context(|| format!("cannot read a course title from {}", dir.display()))
}

/// The id grouping a course's lessons: the one given, else the title's slug.
///
/// Refused when the title slugs to nothing — a title of only punctuation, or
/// only a script the slug drops — because an empty id would file every such
/// course under the same one.
pub fn collection_id(course: &str, given: Option<&str>) -> Result<String> {
    if let Some(explicit) = given {
        return Ok(explicit.to_string());
    }
    let derived = mlib_spec::slug::slug(course);
    if derived.is_empty() {
        bail!("cannot derive a collection id from `{course}`; pass --cid with an id of your own");
    }
    Ok(derived)
}

/// The first `(chapter, lesson)` pair claimed twice, if any.
pub fn duplicate_identity(lessons: &[Lesson]) -> Option<(u32, u32)> {
    let mut seen = std::collections::BTreeSet::new();
    lessons
        .iter()
        .find(|l| !seen.insert((l.chapter, l.lesson)))
        .map(|l| (l.chapter, l.lesson))
}
