//! `mediagram add-course`: walk a course folder and upload every lesson.
//!
//! Each lesson is an ordinary set, so this adds no upload machinery: it
//! walks the folder and hands the walk to an upload session
//! (`course::upload`), which skips what finished and publishes once at the
//! end.

use anyhow::{Result, bail};

use super::args::AddCourseArgs;
use crate::config::Config;
use crate::course::identity::{collection_id, course_title, duplicate_identity};
use crate::course::report::dry_run_table;
use crate::course::upload::{self, Walk};
use crate::course::walk::walk_course;
use crate::index::{artwork, db};
use crate::upload::session::Session;
use crate::upload::session::link::TelegramLink;
use mlib_spec::Kind;

pub async fn run(cfg: &Config, args: AddCourseArgs) -> Result<()> {
    let course = course_title(args.course.as_deref(), &args.dir)?;
    let cid = collection_id(&course, args.cid.as_deref())?;
    // Validated before anything is uploaded, so a bad `--category` fails the
    // same way whether or not `--dry-run` was given.
    let category = args
        .category
        .as_deref()
        .map(|raw| crate::edit::category::planned(Kind::Tut, Some(&course), raw))
        .transpose()?;

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
        if let Some(category) = &category {
            println!("category: {}", category.category.as_deref().unwrap_or("-"));
        }
        return Ok(());
    }

    let conn = db::open(&cfg.data_dir()?)?;
    if let Some(key) = mlib_spec::package::title_art_key(&course) {
        match artwork::adopt_folder(&conn, &args.dir, &key) {
            Ok(0) => {}
            Ok(n) => println!("picked up {n} artwork file(s) from {}", args.dir.display()),
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
        title: &course,
        cid: &cid,
        kind: Kind::Tut,
        variant: args.variant.clone(),
        no_remux: args.no_remux,
    };
    let summary = upload::upload(&mut session, &walk).await;
    for line in summary.lines() {
        println!("{line}");
    }
    session.end(args.no_push).await?;
    if summary.failed_count() > 0 {
        bail!("{} set(s) failed", summary.failed_count());
    }
    Ok(())
}
