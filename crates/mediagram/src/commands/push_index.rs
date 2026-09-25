//! `mediagram push-index`: publish the local index and report its message.

use anyhow::Result;

use crate::config::Config;
use crate::telegram::index_publish::{self, Guard};

pub async fn run(cfg: &Config, force: bool, check: bool) -> Result<()> {
    if check {
        index_publish::check_only(cfg).await?;
        println!("safe to push: the channel's index holds nothing this one lacks");
        return Ok(());
    }
    let guard = if force { Guard::Skip } else { Guard::Check };
    let message_id = index_publish::publish_with(cfg, guard).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}
