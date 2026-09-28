//! A documentary collection, e.g. "Terra X": every video becomes a
//! `Kind::Docu` episode, numbered and grouped exactly as `add-course` numbers
//! and groups a course's lessons — the walk, identity and dry-run table are
//! `add-course`'s own ([`walk_course`], [`course_title`], [`collection_id`],
//! [`dry_run_table`]), reused whole.

use anyhow::{Result, bail};
use mlib_spec::Kind;

use super::AddDocuArgs;
use crate::config::Config;
use crate::course::identity::{collection_id, course_title, duplicate_identity};
use crate::course::report::dry_run_table;
use crate::course::upload::{self, Walk};
use crate::course::walk::walk_course;
use crate::index::{artwork, db};
use crate::upload::session::Session;
use crate::upload::session::link::TelegramLink;

pub(super) async fn run(cfg: &Config, args: AddDocuArgs) -> Result<()> {
    let collection = course_title(args.title.as_deref(), &args.path)?;
    let cid = collection_id(&collection, args.cid.as_deref())?;
    // Validated before anything is uploaded, so a bad `--category` fails the
    // same way whether or not `--dry-run` was given.
    let category = args
        .category
        .as_deref()
        .map(|raw| crate::edit::category::planned(Kind::Docu, Some(&collection), raw))
        .transpose()?;

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
        if let Some(category) = &category {
            println!("category: {}", category.category.as_deref().unwrap_or("-"));
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
    if let Some(category) = &category {
        crate::edit::category::write(&conn, category)?;
    }

    drop(conn);
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let walk = Walk {
        course: &walked,
        title: &collection,
        cid: &cid,
        kind: Kind::Docu,
        variant: args.variant.clone(),
        no_remux: args.no_remux,
    };
    let summary = upload::upload(&mut session, &walk).await;
    for line in summary.lines() {
        println!("{}", in_docu_words(&line));
    }
    session.end(args.no_push).await?;
    if summary.failed_count() > 0 {
        bail!("{} set(s) failed", summary.failed_count());
    }
    Ok(())
}

/// The course report, worded for a documentary collection: its videos are
/// episodes and its top folder is the collection, not a course.
fn in_docu_words(line: &str) -> String {
    line.replace("lesson(s)", "episode(s)")
        .replace("(course root)", "(collection root)")
}
