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

/// The table shown before anything uploads, so inferred numbers and titles
/// can be corrected while correcting them is still cheap.
pub fn dry_run_table(course: &str, cid: &str, lessons: &[Lesson]) -> Vec<String> {
    let mut out = vec![
        format!("course: {course}"),
        format!("id:     {cid}"),
        String::new(),
        format!("{:>3}  {:>3}  {:<28} {}", "ch", "les", "title", "file"),
    ];
    for lesson in lessons {
        out.push(format!(
            "{:>3}  {:>3}  {:<28} {}",
            lesson.chapter,
            lesson.lesson,
            lesson.title.as_deref().unwrap_or("-"),
            lesson
                .path
                .file_name()
                .map(|n| n.to_string_lossy().to_string())
                .unwrap_or_default()
        ));
    }
    let chapters: std::collections::BTreeSet<u32> = lessons.iter().map(|l| l.chapter).collect();
    out.push(String::new());
    out.push(format!(
        "{} lesson(s) across {} chapter(s)",
        lessons.len(),
        chapters.len()
    ));
    out
}
