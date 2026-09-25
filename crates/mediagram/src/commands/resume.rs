//! `mediagram resume`: finish every set left pending by an interrupted `add`.

use anyhow::{Context, Result};

use crate::config::Config;
use crate::index::{db, sets};
use crate::telegram::client::Tg;
use crate::telegram::index_publish;
use crate::upload::lock;
use crate::upload::resume;
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

    let summary = resume::pending(
        &conn,
        &transport,
        cfg.throttle_ms,
        &pending_sets,
        &data_dir,
        |id, result| match result {
            Ok(true) => println!("set {id} complete"),
            Ok(false) => {}
            Err(error) => println!("set {id}: {error:#}"),
        },
    )
    .await;
    tg.shutdown().await;

    if summary.completed > 0 && !no_push {
        let message_id = index_publish::publish(cfg)
            .await
            .context("completed sets could not be published; run `mediagram push-index`")?;
        println!("pushed index as message {message_id}");
    }
    if let Some(error) = summary.stopped {
        return Err(error).context("resuming pending sets stopped");
    }
    anyhow::ensure!(
        summary.blocked == 0,
        "{} set(s) blocked by unavailable or changed sources",
        summary.blocked
    );
    Ok(())
}
