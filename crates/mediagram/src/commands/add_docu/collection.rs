//! A documentary collection, e.g. "Terra X": every video becomes a
//! `Kind::Docu` episode, numbered and grouped exactly as `add-course` numbers
//! and groups a course's lessons — the walk, identity and dry-run table are
//! `add-course`'s own ([`walk_course`], [`course_title`], [`collection_id`],
//! [`dry_run_table`]), reused whole.

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;

use super::AddDocuArgs;
use crate::config::Config;
use crate::course::identity::{collection_id, course_title, duplicate_identity};
use crate::course::report::{Outcome, Summary, dry_run_table};
use crate::course::walk::{Document, Lesson, walk_course};
use crate::index::status::SetStatus;
use crate::index::{artwork, db, set_lookup};
use crate::telegram::index_publish;
use crate::upload::finish_set::Uploader;
use crate::upload::new_set::{LessonOf, NewSet};
use crate::upload::prepare_set::prepare_and_record_set;
use crate::upload::record_document::{Document as DocumentSet, record_document_set};

pub(super) async fn run(cfg: &Config, args: AddDocuArgs) -> Result<()> {
    let collection = course_title(args.title.as_deref(), &args.path)?;
    let cid = collection_id(&collection, args.cid.as_deref())?;

    let walked = walk_course(&args.path)?;
    if walked.is_empty() {
        println!("nothing to upload under {}", args.path.display());
        return Ok(());
    }

    // Same defence add-course keeps: identity is what decides whether an
    // episode is skipped, so two sharing one would make the second
    // unreachable forever.
    if let Some((chapter, number)) = duplicate_identity(&walked.lessons) {
        bail!(
            "two episodes would share chapter {chapter} number {number}, so one would be \
             skipped as already uploaded; this is a bug in the walk, please report the folder \
             layout"
        );
    }

    if args.dry_run {
        for line in dry_run_table(&collection, &cid, &walked) {
            println!("{}", in_docu_words(&line));
        }
        return Ok(());
    }

    let conn = db::open(&cfg.data_dir()?)?;
    if let Some(key) = mlib_spec::package::title_art_key(&collection) {
        match artwork::adopt_folder(&conn, &args.path, &key) {
            Ok(0) => {}
            Ok(n) => println!("picked up {n} artwork file(s) from {}", args.path.display()),
            Err(err) => println!("artwork not stored: {err:#}"),
        }
    }

    let mut uploader = Uploader::new(cfg);
    let mut summary = Summary::default();
    for episode in &walked.lessons {
        let outcome =
            match set_lookup::lesson_status(&conn, &cid, episode.chapter, episode.lesson)? {
                Some(SetStatus::Complete) => Outcome::AlreadyDone,
                Some(_) => Outcome::Pending,
                None => match upload_episode(cfg, &mut uploader, &args, &collection, &cid, episode)
                    .await
                {
                    Ok(()) => Outcome::Uploaded,
                    // One unreadable file must not abandon the rest.
                    Err(err) => {
                        println!("  episode {}: {err:#}", episode.lesson);
                        Outcome::Failed
                    }
                },
            };
        summary.record_lesson(outcome);
    }

    for document in &walked.documents {
        let outcome =
            match set_lookup::document_status(&conn, &cid, document.chapter, document.number)? {
                Some(SetStatus::Complete) => Outcome::AlreadyDone,
                Some(_) => Outcome::Pending,
                None => match upload_document(cfg, &mut uploader, &args, &collection, &cid, document)
                    .await
                {
                    Ok(()) => Outcome::Uploaded,
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
        println!("{}", in_docu_words(&line));
    }

    if summary.uploaded_anything() && !args.no_push {
        let message_id = index_publish::publish(cfg)
            .await
            .context("pushing the index after the collection")?;
        println!("pushed index as message {message_id}");
    }
    if summary.failed_count() > 0 {
        bail!("{} set(s) failed", summary.failed_count());
    }
    Ok(())
}

async fn upload_episode(
    cfg: &Config,
    uploader: &mut Uploader<'_>,
    args: &AddDocuArgs,
    collection: &str,
    cid: &str,
    episode: &Lesson,
) -> Result<()> {
    println!(
        "uploading c{:02}e{:02} {}",
        episode.chapter,
        episode.lesson,
        episode.title.as_deref().unwrap_or("")
    );
    let new = NewSet {
        file: episode.path.clone(),
        variant: args.variant.clone(),
        no_remux: args.no_remux,
        lesson: Some(LessonOf {
            course: collection.to_string(),
            cid: cid.to_string(),
            chapter: Some(episode.chapter),
            chapter_title: episode.chapter_title.clone(),
            path: Some(episode.rel_path.clone()).filter(|p| !p.is_empty()),
            number: Some(episode.lesson),
            kind: Kind::Docu,
        }),
        ..NewSet::default()
    };
    let planned = prepare_and_record_set(cfg, &new).await?;
    uploader.finish(&planned.set_id, None).await?;
    Ok(())
}

async fn upload_document(
    cfg: &Config,
    uploader: &mut Uploader<'_>,
    args: &AddDocuArgs,
    collection: &str,
    cid: &str,
    document: &Document,
) -> Result<()> {
    println!(
        "uploading c{:02}d{:02} {}",
        document.chapter,
        document.number,
        document.title.as_deref().unwrap_or("")
    );
    let set_id = record_document_set(
        cfg,
        &DocumentSet {
            file: document.path.clone(),
            course: collection.to_string(),
            cid: cid.to_string(),
            chapter: document.chapter,
            chapter_title: document.chapter_title.clone(),
            path: Some(document.rel_path.clone()).filter(|p| !p.is_empty()),
            number: document.number,
            title: document.title.clone(),
            variant: args.variant.clone(),
        },
    )?;
    uploader.finish(&set_id, None).await?;
    Ok(())
}

/// The course report, worded for a documentary collection: its videos are
/// episodes and its top folder is the collection, not a course.
fn in_docu_words(line: &str) -> String {
    line.replace("lesson(s)", "episode(s)")
        .replace("(course root)", "(collection root)")
}
