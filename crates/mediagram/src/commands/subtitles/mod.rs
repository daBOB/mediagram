//! `mediagram subtitles`: give titles their German and English subtitles as
//! bundles in the channel.
//!
//! - `move-inline` turns the lessons' inline subtitle rows into bundles.
//! - `backfill <folder>...` matches local media files against sets the
//!   channel carries and sends their subtitles; `--dry-run` only measures.
//! - `backfill --channel` reads the subtitles from the copy in the channel
//!   itself, through a loopback server on the one Telegram session.

pub mod backfill;
pub mod backfill_channel;
mod dry_run;
mod dry_run_report;
pub mod loopback;
pub mod match_source;
pub mod move_inline;
pub mod session;

use std::path::PathBuf;

use anyhow::{Result, bail};
use clap::{Args, Subcommand};

use crate::config::Config;

#[derive(Subcommand)]
pub enum SubtitlesAction {
    /// Send German and English subtitles of titles the channel already
    /// carries, from local media folders or from the channel's own copy
    Backfill(BackfillArgs),
    /// Move the lessons' inline subtitle rows into bundles, verified by
    /// reading each back from the channel
    MoveInline(MoveInlineArgs),
}

#[derive(Args, Debug, Clone)]
pub struct MoveInlineArgs {
    /// Count what would move; nothing is sent or written
    #[arg(long)]
    pub dry_run: bool,
    /// Do not publish the index afterwards
    #[arg(long)]
    pub no_push: bool,
}

#[derive(Args, Debug, Clone)]
pub struct BackfillArgs {
    /// Media folders to walk (never a drive root: a lecture tree of the same
    /// duration as an episode is exactly what the fallback can mismatch)
    #[arg(required_unless_present = "channel", conflicts_with = "channel")]
    pub folders: Vec<PathBuf>,
    /// Match and count only; nothing is extracted, sent or written
    #[arg(long)]
    pub dry_run: bool,
    /// Send a name-and-duration match too, for this file (repeatable). Such
    /// a match is never sent unless named here
    #[arg(long, value_name = "FILE", conflicts_with = "channel")]
    pub accept_fallback: Vec<PathBuf>,
    /// Replace this set's bundle: the new one is recorded, then the old
    /// message is deleted (repeatable)
    #[arg(long, value_name = "SET")]
    pub redo: Vec<String>,
    /// Do not publish the index afterwards
    #[arg(long)]
    pub no_push: bool,
    /// Read the subtitles from the copy in the channel instead of from
    /// folders: MP4 sets first
    #[arg(long)]
    pub channel: bool,
    /// With --channel, also read sets that are not MP4 (a full read each)
    #[arg(long)]
    pub mkv: bool,
    /// With --channel, stop after this many sets
    #[arg(long, value_name = "N")]
    pub limit: Option<usize>,
}

pub async fn run(cfg: &Config, action: SubtitlesAction) -> Result<()> {
    match action {
        SubtitlesAction::MoveInline(args) => move_inline::run(cfg, args).await,
        SubtitlesAction::Backfill(args) if args.channel => backfill_channel::run(cfg, args).await,
        SubtitlesAction::Backfill(args) => {
            if args.mkv || args.limit.is_some() {
                bail!("--mkv and --limit apply to --channel only");
            }
            if args.folders.is_empty() {
                bail!("subtitles backfill needs at least one folder, or --channel");
            }
            if args.dry_run {
                dry_run::run(cfg, &args.folders).await
            } else {
                backfill::run_folders(cfg, args).await
            }
        }
    }
}
