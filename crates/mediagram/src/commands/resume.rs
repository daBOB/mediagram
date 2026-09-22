//! `mediagram resume`: finish every set left pending by an interrupted `add`.

use anyhow::{Context, Result};

use super::push_index;
use crate::config::Config;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::upload::finish::finish_one;
use crate::upload::lock;
use crate::upload::transport::TelegramTransport;

/// Finishes every pending set, then pushes the index once at the end
/// (unless `no_push`) rather than after each individual set, since a bulk
/// resume session is typically followed by one manual push anyway.
pub async fn run(cfg: &Config, no_push: bool) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    // Same queue as everything else: this finishes sets, and a background
    // upload started by `add` is finishing one right now.
    let _lock = lock::acquire(&data_dir, || {
        println!("waiting for the upload already running");
    })
    .await?;
    let conn = db::open(&data_dir)?;
    let pending_sets = sets::list_pending(&conn)?;
    if pending_sets.is_empty() {
        println!("no pending sets");
        return Ok(());
    }

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let transport = TelegramTransport::new(&tg, cfg.max_attempts);

    let mut result: Result<bool> = Ok(false);
    for set in &pending_sets {
        result = finish_one(&conn, &transport, cfg.throttle_ms, set, &data_dir).await;
        if let Ok(true) = result {
            println!("set {} complete", set.set_id);
        }
        if result.is_err() {
            break;
        }
    }
    tg.shutdown().await;
    result.map(drop)?;

    if !no_push {
        push_index::run(cfg)
            .await
            .context("sets are complete but the index push failed; run `mediagram push-index`")?;
    }
    Ok(())
}
