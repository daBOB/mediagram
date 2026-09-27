//! Publishing: pull, snapshot, check nothing landed meanwhile, send, pin,
//! clear the pins it replaces — one machine at a time.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use super::remote::{Candidate, ChannelRemote};
use super::{Mode, pull, unpin};
use crate::clock::now_unix;
use crate::index::{db, pins, sets, snapshot};
use crate::upload::lock;

/// How many looks at the channel index a publish takes before giving up:
/// it pulls again after each of the first two finds the channel index moved
/// — another machine published within the seconds this one took to merge
/// and snapshot — and fails on the third.
const ATTEMPTS: usize = 3;

/// Beside `upload.lock`: two processes on this machine — two background
/// uploads finishing together — publish one after the other, the second
/// pulling in the first rather than racing it for the pins.
const LOCK_FILE: &str = "publish.lock";

pub(super) async fn publish(
    remote: &impl ChannelRemote,
    data_dir: &Path,
    mode: Mode,
) -> Result<i32> {
    let _lock = lock::acquire_file(&data_dir.join(LOCK_FILE), || {
        println!("waiting for another publish on this machine to finish");
    })
    .await?;
    let conn = db::open(data_dir)?;
    if mode == Mode::Force {
        let snapshot = Snapshot::take(&conn, data_dir)?;
        return send(remote, &conn, &snapshot.0).await;
    }

    let mut seen = pull::current(remote).await?;
    for _ in 0..ATTEMPTS {
        if id_of(&seen) != pins::pulled(&conn)? {
            pull::pull_from(remote, data_dir, seen.as_ref(), false).await?;
        }
        let snapshot = Snapshot::take(&conn, data_dir)?;
        // The window a publish from elsewhere could land in closes here; one
        // landing after this is at most seconds older than ours, and ours
        // holds everything up to the pull.
        let now = pull::current(remote).await?;
        if id_of(&now) == id_of(&seen) {
            return send(remote, &conn, &snapshot.0).await;
        }
        seen = now;
    }
    bail!(
        "the channel index kept changing while publishing: another machine published \
         {ATTEMPTS} times in a row. Nothing was sent; publish again once it has finished"
    )
}

fn id_of(candidate: &Option<Candidate>) -> Option<i32> {
    candidate.as_ref().map(|c| c.id)
}

async fn send(remote: &impl ChannelRemote, conn: &Connection, path: &Path) -> Result<i32> {
    let caption = mlib_spec::index_caption::render(now_unix(), i64::try_from(sets::count(conn)?)?);
    let id = remote
        .send_index(path, &caption)
        .await
        .context("sending the index document")?;
    remote.pin(id).await.context("pinning the index message")?;
    unpin::unpin_previous(remote, conn).await?;
    pins::record_current(conn, id)?;
    // What was just published holds everything the local index does.
    pins::record_pulled(conn, Some(id))?;
    Ok(id)
}

/// A consistent copy of the local index to send, removed however the
/// publish ends. Named per process, like the downloaded channel index.
struct Snapshot(PathBuf);

impl Snapshot {
    fn take(conn: &Connection, data_dir: &Path) -> Result<Snapshot> {
        snapshot::checkpoint(conn).context("checkpointing before snapshot")?;
        let path = data_dir.join(format!("library.push.{}.db", std::process::id()));
        let _ = std::fs::remove_file(&path);
        snapshot::snapshot_to(conn, &path).context("snapshotting the index")?;
        Ok(Snapshot(path))
    }
}

impl Drop for Snapshot {
    fn drop(&mut self) {
        if let Err(err) = std::fs::remove_file(&self.0) {
            tracing::warn!(path = %self.0.display(), error = %err, "failed to remove the publish snapshot");
        }
    }
}
