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

use std::io::IsTerminal;
use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use futures::stream::{self, StreamExt};

use super::args::{AddArgs, AddShowArgs};
use crate::config::Config;
use crate::index::{db, sets};
use crate::media::direct_play::{self, Blocker};
use crate::media::streams;

/// How many files are probed at once. Each spawns an ffprobe, so this is
/// bounded by processes rather than by bandwidth.
const PROBE_CONCURRENCY: usize = 4;

/// One file, and the episode it will be filed as.
pub struct Episode {
    pub path: PathBuf,
    pub season: u32,
    pub episode: u32,
}

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
    let (mut uploaded, mut skipped, mut failed) = (0usize, 0usize, 0usize);
    for ep in &episodes {
        if sets::episode_status(&conn, tmdb, ep.season, ep.episode)?.as_deref() == Some("complete")
        {
            println!("S{:02}E{:02} already uploaded", ep.season, ep.episode);
            skipped += 1;
            continue;
        }
        println!(
            "uploading S{:02}E{:02} {}",
            ep.season,
            ep.episode,
            name(&ep.path)
        );
        match upload_one(cfg, tmdb, ep).await {
            Ok(()) => uploaded += 1,
            // One unreadable file must not abandon the rest of the show.
            Err(err) => {
                println!("  S{:02}E{:02}: {err:#}", ep.season, ep.episode);
                failed += 1;
            }
        }
    }
    drop(conn);

    println!("\n{uploaded} uploaded, {skipped} already held, {failed} failed");
    if uploaded > 0 && !args.no_push {
        super::push_index::push_after_set(cfg)
            .await
            .context("pushing the index after the show")?;
    }
    if failed > 0 {
        bail!("{failed} episode(s) failed");
    }
    Ok(())
}

async fn upload_one(cfg: &Config, tmdb: u64, ep: &Episode) -> Result<()> {
    super::add::run(
        cfg,
        AddArgs {
            file: ep.path.clone(),
            tmdb: Some(tmdb),
            season: Some(ep.season),
            episode: Some(ep.episode),
            // The index is pushed once when the show is done.
            no_push: true,
            ..Default::default()
        },
    )
    .await
}

/// Every video file under `dir` that names a season and an episode.
///
/// The numbering comes from the file name, which is the one part of these
/// names that release prefixes leave alone: `mlib_spec` reads S01E02 out of
/// them correctly even where it reads the title badly.
pub fn walk(dir: &Path) -> Result<Vec<Episode>> {
    if !dir.is_dir() {
        bail!("{} is not a directory", dir.display());
    }
    // `prepare`'s walk, which also skips the working files a crashed run
    // leaves behind — those look like episodes and would clash with the real
    // ones, aborting the show.
    let files = super::prepare::collect(dir)?;

    let mut episodes = Vec::new();
    for path in files {
        let Some(guess) = mlib_spec::filename::parse_filename(&name(&path)) else {
            continue;
        };
        match (guess.season, guess.episode) {
            (Some(season), Some(episode)) => episodes.push(Episode {
                path,
                season,
                episode,
            }),
            // A file with no episode number is not an episode: a trailer or
            // an extra, and filing it under a guessed number would be worse
            // than leaving it out.
            _ => println!("skipping {} — no season/episode in the name", name(&path)),
        }
    }
    Ok(episodes)
}

/// The first episode number claimed by more than one file, with their names.
pub fn duplicate_episode(episodes: &[Episode]) -> Option<(u32, u32, Vec<String>)> {
    for (i, ep) in episodes.iter().enumerate() {
        let same: Vec<String> = episodes[i..]
            .iter()
            .filter(|other| other.season == ep.season && other.episode == ep.episode)
            .map(|other| name(&other.path))
            .collect();
        if same.len() > 1 {
            return Some((ep.season, ep.episode, same));
        }
    }
    None
}

/// Asks each file whether a browser could open it.
///
/// Probes run together: each spawns an ffprobe, and a season of them one at a
/// time is a minute of nothing happening before the question is even asked.
async fn survey(episodes: &[Episode]) -> Vec<Blocker> {
    stream::iter(episodes)
        .map(|ep| async move {
            let probed = streams::probe(&ep.path).await.ok()?;
            Some(direct_play::blockers(&ep.path, &probed.streams))
        })
        .buffered(PROBE_CONCURRENCY)
        .filter_map(|found| async move { found })
        .flat_map(stream::iter)
        .collect()
        .await
}

fn report_blockers(blockers: &[Blocker], dir: &Path) {
    if blockers.is_empty() {
        return;
    }
    println!();
    // Split by what the viewer can do about it, not by what is wrong: one
    // group has a command to run and the other does not.
    let (fixable, stuck): (Vec<&Blocker>, Vec<&Blocker>) =
        blockers.iter().partition(|b| b.fixable_by_prepare());

    if !fixable.is_empty() {
        println!(
            "{} file(s) will be converted on every play: {}",
            fixable.len(),
            reasons(&fixable).join("; ")
        );
        println!(
            "   mediagram prepare \"{}\" --mp4 --out <dir> would fix that",
            dir.display()
        );
    }
    if !stuck.is_empty() {
        println!(
            "{} file(s) will be converted on every play: {}",
            stuck.len(),
            reasons(&stuck).join(" / ")
        );
        println!("   prepare cannot fix that — the picture itself would have to be re-encoded");
    }
}

/// The distinct reasons in a group, in a stable order.
fn reasons(blockers: &[&Blocker]) -> Vec<String> {
    let mut named: Vec<String> = blockers.iter().map(|b| b.reason()).collect();
    named.sort_unstable();
    named.dedup();
    named
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
        println!("  S{:02}E{:02}  {}", ep.season, ep.episode, name(&ep.path));
    }
}

fn name(path: &Path) -> String {
    path.file_name()
        .map(|n| n.to_string_lossy().to_string())
        .unwrap_or_default()
}
