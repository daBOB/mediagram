//! Pulling the channel index into the local index, and publishing the local
//! index as the channel index (see `CONTEXT.md`).
//!
//! More than one machine publishes to one channel, and a publish replaces the
//! channel index wholesale, so every publish pulls first: what another machine
//! published is merged in, not dropped. The whole round trip lives here —
//! choosing the current channel index, downloading and merging it, noticing a
//! publish that lands while this one is under way, sending, pinning, and
//! clearing the pins it replaces — so callers only say which kind of publish
//! they want.
//!
//! The channel is reached only through [`remote::ChannelRemote`]; Telegram
//! only in `telegram_remote` and the two entry points below that connect.

mod backup_path;
mod conflicts;
mod live;
mod message_gone;
mod publish;
mod pull;
pub mod remote;
mod report;
mod telegram_remote;
mod unpin;

use std::path::PathBuf;

use anyhow::{Context, Result};

use crate::config::Config;
use crate::telegram::client::Tg;
use remote::ChannelRemote;
pub use telegram_remote::TelegramRemote;

/// MIME type the index document is sent under.
const INDEX_MIME_TYPE: &str = "application/vnd.sqlite3";

/// Which kind of publish.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    /// Pull what the channel holds first, so nothing another machine
    /// published is dropped. What every publish does unless told otherwise.
    AfterPull,
    /// Replace the channel index with the local one as it is, pulling
    /// nothing: for recovering from a channel index that is itself wrong.
    Force,
}

/// The channel index as seen from one machine's data directory.
pub struct ChannelIndex<'r, R> {
    remote: &'r R,
    data_dir: PathBuf,
}

impl<'r, R: ChannelRemote> ChannelIndex<'r, R> {
    pub fn new(remote: &'r R, data_dir: impl Into<PathBuf>) -> Self {
        ChannelIndex {
            remote,
            data_dir: data_dir.into(),
        }
    }

    /// Merges the current channel index into the local index, or with
    /// `dry_run` reports what a merge would do and writes nothing. Returns
    /// whether the channel index lacks the subtitle tables, which only a
    /// publish can restore; never on a dry run.
    pub async fn pull(&self, dry_run: bool) -> Result<bool> {
        let current = pull::current(self.remote).await?;
        pull::pull_from(self.remote, &self.data_dir, current.as_ref(), dry_run).await
    }

    /// Publishes the local index as the channel index, returning the id of
    /// the message now pinned. One publish at a time per machine; with
    /// [`Mode::AfterPull`], a publish landing from elsewhere meanwhile is
    /// pulled in too, and one that keeps landing fails the publish.
    pub async fn publish(&self, mode: Mode) -> Result<i32> {
        publish::publish(self.remote, &self.data_dir, mode).await
    }
}

/// `pull-index`: connects, pulls, disconnects. When the merge finds that the
/// channel index has no subtitle tables — an uploader older than this schema
/// published over them — it also republishes, restoring them: the only case
/// where a plain pull sends anything.
pub async fn pull_from_channel(cfg: &Config, dry_run: bool) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let remote = TelegramRemote::new(&tg, cfg.max_attempts);
    let index = ChannelIndex::new(&remote, data_dir);
    let result = match index.pull(dry_run).await {
        Ok(true) if !dry_run => {
            println!("publishing again to restore the channel's subtitle tables");
            index.publish(Mode::AfterPull).await.map(|_| ())
        }
        other => other.map(|_| ()),
    };
    tg.shutdown().await;
    result
}

/// Every command that publishes: connects, publishes, disconnects, and
/// returns the id of the message now pinned.
pub async fn publish_to_channel(cfg: &Config, mode: Mode) -> Result<i32> {
    let data_dir = cfg.data_dir()?;
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let remote = TelegramRemote::new(&tg, cfg.max_attempts);
    let result = ChannelIndex::new(&remote, data_dir).publish(mode).await;
    tg.shutdown().await;
    result
}
