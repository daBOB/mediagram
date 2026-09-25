//! `mediagram push-index`: publish the local index and report its message.

use anyhow::Result;

use crate::config::Config;
use crate::telegram::index_publish::{self, Guard};

/// Arguments for `mediagram push-index`.
#[derive(clap::Args, Debug, Clone)]
pub struct PushIndexArgs {
    /// Replace the channel's index even if it holds sets this one lacks
    #[arg(long, conflicts_with = "check")]
    pub force: bool,
    /// Only check whether a push would remove sets from the channel; send nothing
    #[arg(long)]
    pub check: bool,
}

pub async fn run(cfg: &Config, args: PushIndexArgs) -> Result<()> {
    if args.check {
        index_publish::check_only(cfg).await?;
        println!("safe to push: the channel's index holds nothing this one lacks");
        return Ok(());
    }
    let guard = if args.force {
        Guard::Skip
    } else {
        Guard::Check
    };
    let message_id = index_publish::publish_with(cfg, guard).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}
