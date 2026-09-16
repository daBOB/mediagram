//! What a course walk prints: the dry-run table and the closing summary.
//! Pure, so the output can be asserted without touching a disk or a network.

use crate::course::walk::Lesson;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Outcome {
    Uploaded,
    AlreadyDone,
    /// A set exists but never finished; `resume` owns it, not this command.
    Pending,
    Failed,
}

#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Summary {
    pub uploaded: u32,
    pub skipped: u32,
    pub pending: u32,
    pub failed: u32,
}

impl Summary {
    pub fn record(&mut self, outcome: Outcome, _lesson: &Lesson) {
        match outcome {
            Outcome::Uploaded => self.uploaded += 1,
            Outcome::AlreadyDone => self.skipped += 1,
            Outcome::Pending => self.pending += 1,
            Outcome::Failed => self.failed += 1,
        }
    }

    pub fn lines(&self) -> Vec<String> {
        let mut out = vec![format!(
            "{} uploaded, {} already done, {} failed",
            self.uploaded, self.skipped, self.failed
        )];
        if self.pending > 0 {
            out.push(format!(
                "{} lesson(s) were started but never finished; run `mediagram resume`",
                self.pending
            ));
        }
        out
    }
}

/// What a course walk will upload, grouped by the folder each lesson came
/// from, so inferred numbers and titles can be corrected while correcting
/// them is still cheap.
///
/// Grouped rather than tabulated because chapter numbers are made unique
/// across the whole course: two sections that each call something "chapter 1"
/// appear as 2 and 5, and a flat table then shows numbers nobody recognises.
/// The folder is what a person named.
pub fn dry_run_table(course: &str, cid: &str, lessons: &[Lesson]) -> Vec<String> {
    let mut out = vec![
        format!("course: {course}"),
        format!("id:     {cid}"),
        String::new(),
    ];

    // BTreeMap so folders print in path order, which is the order someone
    // browsing the course on disk would see them.
    let mut by_folder: std::collections::BTreeMap<&str, Vec<&Lesson>> =
        std::collections::BTreeMap::new();
    for lesson in lessons {
        by_folder
            .entry(lesson.rel_path.as_str())
            .or_default()
            .push(lesson);
    }

    for (folder, mut items) in by_folder.iter().map(|(k, v)| (*k, v.clone())) {
        items.sort_by_key(|lesson| lesson.lesson);
        let heading = if folder.is_empty() {
            "(course root)".to_string()
        } else {
            folder.to_string()
        };
        out.push(format!("{heading}  ({} lesson(s))", items.len()));
        for lesson in items {
            out.push(format!(
                "  {:>3}  {}",
                lesson.lesson,
                lesson.title.as_deref().unwrap_or("-")
            ));
        }
        out.push(String::new());
    }

    out.push(format!(
        "{} lesson(s) across {} folder(s)",
        lessons.len(),
        by_folder.len()
    ));
    out
}
