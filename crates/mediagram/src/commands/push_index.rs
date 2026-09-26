//! `mediagram push-index`: publish the local index and report its message.

use anyhow::Result;

use crate::commands::pull_index;
use crate::config::Config;
use crate::telegram::index_publish::{self, Guard};

/// Arguments for `mediagram push-index`.
#[derive(clap::Args, Debug, Clone)]
pub struct PushIndexArgs {
    /// Replace the channel's index even if it holds sets this one lacks
    #[arg(long, conflicts_with_all = ["check", "merge"])]
    pub force: bool,
    /// Only check whether a push would remove sets from the channel; send nothing
    #[arg(long, conflicts_with = "merge")]
    pub check: bool,
    /// Pull the channel's index in first (see `pull-index`), so a push that
    /// would otherwise be refused for dropping sets can proceed
    #[arg(long)]
    pub merge: bool,
}

pub async fn run(cfg: &Config, args: PushIndexArgs) -> Result<()> {
    if args.check {
        index_publish::check_only(cfg).await?;
        println!("safe to push: the channel's index holds nothing this one lacks");
        return Ok(());
    }
    let message_id = if args.merge {
        pull_index::merge_and_publish(cfg).await?
    } else if args.force {
        index_publish::publish_with(cfg, Guard::Skip).await?
    } else {
        index_publish::publish_with(cfg, Guard::Check).await?
    };
    println!("pushed index as message {message_id}");
    Ok(())
}
