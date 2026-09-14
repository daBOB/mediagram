//! Hidden smoke test: upload one file with a smoke caption, print message id, delete it.
//!
//! Manual gate for two assumptions: that Telegram enforces the 4 GB
//! per-document cap on this (Premium) account, and that FLOOD_WAIT surfaces
//! the way `telegram::retry` expects it to.

use std::path::Path;

use anyhow::{Context, Result};
use grammers_client::message::InputMessage;

use crate::config::Config;
use crate::telegram::client::Tg;
use crate::telegram::retry::with_retry;

const SMOKE_CAPTION: &str = "#mlib smoke";

pub async fn run(cfg: &Config, file: &Path) -> Result<()> {
    let tg = Tg::connect(cfg).await?;
    let channel = tg.channel;

    let uploaded = tg
        .client
        .upload_file(file)
        .await
        .with_context(|| format!("uploading {}", file.display()))?;

    let message = with_retry(cfg.max_attempts, || {
        let uploaded = uploaded.clone();
        let client = tg.client.clone();
        async move {
            client
                .send_message(
                    channel,
                    InputMessage::new().text(SMOKE_CAPTION).document(uploaded),
                )
                .await
        }
    })
    .await
    .context("sending smoke document")?;

    let msg_id = message.id();
    println!("Uploaded message id: {msg_id}");

    with_retry(cfg.max_attempts, || {
        let client = tg.client.clone();
        async move { client.delete_messages(channel, &[msg_id]).await.map(drop) }
    })
    .await
    .context("deleting smoke message")?;

    println!("Smoke message deleted.");
    tg.shutdown().await;
    Ok(())
}
