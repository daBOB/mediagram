//! `mediagram pull-index`: merge the channel's index into this one.
//!
//! Two machines can publish to one channel, and a push replaces the
//! channel's index wholesale — so a machine that has fallen behind another
//! cannot publish everything until it has brought in what it lacks. This
//! downloads the channel's index, merges it (see `index::merge`), and
//! reports what changed.

mod conflicts;
mod keep_live;

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::clock::{backup_timestamp, now_unix};
use crate::config::Config;
use crate::index::merge::{self, MergeReport};
use crate::index::{db, snapshot};
use crate::telegram::client::Tg;
use crate::telegram::download_index::download_channel_index;
use keep_live::live_sets;

/// Arguments for `mediagram pull-index`.
#[derive(clap::Args, Debug, Clone, Default)]
pub struct PullIndexArgs {
    /// Show what a merge would do without writing anything
    #[arg(long)]
    pub dry_run: bool,
}

pub async fn run(cfg: &Config, args: PullIndexArgs) -> Result<()> {
    pull(cfg, args.dry_run).await.map(|_| ())
}

/// Merges the channel's index in and returns the sets it proved removed
/// from the channel, which a push that follows may drop (see `index_guard`).
pub async fn pull(cfg: &Config, dry_run: bool) -> Result<HashSet<String>> {
    let data_dir = cfg.data_dir()?;
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let result = run_with(cfg, &data_dir, &tg, dry_run).await;
    tg.shutdown().await;
    result
}

async fn run_with(
    cfg: &Config,
    data_dir: &Path,
    tg: &Tg,
    dry_run: bool,
) -> Result<HashSet<String>> {
    let Some(channel_path) = download_channel_index(tg, data_dir).await? else {
        println!("no index pinned in the channel; nothing to merge");
        return Ok(HashSet::new());
    };
    let outcome = merge_and_report(cfg, data_dir, tg, &channel_path, dry_run).await;
    let _ = std::fs::remove_file(&channel_path);
    outcome
}

async fn merge_and_report(
    cfg: &Config,
    data_dir: &Path,
    tg: &Tg,
    channel_path: &Path,
    dry_run: bool,
) -> Result<HashSet<String>> {
    if dry_run {
        let report = dry_run_merge(data_dir, channel_path, tg, cfg).await?;
        print_report(&report, true);
        if !report.conflicts.is_empty() {
            println!(
                "{} conflicting set(s) would be re-read from their captions",
                report.conflicts.len()
            );
        }
        return Ok(HashSet::new());
    }

    let local = db::open(data_dir)?;
    // Named to the process as well as the minute, and never replaced: a
    // second merge in the same minute must not overwrite the first one's
    // only rollback point.
    let backup_path = data_dir.join(format!(
        "library.before-channel-merge-{}-{}.db",
        backup_timestamp(now_unix()),
        std::process::id()
    ));
    anyhow::ensure!(
        !backup_path.exists(),
        "{} already exists; not overwriting a backup",
        backup_path.display()
    );
    snapshot::copy_to(&local, &backup_path).context("backing up the local index before merging")?;
    println!("backed up the local index to {}", backup_path.display());

    let live = live_sets(tg, cfg, &merge::channel_candidates(&local, channel_path)?).await?;
    let report = merge::merge_from(&local, channel_path, |_| Ok(live))
        .context("merging the channel's index")?;
    // Reported before the re-read: the merge has committed, and a failure
    // re-reading captions must not hide what it already changed.
    print_report(&report, false);
    let resolved = conflicts::resolve(&local, tg, cfg, &report.conflicts).await?;
    if !report.conflicts.is_empty() {
        println!(
            "{resolved} of {} conflicting set(s) re-read from captions",
            report.conflicts.len()
        );
    }
    Ok(report.sets_skipped_removed.into_iter().collect())
}

/// Runs the merge against a throwaway copy of the local index, so `--dry-run`
/// writes nothing to the real one; conflicts are only detected here, since
/// resolving them is itself a write.
async fn dry_run_merge(
    data_dir: &Path,
    channel_path: &Path,
    tg: &Tg,
    cfg: &Config,
) -> Result<MergeReport> {
    let real = db::open(data_dir)?;
    let live = live_sets(tg, cfg, &merge::channel_candidates(&real, channel_path)?).await?;
    let scratch_path = data_dir.join(format!(
        "library.pull-index-dry-run.{}.db",
        std::process::id()
    ));
    let outcome = (|| -> Result<MergeReport> {
        snapshot::copy_to(&real, &scratch_path).context("copying the local index for a dry run")?;
        let scratch = open_scratch(&scratch_path)?;
        merge::merge_from(&scratch, channel_path, |_| Ok(live))
            .context("merging the channel's index")
    })();
    let _ = std::fs::remove_file(&scratch_path);
    outcome
}

fn open_scratch(path: &Path) -> Result<Connection> {
    let conn = crate::index::sqlite_init::open(path)
        .with_context(|| format!("opening {}", path.display()))?;
    conn.pragma_update(None, "foreign_keys", true)
        .context("enabling foreign key enforcement")?;
    Ok(conn)
}

fn print_report(report: &MergeReport, dry_run: bool) {
    let verb = if dry_run { "would add" } else { "added" };
    println!("{verb} {} set(s)", report.sets_added.len());
    if !report.sets_skipped_pending.is_empty() {
        println!(
            "{} channel set(s) still pending elsewhere, skipped",
            report.sets_skipped_pending.len()
        );
    }
    if !report.sets_skipped_removed.is_empty() {
        println!(
            "{} channel set(s) no longer exist in the channel, skipped",
            report.sets_skipped_removed.len()
        );
    }
    if report.shows_added > 0 || report.shows_filled > 0 {
        println!(
            "{} show(s) added, {} filled in",
            report.shows_added, report.shows_filled
        );
    }
    if report.credits_added > 0 {
        println!("{} credit row(s) added", report.credits_added);
    }
    if report.franchises_added > 0 {
        println!("{} franchise(s) added", report.franchises_added);
    }
    if report.artwork_added > 0 {
        println!("{} artwork row(s) added", report.artwork_added);
    }
}
