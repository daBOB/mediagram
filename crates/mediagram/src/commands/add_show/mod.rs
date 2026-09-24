//! `mediagram add-show`: walk a series folder and upload every episode.
//!
//! Like `add-course`, this adds no upload machinery: it decides what to
//! upload and what to skip, then calls the same path `add` uses, and pushes
//! the index once at the end rather than per episode.
//!
//! What it adds is a look before the leap. A show is tens of files and
//! hundreds of gigabytes, and two things about it are worth knowing before
//! any of that moves: which file is about to be filed as which episode, and
//! whether the player will end up converting every one of them on every play.
//! Both are cheap to answer here and expensive to discover afterwards.

mod survey;

use std::io::IsTerminal;

use anyhow::{Context, Result, bail};

use super::args::AddShowArgs;
use crate::config::Config;
use crate::index::status::SetStatus;
use crate::index::{db, set_lookup};
use crate::media::show_episodes::{Episode, duplicate_episode, walk};
use crate::paths::file_name;
use crate::telegram::index_publish;
use crate::upload::finish_set::Uploader;
use crate::upload::new_set::NewSet;
use crate::upload::prepare_set::prepare_and_record_set;
use survey::{report_blockers, survey};

pub async fn run(cfg: &Config, args: AddShowArgs) -> Result<()> {
    let episodes = walk(&args.dir)?;
    if episodes.is_empty() {
        println!("no episodes under {}", args.dir.display());
        return Ok(());
    }
    let tmdb = args.tmdb.context(
        "add-show needs --tmdb: it resolves once for the whole show, and without an id \
         every episode would be looked up on its own — which a release prefix in these \
         names turns into the same prompt, once per file",
    )?;

    // Two files claiming one episode is not a thing to resolve quietly: the
    // second would be skipped as already uploaded, so which one reached the
    // channel would come down to sort order. A folder holding both an mkv and
    // its converted mp4 looks exactly like this.
    if let Some((season, episode, files)) = duplicate_episode(&episodes) {
        bail!(
            "S{season:02}E{episode:02} is claimed by {} files:\n  {}\nOne would be uploaded and \
             the rest skipped as already held, decided by sort order. Point --dir at a folder \
             holding one file per episode, or move the others aside.",
            files.len(),
            files.join("\n  ")
        );
    }

    print_table(&episodes, tmdb);

    let blockers = survey(&episodes).await;
    report_blockers(&blockers, &args.dir);

    if args.dry_run {
        println!("\ndry run: nothing was uploaded.");
        return Ok(());
    }
    if !blockers.is_empty() && !args.yes && !confirm()? {
        println!("nothing was uploaded.");
        return Ok(());
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let mut uploader = Uploader::new(cfg);
    let (mut uploaded, mut skipped, mut pending, mut failed) = (0usize, 0usize, 0usize, 0usize);
    for ep in &episodes {
        match set_lookup::episode_status(&conn, tmdb, ep.season, ep.episode)? {
            Some(SetStatus::Complete) => {
                println!("S{:02}E{:02} already uploaded", ep.season, ep.episode);
                skipped += 1;
                continue;
            }
            Some(SetStatus::Pending) => {
                println!(
                    "S{:02}E{:02} pending; run mediagram resume",
                    ep.season, ep.episode
                );
                pending += 1;
                continue;
            }
            None => {}
        }
        println!(
            "uploading S{:02}E{:02} {}",
            ep.season,
            ep.episode,
            file_name(&ep.path)
        );
        match upload_one(cfg, &mut uploader, tmdb, ep, args.delete_source).await {
            Ok(()) => uploaded += 1,
            // One unreadable file must not abandon the rest of the show.
            Err(err) => {
                println!("  S{:02}E{:02}: {err:#}", ep.season, ep.episode);
                failed += 1;
            }
        }
    }
    drop(conn);
    uploader.close().await;

    println!("\n{uploaded} uploaded, {skipped} already held, {failed} failed");
    if pending > 0 {
        println!("{pending} pending; run mediagram resume to finish them");
    }
    if uploaded > 0 && !args.no_push {
        let message_id = index_publish::publish(cfg)
            .await
            .context("pushing the index after the show")?;
        println!("pushed index as message {message_id}");
    }
    if failed > 0 {
        bail!("{failed} episode(s) failed");
    }
    Ok(())
}

async fn upload_one(
    cfg: &Config,
    uploader: &mut Uploader<'_>,
    tmdb: u64,
    ep: &Episode,
    delete: bool,
) -> Result<()> {
    let new = NewSet {
        file: ep.path.clone(),
        tmdb: Some(tmdb),
        season: Some(ep.season),
        episode: Some(ep.episode),
        ..NewSet::default()
    };
    let planned = prepare_and_record_set(cfg, &new).await?;
    // The index is pushed once when the show is done, not per episode.
    uploader
        .finish(&planned.set_id, delete.then_some(ep.path.as_path()))
        .await?;
    Ok(())
}

/// Asks before uploading something that will play badly.
///
/// A pipe or a cron job gets on with it: stopping to ask where nobody can
/// answer would hang a batch that was deliberately left running.
fn confirm() -> Result<bool> {
    if !std::io::stdin().is_terminal() {
        println!("(not a terminal, continuing; pass --yes to silence this)");
        return Ok(true);
    }
    print!("\nUpload anyway? [y/N] ");
    use std::io::Write;
    std::io::stdout().flush().ok();
    let mut answer = String::new();
    std::io::stdin()
        .read_line(&mut answer)
        .context("reading the answer")?;
    Ok(matches!(answer.trim(), "y" | "Y" | "yes"))
}

fn print_table(episodes: &[Episode], tmdb: u64) {
    println!("{} episode(s) for tmdb {tmdb}", episodes.len());
    for ep in episodes {
        println!(
            "  S{:02}E{:02}  {}",
            ep.season,
            ep.episode,
            file_name(&ep.path)
        );
    }
}
