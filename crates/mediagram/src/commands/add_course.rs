//! `mediagram add-course`: walk a course folder and upload every lesson.
//!
//! Each lesson is an ordinary set, so this adds no upload machinery: it
//! decides what to upload, in what order, and what to skip, then calls the
//! same path `add` uses. The index is pushed once at the end rather than per
//! lesson, which is what the locked rule says a bulk session should do.

use anyhow::{Context, Result, bail};

use super::args::AddCourseArgs;
use crate::config::Config;
use crate::course::identity::{collection_id, course_title, duplicate_identity};
use crate::course::report::{Outcome, Summary, dry_run_table};
use crate::course::walk::walk_course;
use crate::index::status::SetStatus;
use crate::index::{db, set_lookup};
use crate::telegram::index_publish;
use crate::upload::finish_set::Uploader;
use crate::upload::new_set::{LessonOf, NewSet};
use crate::upload::plan_document::{Document, plan_document};
use crate::upload::plan_set::plan_set;

pub async fn run(cfg: &Config, args: AddCourseArgs) -> Result<()> {
    let course = course_title(args.course.as_deref(), &args.dir)?;
    let cid = collection_id(&course, args.cid.as_deref())?;

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
    let mut uploader = Uploader::new(cfg);
    let mut summary = Summary::default();
    for lesson in &walked.lessons {
        // Identity is the collection id plus the two numbers, so a re-run
        // after an interruption skips what finished without depending on
        // where the folder happens to live.
        let outcome = match set_lookup::lesson_status(&conn, &cid, lesson.chapter, lesson.lesson)? {
            Some(SetStatus::Complete) => Outcome::AlreadyDone,
            Some(_) => Outcome::Pending,
            None => match upload_one(cfg, &mut uploader, &args, &course, &cid, lesson).await {
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
        let outcome =
            match set_lookup::document_status(&conn, &cid, document.chapter, document.number)? {
                Some(SetStatus::Complete) => Outcome::AlreadyDone,
                Some(_) => Outcome::Pending,
                None => match upload_document(cfg, &mut uploader, &args, &course, &cid, document)
                    .await
                {
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
    uploader.close().await;

    for line in summary.lines() {
        println!("{line}");
    }

    if summary.uploaded_anything() && !args.no_push {
        let message_id = index_publish::publish(cfg)
            .await
            .context("pushing the index after the course")?;
        println!("pushed index as message {message_id}");
    }
    if summary.failed_count() > 0 {
        bail!("{} set(s) failed", summary.failed_count());
    }
    Ok(())
}

async fn upload_one(
    cfg: &Config,
    uploader: &mut Uploader<'_>,
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
    let new = NewSet {
        file: lesson.path.clone(),
        variant: args.variant.clone(),
        no_remux: args.no_remux,
        lesson: Some(LessonOf {
            course: course.to_string(),
            cid: cid.to_string(),
            chapter: Some(lesson.chapter),
            chapter_title: lesson.chapter_title.clone(),
            // Empty means the lesson sat at the course root, which the
            // caption spells as absent rather than as an empty string.
            path: Some(lesson.rel_path.clone()).filter(|p| !p.is_empty()),
            number: Some(lesson.lesson),
        }),
        ..NewSet::default()
    };
    let planned = plan_set(cfg, &new).await?;
    // A course is walked from a folder the caller still wants, so nothing is
    // deleted; the index is pushed once when the walk finishes.
    uploader.finish(&planned.set_id, None).await?;
    Ok(())
}

/// Uploads one document: the same parts and captions a lesson gets, with
/// none of the probing or remuxing a video needs.
async fn upload_document(
    cfg: &Config,
    uploader: &mut Uploader<'_>,
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
    let set_id = plan_document(
        cfg,
        &Document {
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
    )?;
    uploader.finish(&set_id, None).await?;
    Ok(())
}
