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
use mlib_spec::Kind;

use super::args::AddDocuArgs;
use crate::config::Config;
use crate::index::db;
use crate::metadata::resolve::{self, ResolveInput};
use crate::upload::new_set::NewSet;
use crate::upload::session::link::TelegramLink;
use crate::upload::session::{Item, Outcome, Session, Set, Step};

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

    // The same title the dry run reports and `prepare_and_record_set` (via
    // `resolve::docu_file`) resolves again for the upload itself — resolved
    // twice on purpose, since a standalone documentary is never looked up at
    // any provider and has nothing else to cache the answer in.
    let resolved = resolve::docu_file(
        args.title.as_deref(),
        &ResolveInput {
            file_name: file_name.clone(),
            ..ResolveInput::default()
        },
    );
    // Validated before anything is uploaded, so a bad `--category` fails the
    // same way whether or not `--dry-run` was given.
    let category = args
        .category
        .as_deref()
        .map(|raw| crate::edit::category::planned(Kind::Docu, resolved.title.as_deref(), raw))
        .transpose()?;

    if args.dry_run {
        println!("documentary: {}", resolved.title.as_deref().unwrap_or("-"));
        println!("file:        {}", args.path.display());
        if let Some(category) = &category {
            println!("category:    {}", category.category.as_deref().unwrap_or("-"));
        }
        return Ok(());
    }

    if let Some(category) = &category {
        let conn = db::open(&cfg.data_dir()?)?;
        crate::edit::category::write(&conn, category)?;
        drop(conn);
    }

    let new = NewSet {
        file: args.path.clone(),
        variant: args.variant.clone(),
        no_remux: args.no_remux,
        docu: true,
        docu_title: args.title.clone(),
        ..NewSet::default()
    };
    // No identity to find it by, so it is planned anew every time, as a
    // single `add` is.
    let item = Item {
        tag: (),
        set: Set::File(new),
        delete_source: None,
    };
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let counts = session
        .upload([item], |(), step| match step {
            Step::Start => println!("uploading {file_name}"),
            Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => println!("  {err:#}"),
            Step::End(_) => {}
        })
        .await;
    session.end(args.no_push).await?;
    anyhow::ensure!(
        counts.failed + counts.blocked == 0,
        "{file_name} was not uploaded"
    );
    Ok(())
}
