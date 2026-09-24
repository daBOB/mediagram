//! `mediagram finish-set`: finishing one set that `add` has already planned,
//! the half of an upload that is only bytes.
//!
//! A command of its own because `add` can either watch that happen or hand it
//! to a process that outlives the terminal, and both must do exactly the same
//! thing: [`finish_with`], then the index push a bulk command saves for last.

use std::path::Path;

use anyhow::{Context, Result};

use crate::config::Config;
use crate::telegram::client::Tg;
use crate::telegram::index_publish;
use crate::upload::finish_set::finish_with;

/// Uploads what is left of `set_id`, then deletes `delete` if the set
/// reached the channel whole, and pushes the index unless told not to.
pub async fn run(cfg: &Config, set_id: &str, delete: Option<&Path>, no_push: bool) -> Result<()> {
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let result = finish_with(cfg, &tg, set_id, delete).await;
    tg.shutdown().await;
    let complete = result?;
    if complete && !no_push {
        let message_id = index_publish::publish(cfg).await.with_context(|| {
            format!(
                "set {set_id} is complete but the index push failed; run `mediagram push-index`"
            )
        })?;
        println!("pushed index as message {message_id}");
    }
    Ok(())
}
