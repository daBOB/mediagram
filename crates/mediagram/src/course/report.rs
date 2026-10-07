//! What a course walk prints: the dry-run table and the closing summary.
//! Pure, so the output can be asserted without touching a disk or a network.

use crate::course::upload::video_words;
use crate::course::walk::Course;
use crate::upload::session::Counts;
use mlib_spec::Kind;

fn failed(counts: &Counts) -> u32 {
    counts.failed + counts.blocked
}

/// Lessons and documents counted apart, because they fail apart: a course
/// whose videos all went up and whose handouts all failed is a different
/// situation from the reverse, and one number cannot say which happened.
/// Each is what the upload session counted for that walk.
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct Summary {
    pub lessons: Counts,
    pub documents: Counts,
}

impl Summary {
    /// How many lessons and documents failed to upload, a missing source
    /// among them.
    pub fn failed_count(&self) -> u32 {
        failed(&self.lessons) + failed(&self.documents)
    }

    /// The closing lines, the videos named for `kind`: lessons, or a
    /// documentary's episodes.
    pub fn lines(&self, kind: Kind) -> Vec<String> {
        let (_, noun) = video_words(kind);
        let mut out = vec![format!(
            "{} {noun}(s) uploaded, {} already done, {} failed",
            self.lessons.uploaded,
            self.lessons.held,
            failed(&self.lessons)
        )];
        // Said only when there were any. A course of pure video should not
        // have to read a line of zeroes about documents it does not have.
        if self.documents != Counts::default() {
            out.push(format!(
                "{} document(s) uploaded, {} already done, {} failed",
                self.documents.uploaded,
                self.documents.held,
                failed(&self.documents)
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
    /// `L` for a lesson, `E` for a documentary episode, `D` for a document
    /// — the same letters the caption uses, so a row here and a name in the
    /// channel read alike.
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
///
/// `kind` is what the videos are recorded as: a documentary collection's are
/// episodes under a collection, not lessons under a course.
pub fn dry_run_table(course: &str, cid: &str, walked: &Course, kind: Kind) -> Vec<String> {
    let (mark, noun) = video_words(kind);
    let whole = if kind == Kind::Docu {
        "collection"
    } else {
        "course"
    };
    let mut out = vec![
        format!("{whole}: {course}"),
        format!("id:     {cid}"),
        String::new(),
    ];

    // BTreeMap so folders print in path order, which is the order someone
    // browsing the course on disk would see them.
    let mut by_folder: std::collections::BTreeMap<&str, Vec<Row>> =
        std::collections::BTreeMap::new();
    for lesson in &walked.lessons {
        by_folder
            .entry(lesson.rel_path.as_str())
            .or_default()
            .push(Row {
                mark: mark.to_ascii_uppercase(),
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
        // `D` sorts before `E` and `L`, which would put every handout above
        // its video.
        rows.sort_by_key(|row| (row.number, u8::from(row.mark == 'D')));
        let heading = if folder.is_empty() {
            format!("({whole} root)")
        } else {
            folder.to_string()
        };
        let videos = rows.iter().filter(|r| r.mark != 'D').count();
        let documents = rows.len() - videos;
        out.push(format!("{heading}  ({})", counted(noun, videos, documents)));
        for row in rows {
            out.push(format!("  {} {:>3}  {}", row.mark, row.number, row.title));
        }
        out.push(String::new());
    }

    out.push(format!(
        "{} across {} folder(s)",
        counted(noun, walked.lessons.len(), walked.documents.len()),
        by_folder.len()
    ));
    out
}

/// `3 lesson(s)`, or `3 lesson(s), 1 document(s)` when there are any; a
/// collection's videos are counted as episodes.
///
/// Documents are named only when the course has some. A course of pure video
/// should not have to read the word at all.
fn counted(noun: &str, videos: usize, documents: usize) -> String {
    if documents == 0 {
        return format!("{videos} {noun}(s)");
    }
    format!("{videos} {noun}(s), {documents} document(s)")
}
