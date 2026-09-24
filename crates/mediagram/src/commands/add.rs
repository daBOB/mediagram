//! `mediagram add`: inspect → resolve → remux → plan → index → upload, the
//! last step either watched here or handed to a background process.
//!
//! Everything up to the upload is [`prepare_and_record_set`], which `add-show` and
//! `add-course` share; this command only turns its flags into a [`NewSet`].

use anyhow::Result;

use super::args::AddArgs;
use super::{background, finish_set};
use crate::config::Config;
use crate::course::identity::collection_id;
use crate::upload::new_set::{LessonOf, NewSet};
use crate::upload::prepare_set::prepare_and_record_set;

pub async fn run(cfg: &Config, args: AddArgs) -> Result<()> {
    let (watch, no_push) = (args.watch, args.no_push);
    let to_delete = args.delete_source.then(|| args.file.clone());
    let planned = prepare_and_record_set(cfg, &new_set(args)?).await?;

    // Everything that can ask a question or refuse has happened: the file was
    // inspected, the title resolved, the caption measured, the rows written.
    // What is left is bytes, which is the part worth handing away.
    if watch {
        return finish_set::run(cfg, &planned.set_id, to_delete.as_deref(), no_push).await;
    }
    // Asked before the child is started, because the child is what will be
    // holding it a moment later.
    let queued = crate::upload::lock::is_held(&cfg.data_dir()?);
    let started =
        background::spawn_finish_set(cfg, &planned.set_id, to_delete.as_deref(), no_push)?;
    println!(
        "set {} planned · {} · {:.2} GB",
        planned.set_id,
        planned.display_name,
        planned.total as f64 / 1e9
    );
    println!(
        "  {} (pid {}); `mediagram status` says how far it has got",
        if queued {
            "queued behind the upload already running"
        } else {
            "uploading in the background"
        },
        started.pid
    );
    println!("  output: {}", started.log.display());
    Ok(())
}

/// What the flags ask for, as the planner takes it.
fn new_set(args: AddArgs) -> Result<NewSet> {
    let lesson = match args.course {
        Some(course) => Some(LessonOf {
            cid: collection_id(&course, args.cid.as_deref())?,
            course,
            chapter: args.chapter,
            chapter_title: args.chap,
            path: args.path,
            number: args.lesson,
        }),
        None => None,
    };
    Ok(NewSet {
        file: args.file,
        tmdb: args.tmdb,
        tvdb: args.tvdb,
        imdb: args.imdb,
        season: args.season,
        episode: args.episode,
        abs: args.abs_no,
        variant: args.variant,
        manual: args.manual,
        no_remux: args.no_remux,
        alang: args.alang,
        slang: args.slang,
        hdr: args.hdr,
        lesson,
    })
}
