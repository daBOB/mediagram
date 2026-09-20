//! What a course walk prints: the dry-run table and the closing summary.
//! Pure, so the output can be asserted without touching a disk or a network.

use crate::course::walk::Course;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Outcome {
    Uploaded,
    AlreadyDone,
    /// A set exists but never finished; `resume` owns it, not this command.
    Pending,
    Failed,
}

/// How one kind of entry fared.
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct Counts {
    pub uploaded: u32,
    pub skipped: u32,
    pub pending: u32,
    pub failed: u32,
}

impl Counts {
    fn record(&mut self, outcome: Outcome) {
        match outcome {
            Outcome::Uploaded => self.uploaded += 1,
            Outcome::AlreadyDone => self.skipped += 1,
            Outcome::Pending => self.pending += 1,
            Outcome::Failed => self.failed += 1,
        }
    }
}

/// Lessons and documents counted apart, because they fail apart: a course
/// whose videos all went up and whose handouts all failed is a different
/// situation from the reverse, and one number cannot say which happened.
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct Summary {
    pub lessons: Counts,
    pub documents: Counts,
}

impl Summary {
    pub fn record_lesson(&mut self, outcome: Outcome) {
        self.lessons.record(outcome);
    }

    pub fn record_document(&mut self, outcome: Outcome) {
        self.documents.record(outcome);
    }

    /// Whether anything at all was sent, which is what decides if the index
    /// is worth pushing.
    pub fn uploaded_anything(&self) -> bool {
        self.lessons.uploaded + self.documents.uploaded > 0
    }

    pub fn failed(&self) -> u32 {
        self.lessons.failed + self.documents.failed
    }

    pub fn lines(&self) -> Vec<String> {
        let mut out = vec![format!(
            "{} lesson(s) uploaded, {} already done, {} failed",
            self.lessons.uploaded, self.lessons.skipped, self.lessons.failed
        )];
        // Said only when there were any. A course of pure video should not
        // have to read a line of zeroes about documents it does not have.
        if self.documents != Counts::default() {
            out.push(format!(
                "{} document(s) uploaded, {} already done, {} failed",
                self.documents.uploaded, self.documents.skipped, self.documents.failed
            ));
        }
        let pending = self.lessons.pending + self.documents.pending;
        if pending > 0 {
            out.push(format!(
                "{pending} set(s) were started but never finished; run `mediagram resume`"
            ));
        }
        out
    }
}

/// One row of the dry-run table.
struct Row {
    /// `L` for a lesson, `D` for a document — the same letters the caption
    /// uses, so a row here and a name in the channel read alike.
    mark: char,
    number: u32,
    title: String,
}

/// What a course walk will upload, grouped by the folder each entry came
/// from, so inferred numbers and titles can be corrected while correcting
/// them is still cheap.
///
/// Grouped rather than tabulated because chapter numbers are made unique
/// across the whole course: two sections that each call something "chapter 1"
/// appear as 2 and 5, and a flat table then shows numbers nobody recognises.
/// The folder is what a person named.
///
/// A lesson and its handout carry the same number on purpose, so the mark is
/// what tells two otherwise identical rows apart.
pub fn dry_run_table(course: &str, cid: &str, walked: &Course) -> Vec<String> {
    let mut out = vec![
        format!("course: {course}"),
        format!("id:     {cid}"),
        String::new(),
    ];

    // BTreeMap so folders print in path order, which is the order someone
    // browsing the course on disk would see them.
    let mut by_folder: std::collections::BTreeMap<&str, Vec<Row>> =
        std::collections::BTreeMap::new();
    for lesson in &walked.lessons {
        by_folder.entry(lesson.rel_path.as_str()).or_default().push(Row {
            mark: 'L',
            number: lesson.lesson,
            title: lesson.title.clone().unwrap_or_else(|| "-".into()),
        });
    }
    for document in &walked.documents {
        by_folder
            .entry(document.rel_path.as_str())
            .or_default()
            .push(Row {
                mark: 'D',
                number: document.number,
                title: document.title.clone().unwrap_or_else(|| "-".into()),
            });
    }

    for (folder, mut rows) in by_folder.iter_mut().map(|(k, v)| (*k, std::mem::take(v))) {
        // Lessons before documents at the same number, so a handout reads as
        // belonging to the lesson above it. By mark rather than alphabetically:
        // `D` sorts before `L`, which would put every handout above its lesson.
        rows.sort_by_key(|row| (row.number, u8::from(row.mark == 'D')));
        let heading = if folder.is_empty() {
            "(course root)".to_string()
        } else {
            folder.to_string()
        };
        let lessons = rows.iter().filter(|r| r.mark == 'L').count();
        let documents = rows.len() - lessons;
        out.push(format!("{heading}  ({})", counted(lessons, documents)));
        for row in rows {
            out.push(format!("  {} {:>3}  {}", row.mark, row.number, row.title));
        }
        out.push(String::new());
    }

    out.push(format!(
        "{} across {} folder(s)",
        counted(walked.lessons.len(), walked.documents.len()),
        by_folder.len()
    ));
    out
}

/// `3 lesson(s)`, or `3 lesson(s), 1 document(s)` when there are any.
///
/// Documents are named only when the course has some. A course of pure video
/// should not have to read the word at all.
fn counted(lessons: usize, documents: usize) -> String {
    if documents == 0 {
        return format!("{lessons} lesson(s)");
    }
    format!("{lessons} lesson(s), {documents} document(s)")
}
