//! Choosing the current channel index, and merging it into the local index.

use std::path::Path;

use anyhow::{Context, Result, ensure};
use rusqlite::Connection;

use super::backup_path::free_backup_path;
use super::live::live_sets;
use super::remote::{Candidate, ChannelRemote};
use super::{conflicts, report};
use crate::clock::{backup_timestamp, now_unix};
use crate::index::merge::{self, MergeReport};
use crate::index::{db, pins, snapshot};

/// The channel index: the newest snapshot the channel itself posted, among
/// its pins and the marker search (spec §7, `index_caption::newest`) — the
/// same candidates and the same choice every player makes.
///
/// Refuses a chosen snapshot whose caption names a schema newer than this
/// build understands — on every path, `--force` included, since every
/// publish and every plain pull starts here.
pub(super) async fn current(remote: &impl ChannelRemote) -> Result<Option<Candidate>> {
    let own: Vec<Candidate> = remote
        .candidates()
        .await
        .context("listing pinned messages")?
        .into_iter()
        .filter(|p| p.own_post)
        .collect();
    let candidates: Vec<(&str, i64)> = own
        .iter()
        .map(|p| (p.caption.as_str(), i64::from(p.id)))
        .collect();
    let chosen = mlib_spec::index_caption::newest(&candidates, now_unix()).map(|i| own[i].clone());
    if let Some(candidate) = &chosen {
        if let Some(schema) = mlib_spec::index_caption::schema(&candidate.caption) {
            ensure!(
                schema <= mlib_spec::schema::SCHEMA_VERSION,
                "the channel index is schema v{schema}, newer than this build (v{}); \
                 reinstall mediagram before pulling or publishing",
                mlib_spec::schema::SCHEMA_VERSION
            );
        }
    }
    Ok(chosen)
}

/// Merges `current` into the local index and records it as pulled; with
/// `dry_run`, reports what that would do and writes nothing.
pub(super) async fn pull_from(
    remote: &impl ChannelRemote,
    data_dir: &Path,
    current: Option<&Candidate>,
    dry_run: bool,
) -> Result<()> {
    let document = match current {
        Some(pinned) => remote
            .download(pinned.id)
            .await
            .context("downloading the channel index")?,
        None => None,
    };
    let Some(bytes) = document else {
        println!("no index pinned in the channel; nothing to merge");
        if !dry_run {
            pins::record_pulled(&db::open(data_dir)?, None)?;
        }
        return Ok(());
    };
    // Per process, so two commands pulling at once cannot collide.
    let path = data_dir.join(format!("library.channel.{}.db", std::process::id()));
    std::fs::write(&path, &bytes).with_context(|| format!("writing {}", path.display()))?;
    let outcome = if dry_run {
        dry_run_merge(remote, data_dir, &path).await.map(|()| false)
    } else {
        merge_in(remote, data_dir, &path).await
    };
    let _ = std::fs::remove_file(&path);
    // Recorded only once the local index holds all of it: a publish that
    // finds this id still current skips the pull, so a conflict left
    // unresolved must leave it unrecorded, for the next publish to retry.
    if outcome? {
        pins::record_pulled(&db::open(data_dir)?, current.map(|c| c.id))?;
    }
    Ok(())
}

/// Merges, then re-reads the conflicts from their captions. Returns whether
/// every conflict was resolved.
async fn merge_in(
    remote: &impl ChannelRemote,
    data_dir: &Path,
    channel_path: &Path,
) -> Result<bool> {
    let local = db::open(data_dir)?;
    let backup_path = free_backup_path(data_dir, &backup_timestamp(now_unix()));
    snapshot::copy_to(&local, &backup_path).context("backing up the local index before merging")?;
    println!("backed up the local index to {}", backup_path.display());

    let live = live_sets(remote, &merge::channel_candidates(&local, channel_path)?).await?;
    let merged = merge::merge_from(&local, channel_path, |_| Ok(live))
        .context("merging the channel index")?;
    if merged.channel_lacks_subtitles {
        pins::owe_publish(&local).context("recording the subtitle re-publish owed")?;
    }
    // Reported before the re-read: the merge has committed, and a failure
    // re-reading captions must not hide what it already changed.
    report::print(&merged, false);
    let summary = conflicts::resolve(&local, remote, &merged.conflicts).await?;
    report::print_conflicts(merged.conflicts.len(), &summary);
    Ok(summary.resolved == merged.conflicts.len())
}

/// Runs the merge against a throwaway copy of the local index, so a dry run
/// writes nothing to the real one; conflicts are only detected here, since
/// resolving them is itself a write.
async fn dry_run_merge(
    remote: &impl ChannelRemote,
    data_dir: &Path,
    channel_path: &Path,
) -> Result<()> {
    let real = db::open(data_dir)?;
    let live = live_sets(remote, &merge::channel_candidates(&real, channel_path)?).await?;
    let scratch_path = data_dir.join(format!(
        "library.pull-index-dry-run.{}.db",
        std::process::id()
    ));
    let outcome = (|| -> Result<MergeReport> {
        snapshot::copy_to(&real, &scratch_path).context("copying the local index for a dry run")?;
        let scratch = open_scratch(&scratch_path)?;
        merge::merge_from(&scratch, channel_path, |_| Ok(live)).context("merging the channel index")
    })();
    let _ = std::fs::remove_file(&scratch_path);
    let merged = outcome?;
    report::print(&merged, true);
    if !merged.conflicts.is_empty() {
        println!(
            "{} conflicting set(s) would be re-read from their captions",
            merged.conflicts.len()
        );
    }
    Ok(())
}

fn open_scratch(path: &Path) -> Result<Connection> {
    // Through `sqlite_init`, as every open here must: a bare open while the
    // Telegram session is live can abort the process.
    let conn = crate::index::sqlite_init::open(path)
        .with_context(|| format!("opening {}", path.display()))?;
    conn.pragma_update(None, "foreign_keys", true)
        .context("enabling foreign key enforcement")?;
    Ok(conn)
}

#[cfg(test)]
#[path = "pull_tests.rs"]
mod tests;
