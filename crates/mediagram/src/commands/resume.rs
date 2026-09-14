//! `mediagram resume`: finish every set left pending by an interrupted `add`.

use std::path::PathBuf;

use anyhow::{Context, Result, bail};

use crate::config::Config;
use crate::index::{db, parts, sets};
use crate::telegram::client::Tg;
use crate::upload::pipeline::run_set;
use crate::upload::transport::TelegramTransport;

/// `no_push` is accepted for CLI symmetry with `add --no-push`; index push
/// is a later command, so it is currently a no-op either way.
pub async fn run(cfg: &Config, _no_push: bool) -> Result<()> {
    let conn = db::open(&cfg.data_dir()?)?;
    let pending_sets = sets::list_pending(&conn)?;
    if pending_sets.is_empty() {
        println!("no pending sets");
        return Ok(());
    }

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let transport = TelegramTransport::new(&tg, cfg.max_attempts);

    let mut result = Ok(());
    for set in &pending_sets {
        result = resume_one(&conn, &transport, cfg.throttle_ms, set).await;
        if result.is_err() {
            break;
        }
    }
    tg.shutdown().await;
    result
}

async fn resume_one(
    conn: &rusqlite::Connection,
    transport: &TelegramTransport,
    throttle_ms: u64,
    set: &sets::SetRow,
) -> Result<()> {
    let source_key = format!("source:{}", set.set_id);
    let source_path = db::get_meta(conn, &source_key)?.ok_or_else(|| {
        anyhow::anyhow!(
            "set {} has no recorded source path; cannot resume",
            set.set_id
        )
    })?;
    let source_path = PathBuf::from(source_path);
    if !source_path.exists() {
        bail!(
            "source file for set {} is missing: {}",
            set.set_id,
            source_path.display()
        );
    }

    run_set(conn, transport, throttle_ms, set, &source_path)
        .await
        .with_context(|| format!("resuming set {}", set.set_id))?;

    if parts::pending_parts(conn, &set.set_id)?.is_empty() {
        db::delete_meta(conn, &source_key)?;
        println!("set {} complete", set.set_id);
    }
    Ok(())
}
