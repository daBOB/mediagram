//! `mediagram subtitles`: match media folders against the titles a channel
//! already carries, and report what a backfill could gain — before any of
//! it is extracted or sent.
//!
//! `backfill` only measures today (`--dry-run`, required); the sending mode,
//! `--accept-fallback` and `--redo` are a later phase, once the counts this
//! one prints have decided which titles are worth it.

mod dry_run;
mod dry_run_report;
mod match_source;

use std::path::PathBuf;

use anyhow::{Result, bail};
use clap::{Args, Subcommand};

use crate::config::Config;

#[derive(Subcommand)]
pub enum SubtitlesAction {
    /// Match media folders against subtitled titles the channel already
    /// carries, without extracting or sending anything
    Backfill(BackfillArgs),
}

#[derive(Args, Debug, Clone)]
pub struct BackfillArgs {
    /// Media folders to walk (never a drive root: a lecture tree of the same
    /// duration as an episode is exactly what the fallback can mismatch)
    pub folders: Vec<PathBuf>,
    /// Report matches only; nothing is extracted or sent. Required for now —
    /// sending lands once the counts this prints decide the scope
    #[arg(long)]
    pub dry_run: bool,
}

pub async fn run(cfg: &Config, action: SubtitlesAction) -> Result<()> {
    match action {
        SubtitlesAction::Backfill(args) => {
            if args.folders.is_empty() {
                bail!("subtitles backfill needs at least one folder");
            }
            if !args.dry_run {
                bail!(
                    "subtitles backfill only measures for now; pass --dry-run (sending is a later phase)"
                );
            }
            dry_run::run(cfg, &args.folders).await
        }
    }
}
