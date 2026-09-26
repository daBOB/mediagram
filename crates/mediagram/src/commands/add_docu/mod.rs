//! `mediagram add-docu`: upload a documentary — one file, or a folder of them
//! grouped as a collection (e.g. "Terra X").
//!
//! A collection folder is walked exactly as a course is: `add-course`'s walk,
//! dry-run table and sidecar handling are reused whole, in [`collection`];
//! the only difference is the kind each entry becomes (`Kind::Docu` rather
//! than `Kind::Tut`) and that `poster.*`/`backdrop.*` at the folder root are
//! picked up as the collection's art. A single file skips all of that and is
//! uploaded here as one standalone documentary, titled from its file name
//! unless `--title` overrides it — never looked up at any provider.

mod collection;

use anyhow::{Context, Result, bail};

use super::args::AddDocuArgs;
use crate::commands::pull_index;
use crate::config::Config;
use crate::metadata::resolve::{self, ResolveInput};
use crate::upload::finish_set::Uploader;
use crate::upload::new_set::NewSet;
use crate::upload::prepare_set::prepare_and_record_set;

pub async fn run(cfg: &Config, args: AddDocuArgs) -> Result<()> {
    if args.path.is_file() {
        return run_file(cfg, args).await;
    }
    if !args.path.is_dir() {
        bail!("{} is neither a file nor a directory", args.path.display());
    }
    collection::run(cfg, args).await
}

/// One standalone documentary: no course, no lookup, titled from the file
/// name unless `--title` overrides it.
async fn run_file(cfg: &Config, args: AddDocuArgs) -> Result<()> {
    let file_name = args
        .path
        .file_name()
        .and_then(|n| n.to_str())
        .with_context(|| format!("{} has no usable file name", args.path.display()))?
        .to_string();

    if args.dry_run {
        let resolved = resolve::docu_file(
            args.title.as_deref(),
            &ResolveInput {
                file_name,
                ..ResolveInput::default()
            },
        );
        println!("documentary: {}", resolved.title.as_deref().unwrap_or("-"));
        println!("file:        {}", args.path.display());
        return Ok(());
    }

    let new = NewSet {
        file: args.path.clone(),
        variant: args.variant.clone(),
        no_remux: args.no_remux,
        docu: true,
        docu_title: args.title.clone(),
        ..NewSet::default()
    };
    let planned = prepare_and_record_set(cfg, &new).await?;
    println!("uploading {}", planned.display_name);
    let mut uploader = Uploader::new(cfg);
    uploader.finish(&planned.set_id, None).await?;
    uploader.close().await;

    if !args.no_push {
        let message_id = pull_index::merge_and_publish(cfg)
            .await
            .context("pushing the index after the documentary")?;
        println!("pushed index as message {message_id}");
    }
    Ok(())
}
