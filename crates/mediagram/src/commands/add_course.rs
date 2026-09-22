//! `mediagram add-course`: walk a course folder and upload every lesson.
//!
//! Each lesson is an ordinary set, so this adds no upload machinery: it
//! decides what to upload, in what order, and what to skip, then calls the
//! same path `add` uses. The index is pushed once at the end rather than per
//! lesson, which is what the locked rule says a bulk session should do.

use anyhow::{Context, Result, bail};

use super::args::{AddArgs, AddCourseArgs};
use crate::config::Config;
use crate::course::report::{Outcome, Summary, dry_run_table};
use crate::course::walk::walk_course;
use crate::index::status::SetStatus;
use crate::index::{db, sets};

pub async fn run(cfg: &Config, args: AddCourseArgs) -> Result<()> {
    let course = course_title(&args)?;
    let cid = match &args.cid {
        Some(explicit) => explicit.clone(),
        None => {
            let derived = mlib_spec::slug::slug(&course);
            if derived.is_empty() {
                bail!(
                    "cannot derive a collection id from `{course}`; pass --cid with an id of your own"
                );
            }
            derived
        }
    };

    let walked = walk_course(&args.dir)?;
    if walked.is_empty() {
        println!("nothing to upload under {}", args.dir.display());
        return Ok(());
    }

    // Defence in depth: identity is what decides whether a lesson is skipped,
    // so two lessons sharing one would make the second unreachable forever.
    // The walker guarantees uniqueness; this refuses to upload if that ever
    // stops being true, rather than silently dropping content.
    if let Some((chapter, lesson)) = duplicate_identity(&walked.lessons) {
        bail!(
            "two lessons would share chapter {chapter} lesson {lesson}, so one \
             would be skipped as already uploaded; this is a bug in the walk, \
             please report the folder layout"
        );
    }

    if args.dry_run {
        for line in dry_run_table(&course, &cid, &walked) {
            println!("{line}");
        }
        return Ok(());
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let mut summary = Summary::default();
    for lesson in &walked.lessons {
        // Identity is the collection id plus the two numbers, so a re-run
        // after an interruption skips what finished without depending on
        // where the folder happens to live.
        let outcome = match sets::lesson_status(&conn, &cid, lesson.chapter, lesson.lesson)? {
            Some(SetStatus::Complete) => Outcome::AlreadyDone,
            Some(_) => Outcome::Pending,
            None => match upload_one(cfg, &args, &course, &cid, lesson).await {
                Ok(()) => Outcome::Uploaded,
                // One unreadable file must not abandon the rest of the course.
                Err(err) => {
                    println!("  lesson {}: {err:#}", lesson.lesson);
                    Outcome::Failed
                }
            },
        };
        summary.record_lesson(outcome);
    }

    // Documents after the lessons: the videos are what someone is waiting
    // for, and a handout is worth having a minute later.
    for document in &walked.documents {
        let outcome = match sets::document_status(&conn, &cid, document.chapter, document.number)? {
            Some(SetStatus::Complete) => Outcome::AlreadyDone,
            Some(_) => Outcome::Pending,
            None => match upload_document(cfg, &args, &course, &cid, document).await {
                Ok(()) => Outcome::Uploaded,
                // One unreadable handout must not abandon the rest.
                Err(err) => {
                    println!("  document {}: {err:#}", document.number);
                    Outcome::Failed
                }
            },
        };
        summary.record_document(outcome);
    }
    drop(conn);

    for line in summary.lines() {
        println!("{line}");
    }

    if summary.uploaded_anything() && !args.no_push {
        super::push_index::push_after_set(cfg)
            .await
            .context("pushing the index after the course")?;
    }
    if summary.failed() > 0 {
        bail!("{} set(s) failed", summary.failed());
    }
    Ok(())
}

async fn upload_one(
    cfg: &Config,
    args: &AddCourseArgs,
    course: &str,
    cid: &str,
    lesson: &crate::course::walk::Lesson,
) -> Result<()> {
    println!(
        "uploading c{:02}l{:02} {}",
        lesson.chapter,
        lesson.lesson,
        lesson.title.as_deref().unwrap_or("")
    );
    super::add::run(
        cfg,
        AddArgs {
            file: lesson.path.clone(),
            tmdb: None,
            tvdb: None,
            imdb: None,
            season: None,
            episode: None,
            abs_no: None,
            variant: args.variant.clone(),
            manual: false,
            no_remux: args.no_remux,
            alang: None,
            slang: None,
            hdr: None,
            // Pushed once when the walk finishes, not per lesson.
            no_push: true,
            // A course is walked from a folder the caller still wants.
            delete_source: false,
            // A course is uploaded lesson by lesson, in this process.
            watch: true,
            course: Some(course.to_string()),
            cid: Some(cid.to_string()),
            chapter: Some(lesson.chapter),
            chap: lesson.chapter_title.clone(),
            // Empty means the lesson sat at the course root, which the
            // caption spells as absent rather than as an empty string.
            path: Some(lesson.rel_path.clone()).filter(|p| !p.is_empty()),
            lesson: Some(lesson.lesson),
        },
    )
    .await
}

/// Uploads one document: the same parts and captions a lesson gets, with
/// none of the probing or remuxing a video needs.
async fn upload_document(
    cfg: &Config,
    args: &AddCourseArgs,
    course: &str,
    cid: &str,
    document: &crate::course::walk::Document,
) -> Result<()> {
    println!(
        "uploading c{:02}d{:02} {}",
        document.chapter,
        document.number,
        document.title.as_deref().unwrap_or("")
    );
    super::add_document::run(
        cfg,
        &super::add_document::Document {
            file: document.path.clone(),
            course: course.to_string(),
            cid: cid.to_string(),
            chapter: document.chapter,
            chapter_title: document.chapter_title.clone(),
            // Empty means the document sat at the course root, which the
            // caption spells as absent rather than as an empty string.
            path: Some(document.rel_path.clone()).filter(|p| !p.is_empty()),
            number: document.number,
            title: document.title.clone(),
            variant: args.variant.clone(),
        },
    )
    .await
}

fn course_title(args: &AddCourseArgs) -> Result<String> {
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
fn duplicate_identity(lessons: &[crate::course::walk::Lesson]) -> Option<(u32, u32)> {
    let mut seen = std::collections::BTreeSet::new();
    lessons
        .iter()
        .find(|l| !seen.insert((l.chapter, l.lesson)))
        .map(|l| (l.chapter, l.lesson))
}
