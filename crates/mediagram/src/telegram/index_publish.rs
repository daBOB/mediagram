//! Publishing the local index as a pinned Telegram document.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result};
use grammers_client::message::InputMessage;
use rusqlite::Connection;

use crate::config::Config;
use crate::index::{db, pins, sets, snapshot};
use crate::telegram::client::Tg;
use crate::telegram::index_guard;
use crate::telegram::retry::{with_flood_wait_only, with_retry};

const INDEX_MIME_TYPE: &str = "application/vnd.sqlite3";

/// Whether a push first checks that the channel holds nothing this index
/// lacks (see `index_guard`). Only `push-index --force` skips it;
/// `push-index --merge` checks, excepting the sets its merge proved removed.
#[derive(Clone, PartialEq, Eq)]
pub enum Guard {
    Check,
    CheckExcept(HashSet<String>),
    Skip,
}

/// Snapshots and publishes the index, returning its message ID, after
/// checking the channel holds no set this index lacks. A failed unpin is a
/// warning: the new pin supersedes the previous index.
pub async fn publish(cfg: &Config) -> Result<i32> {
    publish_with(cfg, Guard::Check).await
}

/// Runs only the check a push would: whether the channel's index holds sets
/// this one lacks. Sends nothing.
pub async fn check_only(cfg: &Config) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    let conn = db::open(&data_dir)?;
    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let result =
        index_guard::refuse_if_channel_has_more(&tg, &conn, &data_dir, &HashSet::new()).await;
    tg.shutdown().await;
    result
}

/// [`publish`], choosing whether to check the channel first.
pub async fn publish_with(cfg: &Config, guard: Guard) -> Result<i32> {
    let data_dir = cfg.data_dir()?;
    let conn = db::open(&data_dir)?;
    snapshot::checkpoint(&conn).context("checkpointing before snapshot")?;
    // Per-process name so two overlapping pushes cannot rewrite each other's snapshot mid-upload.
    let temp_path = data_dir.join(format!("library.push.{}.db", std::process::id()));
    snapshot::snapshot_to(&conn, &temp_path).context("snapshotting the index")?;

    let result = push_via_telegram(cfg, &conn, &temp_path, &data_dir, guard).await;

    if let Err(err) = tokio::fs::remove_file(&temp_path).await {
        tracing::warn!(path = %temp_path.display(), error = %err, "failed to remove push snapshot temp file");
    }

    result
}

async fn push_via_telegram(
    cfg: &Config,
    conn: &Connection,
    temp_path: &Path,
    scratch: &Path,
    guard: Guard,
) -> Result<i32> {
    let sets_count = i64::try_from(sets::count(conn)?)?;
    let pushed_at = crate::clock::now_unix();
    let caption = mlib_spec::index_caption::render(pushed_at, sets_count);

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let checked = match guard {
        Guard::Check => {
            index_guard::refuse_if_channel_has_more(&tg, conn, scratch, &HashSet::new()).await
        }
        Guard::CheckExcept(removed) => {
            index_guard::refuse_if_channel_has_more(&tg, conn, scratch, &removed).await
        }
        Guard::Skip => Ok(()),
    };
    let result = match checked {
        Ok(()) => send_and_pin(&tg, cfg.max_attempts, temp_path, &caption, conn).await,
        Err(err) => Err(err),
    };
    tg.shutdown().await;
    result
}

async fn send_and_pin(
    tg: &Tg,
    max_attempts: u32,
    temp_path: &Path,
    caption: &str,
    conn: &Connection,
) -> Result<i32> {
    let size = tokio::fs::metadata(temp_path)
        .await
        .with_context(|| format!("stat {}", temp_path.display()))?
        .len() as usize;
    let mut file = tokio::fs::File::open(temp_path)
        .await
        .with_context(|| format!("opening {}", temp_path.display()))?;
    // `upload_stream` with an explicit name lets the uploaded document be
    // called `library.db` regardless of the temp file's on-disk name.
    let uploaded = tg
        .client
        .upload_stream(&mut file, size, mlib_spec::schema::INDEX_FILE.to_string())
        .await
        .with_context(|| format!("uploading {}", temp_path.display()))?;

    let channel = tg.channel;
    let client = tg.client.clone();
    let caption_owned = caption.to_string();
    // Not idempotent: a lost response after a committed send would duplicate
    // the index message, so only FLOOD_WAIT is retried here.
    let message = with_flood_wait_only(max_attempts, move || {
        let client = client.clone();
        let uploaded = uploaded.clone();
        let caption_owned = caption_owned.clone();
        async move {
            client
                .send_message(
                    channel,
                    InputMessage::new()
                        .mime_type(INDEX_MIME_TYPE)
                        .text(caption_owned)
                        .document(uploaded),
                )
                .await
        }
    })
    .await
    .context("sending index document")?;

    let new_id = message.id();

    let client = tg.client.clone();
    with_retry(max_attempts, move || {
        let client = client.clone();
        async move { client.pin_message(channel, new_id).await }
    })
    .await
    .context("pinning index message")?;

    crate::telegram::unpin::unpin_previous(tg, max_attempts, conn, channel).await?;

    pins::record_current(conn, new_id)?;

    Ok(new_id)
}
