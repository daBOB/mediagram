//! What identifies a course and its lessons: the title the course is filed
//! under, and the rule that no two lessons may share a chapter and number.

use anyhow::{Context, Result};

use crate::commands::args::AddCourseArgs;
use crate::course::walk::Lesson;

pub fn course_title(args: &AddCourseArgs) -> Result<String> {
    if let Some(title) = &args.course {
        return Ok(title.clone());
    }
    args.dir
        .canonicalize()
        .unwrap_or_else(|_| args.dir.clone())
        .file_name()
        .and_then(|n| n.to_str())
        .map(|n| n.to_string())
        .with_context(|| format!("cannot read a course title from {}", args.dir.display()))
}

/// The first `(chapter, lesson)` pair claimed twice, if any.
pub fn duplicate_identity(lessons: &[Lesson]) -> Option<(u32, u32)> {
    let mut seen = std::collections::BTreeSet::new();
    lessons
        .iter()
        .find(|l| !seen.insert((l.chapter, l.lesson)))
        .map(|l| (l.chapter, l.lesson))
}
