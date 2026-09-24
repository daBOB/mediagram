//! `mediagram push-index`: publish the local index and report its message.

use anyhow::Result;

use crate::config::Config;
use crate::telegram::index_publish;

pub async fn run(cfg: &Config) -> Result<()> {
    let message_id = index_publish::publish(cfg).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}
