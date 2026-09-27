//! `mediagram push-index`: publish the local index and report its message.

use anyhow::Result;

use crate::channel_index::{self, Mode};
use crate::config::Config;

/// Arguments for `mediagram push-index`.
#[derive(clap::Args, Debug, Clone)]
pub struct PushIndexArgs {
    /// Replace the channel index with this one as it is, without pulling in
    /// what the channel holds first
    #[arg(long)]
    pub force: bool,
}

pub async fn run(cfg: &Config, args: PushIndexArgs) -> Result<()> {
    let mode = if args.force {
        Mode::Force
    } else {
        Mode::AfterPull
    };
    let message_id = channel_index::publish_to_channel(cfg, mode).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}
