//! Uploading a walked course — or a documentary collection, walked and
//! numbered the same way — through an upload session: its videos, then its
//! documents, each counted apart.

use crate::course::report::Summary;
use crate::course::walk::{Course, Document, Lesson};
use crate::upload::new_set::{LessonOf, NewSet};
use crate::upload::record_document::Document as DocumentSet;
use crate::upload::session::link::Link;
use crate::upload::session::{Item, Outcome, Session, Set, Step};
use mlib_spec::Kind;

/// One walked folder and what it is.
pub struct Walk<'a> {
    pub course: &'a Course,
    pub title: &'a str,
    pub cid: &'a str,
    /// What its videos are recorded as: `Tut` lessons or `Docu` episodes.
    pub kind: Kind,
    pub variant: Option<String>,
    pub no_remux: bool,
}

pub async fn upload<L: Link>(session: &mut Session<'_, L>, walk: &Walk<'_>) -> Summary {
    let (mark, noun) = match walk.kind {
        Kind::Docu => ('e', "episode"),
        _ => ('l', "lesson"),
    };
    let videos = walk.course.lessons.iter().map(|lesson| video(walk, lesson));
    let lessons = session
        .upload(videos, |lesson: &&Lesson, step| match step {
            Step::Start => println!(
                "uploading c{:02}{mark}{:02} {}",
                lesson.chapter,
                lesson.lesson,
                lesson.title.as_deref().unwrap_or("")
            ),
            Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => {
                println!("  {noun} {}: {err:#}", lesson.lesson);
            }
            Step::End(_) => {}
        })
        .await;
    // Documents after the videos: the videos are what someone is waiting
    // for, and a handout is worth having a minute later.
    let handouts = walk.course.documents.iter().map(|doc| document(walk, doc));
    let documents = session
        .upload(handouts, |doc: &&Document, step| match step {
            Step::Start => println!(
                "uploading c{:02}d{:02} {}",
                doc.chapter,
                doc.number,
                doc.title.as_deref().unwrap_or("")
            ),
            Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => {
                println!("  document {}: {err:#}", doc.number);
            }
            Step::End(_) => {}
        })
        .await;
    Summary { lessons, documents }
}

/// A video, identified by the collection and its two numbers so a re-run
/// skips what finished wherever the folder happens to live. Nothing is
/// deleted: the folder is one the person still wants.
fn video<'l>(walk: &Walk<'_>, lesson: &'l Lesson) -> Item<&'l Lesson> {
    let new = NewSet {
        file: lesson.path.clone(),
        variant: walk.variant.clone(),
        no_remux: walk.no_remux,
        lesson: Some(LessonOf {
            course: walk.title.to_string(),
            cid: walk.cid.to_string(),
            chapter: Some(lesson.chapter),
            chapter_title: lesson.chapter_title.clone(),
            // Empty means the video sat at the root, which the caption
            // spells as absent rather than as an empty string.
            path: Some(lesson.rel_path.clone()).filter(|p| !p.is_empty()),
            number: Some(lesson.lesson),
            kind: walk.kind,
        }),
        ..NewSet::default()
    };
    Item {
        tag: lesson,
        set: Set::File(new),
        delete_source: None,
    }
}

/// A document: the same parts and captions a video gets, with none of the
/// probing or remuxing.
fn document<'d>(walk: &Walk<'_>, doc: &'d Document) -> Item<&'d Document> {
    let set = DocumentSet {
        file: doc.path.clone(),
        course: walk.title.to_string(),
        cid: walk.cid.to_string(),
        chapter: doc.chapter,
        chapter_title: doc.chapter_title.clone(),
        path: Some(doc.rel_path.clone()).filter(|p| !p.is_empty()),
        number: doc.number,
        title: doc.title.clone(),
        variant: walk.variant.clone(),
    };
    Item {
        tag: doc,
        set: Set::Document(set),
        delete_source: None,
    }
}
