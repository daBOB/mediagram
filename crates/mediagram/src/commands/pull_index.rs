//! `mediagram pull-index`: merge the channel index into the local index.
//!
//! Two machines can publish to one channel, and a publish replaces the
//! channel index wholesale; this brings in what another machine published
//! and reports what changed. Every publish does it first anyway — this is
//! for looking before publishing, or with `--dry-run`, for only looking.

use anyhow::Result;

use crate::channel_index;
use crate::config::Config;

/// Arguments for `mediagram pull-index`.
#[derive(clap::Args, Debug, Clone, Default)]
pub struct PullIndexArgs {
    /// Show what a merge would do without writing anything
    #[arg(long)]
    pub dry_run: bool,
}

pub async fn run(cfg: &Config, args: PullIndexArgs) -> Result<()> {
    channel_index::pull_from_channel(cfg, args.dry_run).await
}
