//! `mediagram sync-index`: the whole round trip with the channel in one go.
//!
//! Pull the channel's index in, describe every title, fetch its artwork, and
//! publish the result — what `pull-index`, `metadata`, `posters` and
//! `push-index --merge` do one at a time. The order matters: describing and
//! fetching after the pull covers the titles the other machine added, and
//! pushing last publishes those descriptions to every player.

use anyhow::{Context, Result};

use crate::commands::metadata::{self, MetadataArgs};
use crate::commands::{posters, pull_index};
use crate::config::Config;

/// Arguments for `mediagram sync-index`.
#[derive(clap::Args, Debug, Clone)]
pub struct SyncIndexArgs {
    #[command(flatten)]
    pub metadata: MetadataArgs,
}

pub async fn run(cfg: &Config, args: SyncIndexArgs) -> Result<()> {
    // Pulled first so the descriptions and artwork below cover the titles the
    // other machine added; the push at the end pulls again, which also merges
    // anything pushed while this ran.
    println!("1/4 pulling the channel's index");
    pull_index::pull(cfg, false)
        .await
        .context("pulling the channel's index")?;

    println!("2/4 describing titles");
    metadata::run(cfg, args.metadata).await?;

    // Artwork lands beside this machine's index for its own web player and is
    // not part of what gets pushed, so a failed fetch must not hold the push.
    println!("3/4 fetching artwork");
    if let Err(err) = posters::run(cfg, None).await {
        println!("artwork not fetched, pushing anyway: {err:#}");
    }

    println!("4/4 pushing the index");
    let message_id = pull_index::merge_and_publish(cfg).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}
