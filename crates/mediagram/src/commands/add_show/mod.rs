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
use crate::index::{artwork, db};
use crate::media::show_episodes::{Episode, duplicate_episode, walk};
use crate::paths::file_name;
use crate::upload::new_set::NewSet;
use crate::upload::session::link::TelegramLink;
use crate::upload::session::{Item, Outcome, Session, Set, Step};
use survey::survey;

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

    let compatibility = survey(&episodes).await;
    compatibility.report(&args.dir);

    if args.dry_run {
        println!("\ndry run: nothing was uploaded.");
        return Ok(());
    }
    if compatibility.needs_confirmation() && !args.yes && !confirm()? {
        println!("nothing was uploaded.");
        return Ok(());
    }

    let conn = db::open(&cfg.data_dir()?)?;
    let art_key = mediagram_tmdb::posters::poster_key(mlib_spec::Kind::Ep, tmdb);
    match artwork::adopt_folder(&conn, &args.dir, &art_key) {
        Ok(0) => {}
        Ok(n) => println!("picked up {n} artwork file(s) from {}", args.dir.display()),
        Err(err) => println!("artwork not stored: {err:#}"),
    }
    drop(conn);
    let mut session = Session::new(cfg, TelegramLink::new(cfg))?;
    let items = episodes
        .iter()
        .map(|ep| episode_item(tmdb, ep, args.delete_source));
    let counts = session
        .upload(items, |ep: &&Episode, step| {
            let code = format!("S{:02}E{:02}", ep.season, ep.episode);
            match step {
                Step::Start => println!("uploading {code} {}", file_name(&ep.path)),
                Step::End(Outcome::AlreadyHeld) => println!("{code} already uploaded"),
                Step::End(Outcome::Pending) => println!("{code} pending; run mediagram resume"),
                Step::End(Outcome::Failed(err) | Outcome::Blocked(err)) => {
                    println!("  {code}: {err:#}");
                }
                Step::End(Outcome::Uploaded) => {}
            }
        })
        .await;
    let failed = counts.failed + counts.blocked;
    println!(
        "\n{} uploaded, {} already held, {failed} failed",
        counts.uploaded, counts.held
    );
    if counts.pending > 0 {
        println!(
            "{} pending; run mediagram resume to finish them",
            counts.pending
        );
    }
    session.end(args.no_push).await?;
    if failed > 0 {
        bail!("{failed} episode(s) failed");
    }
    Ok(())
}

/// One episode, known by its show and numbers, so a re-run skips what
/// finished. Its file goes once its set is complete, if asked.
fn episode_item(tmdb: u64, ep: &Episode, delete: bool) -> Item<&Episode> {
    Item {
        tag: ep,
        set: Set::File(NewSet {
            file: ep.path.clone(),
            tmdb: Some(tmdb),
            season: Some(ep.season),
            episode: Some(ep.episode),
            ..NewSet::default()
        }),
        delete_source: delete.then(|| ep.path.clone()),
    }
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
