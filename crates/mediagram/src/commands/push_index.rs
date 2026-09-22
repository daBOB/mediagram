//! `mediagram push-index`: snapshot `library.db`, upload it to the channel
//! as a pinned document, and unpin whatever index message it replaces.

use std::path::Path;

use anyhow::{Context, Result};
use grammers_client::message::InputMessage;
use rusqlite::Connection;
use serde_json::json;

use crate::config::Config;
use crate::index::{db, snapshot};
use crate::telegram::client::Tg;
use crate::telegram::retry::{with_flood_wait_only, with_retry};

/// The marker on the index snapshot's caption. `rescan` matches on the
/// version-less prefix so a future `v=3` snapshot is still recognised as one.
pub const INDEX_CAPTION_MARKER: &str = "#mlib-index v=2";
pub const INDEX_CAPTION_PREFIX: &str = "#mlib-index";
const META_INDEX_MESSAGE_ID: &str = "index_message_id";
/// Index messages still pinned that should not be: the one this push
/// replaces, plus any a previous push failed to unpin or a `rescan`
/// rediscovered. Stored comma-separated, newest first.
const META_STALE_INDEX_ID: &str = "stale_index_message_id";
const INDEX_MIME_TYPE: &str = "application/vnd.sqlite3";

/// Snapshots and pushes `library.db`, pins the new message, and unpins the
/// previously recorded index message (a failure to unpin is a warning, not
/// an error: the new pin still supersedes it for anyone reading the pins).
pub async fn run(cfg: &Config) -> Result<()> {
    let message_id = push_snapshot(cfg).await?;
    println!("pushed index as message {message_id}");
    Ok(())
}

async fn push_snapshot(cfg: &Config) -> Result<i32> {
    let data_dir = cfg.data_dir()?;
    let conn = db::open(&data_dir)?;
    snapshot::checkpoint(&conn).context("checkpointing before snapshot")?;
    // Per-process name so two overlapping pushes cannot rewrite each other's snapshot mid-upload.
    let temp_path = data_dir.join(format!("library.push.{}.db", std::process::id()));
    snapshot::snapshot_to(&conn, &temp_path).context("snapshotting the index")?;

    let result = push_via_telegram(cfg, &conn, &temp_path).await;

    if let Err(err) = tokio::fs::remove_file(&temp_path).await {
        tracing::warn!(path = %temp_path.display(), error = %err, "failed to remove push snapshot temp file");
    }

    result
}

async fn push_via_telegram(cfg: &Config, conn: &Connection, temp_path: &Path) -> Result<i32> {
    let sets_count: i64 = conn
        .query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
        .context("counting sets for index caption")?;
    let pushed_at = crate::clock::now_unix();
    let caption = format!(
        "{INDEX_CAPTION_MARKER}\n{}",
        json!({
            "pushed_at": pushed_at,
            "sets": sets_count,
            "schema": mlib_spec::schema::SCHEMA_VERSION,
        })
    );

    let tg = Tg::connect(cfg).await.context("connecting to Telegram")?;
    let result = send_and_pin(&tg, cfg.max_attempts, temp_path, &caption, conn).await;
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

    unpin_previous(tg, max_attempts, conn, channel).await?;

    db::set_meta(conn, META_INDEX_MESSAGE_ID, &new_id.to_string())
        .context("recording new index message id")?;

    Ok(new_id)
}

/// How many times one push will try to clear a single pin.
///
/// Two, because the failure this guards against cleared on the second
/// identical attempt when it was met live. A third would cost two more round
/// trips to tell us what the next push will find out anyway.
const UNPIN_ATTEMPTS: u32 = 2;

/// Unpins every index message that should no longer be pinned.
///
/// Best-effort about the channel: one that will not unpin is kept on the list
/// so the next push tries again, because the alternative is a channel with two
/// pinned indexes and a reader with no way to tell which is current. Not
/// about the list itself — failing to record it is an error, since that is
/// exactly how an id gets lost.
async fn unpin_previous(
    tg: &Tg,
    max_attempts: u32,
    conn: &Connection,
    channel: grammers_session::types::PeerRef,
) -> Result<()> {
    let mut unresolved: Vec<i32> = Vec::new();
    for old_id in pending_unpins(conn) {
        if !clear_pin(tg, max_attempts, channel, old_id).await {
            unresolved.push(old_id);
        }
    }
    record_unpin_outcome(conn, &unresolved)
}

/// Unpins one message, and reads the channel back to see whether it worked.
///
/// `unpin_message` answers `Ok` whenever the server raised no error, which is
/// not the same as the message being unpinned. `messages.updatePinnedMessage`
/// returns the `Updates` it produced and grammers maps them through
/// `.map(drop)`, so a request the server accepted and acted on not at all is
/// indistinguishable from one that worked.
///
/// That is not hypothetical. A push reported success and left its predecessor
/// pinned; because `Ok` dropped the id from the database, no later push could
/// find it again and only a `rescan` — which reads the pins off the channel —
/// could. So the pin flag is read back here rather than inferred, and an id
/// that is still pinned stays on the stale list.
///
/// Returns whether the message is known to be unpinned. "Not known" counts as
/// not unpinned: keeping an id that turned out to be fine costs one wasted
/// call on the next push, while dropping one that was not leaves a second
/// pinned index in the channel until someone notices.
async fn clear_pin(
    tg: &Tg,
    max_attempts: u32,
    channel: grammers_session::types::PeerRef,
    old_id: i32,
) -> bool {
    for attempt in 1..=UNPIN_ATTEMPTS {
        let client = tg.client.clone();
        let sent = with_retry(max_attempts, move || {
            let client = client.clone();
            async move { client.unpin_message(channel, old_id).await }
        })
        .await;
        if let Err(err) = sent {
            if message_is_gone(&err) {
                tracing::info!(
                    old_id,
                    "previous index message no longer exists; nothing to unpin"
                );
                return true;
            }
            tracing::warn!(old_id, error = %err, "failed to unpin index message; will retry on the next push");
            return false;
        }

        match still_pinned(tg, max_attempts, channel, old_id).await {
            Ok(false) => return true,
            Ok(true) => tracing::warn!(
                old_id,
                attempt,
                "the channel still reports this index message as pinned; unpinning again"
            ),
            // The unpin may well have worked; without a reading we cannot say,
            // and the next push is cheaper than a wrong answer here.
            Err(err) => {
                tracing::warn!(old_id, error = %err, "could not confirm the unpin; will retry on the next push");
                return false;
            }
        }
    }
    false
}

/// Whether the channel still reports `old_id` as pinned.
///
/// Asked of the message itself rather than of the pinned-message search,
/// which is an index and can lag behind what the message carries.
async fn still_pinned(
    tg: &Tg,
    max_attempts: u32,
    channel: grammers_session::types::PeerRef,
    old_id: i32,
) -> Result<bool> {
    let client = tg.client.clone();
    let found = with_retry(max_attempts, move || {
        let client = client.clone();
        async move { client.get_messages_by_id(channel, &[old_id]).await }
    })
    .await
    .context("reading back the pinned flag")?;
    // A deleted message reads as `None`, and has no pin left to clear.
    Ok(matches!(found.first(), Some(Some(message)) if message.pinned()))
}

/// Writes down which ids a push could not prove unpinned.
///
/// Separate from the clearing so the bookkeeping can be tested without a
/// channel: whether a stale id survives a push is the whole question, and the
/// defect this replaced was that a false `Ok` erased it.
///
/// The unresolved ids are written before the replaced id is forgotten, so a
/// write that fails part way can at worst leave an id listed twice — which
/// `pending_unpins` folds — and never lose one.
pub fn record_unpin_outcome(conn: &Connection, unresolved: &[i32]) -> Result<()> {
    if unresolved.is_empty() {
        db::delete_meta(conn, META_STALE_INDEX_ID)?;
    } else {
        db::set_meta(conn, META_STALE_INDEX_ID, &join_ids(unresolved))
            .context("recording the index pins still to clear")?;
    }
    db::delete_meta(conn, META_INDEX_MESSAGE_ID)
}

/// The index messages this push should unpin, newest first.
///
/// Both keys hold comma-separated ids: `index_message_id` the snapshot this
/// push replaces, `stale_index_message_id` anything a previous push could not
/// clear or a `rescan` rediscovered.
pub fn pending_unpins(conn: &Connection) -> Vec<i32> {
    let mut ids: Vec<i32> = Vec::new();
    for key in [META_INDEX_MESSAGE_ID, META_STALE_INDEX_ID] {
        let Ok(Some(raw)) = db::get_meta(conn, key) else {
            continue;
        };
        for piece in raw.split(',').map(str::trim).filter(|p| !p.is_empty()) {
            match piece.parse::<i32>() {
                Ok(id) if !ids.contains(&id) => ids.push(id),
                Ok(_) => {}
                Err(_) => {
                    tracing::warn!(key, value = %piece, "recorded index message id is not valid")
                }
            }
        }
    }
    ids
}

/// Records the index snapshots a `rescan` found in the channel.
///
/// The memory of which snapshot is current lives in `library.db`, and
/// `rescan` exists because that file gets lost — so without this the first
/// push after a rescan pins a new index and leaves the old one pinned beside
/// it. Found by a live run against a real channel, which is what those are for.
pub fn record_index_messages(conn: &Connection, ids: &[i32]) -> Result<()> {
    if ids.is_empty() {
        return Ok(());
    }
    let mut sorted: Vec<i32> = ids.to_vec();
    sorted.sort_unstable_by(|a, b| b.cmp(a));
    sorted.dedup();

    let (newest, older) = sorted.split_first().expect("checked non-empty");
    db::set_meta(conn, META_INDEX_MESSAGE_ID, &newest.to_string())
        .context("recording the current index message id")?;
    if older.is_empty() {
        let _ = db::delete_meta(conn, META_STALE_INDEX_ID);
    } else {
        db::set_meta(conn, META_STALE_INDEX_ID, &join_ids(older))
            .context("recording stale index message ids")?;
    }
    Ok(())
}

fn join_ids(ids: &[i32]) -> String {
    ids.iter().map(i32::to_string).collect::<Vec<_>>().join(",")
}

/// Errors that mean there is no pin left to clear, so the id can be dropped.
///
/// `MESSAGE_ID_INVALID` is a message that no longer exists.
/// `MESSAGE_NOT_MODIFIED` is the server saying it was already in the state
/// asked for, which for an unpin is the outcome wanted.
const NOTHING_TO_UNPIN: &[&str] = &["MESSAGE_ID_INVALID", "MESSAGE_NOT_MODIFIED"];

/// Whether an error means the message is gone rather than that the call
/// failed.
///
/// Matched on the error name, not on the 400 class it arrives in. That class
/// also carries `PEER_ID_INVALID`, `CHANNEL_INVALID` and `CHAT_WRITE_FORBIDDEN`,
/// none of which say anything about the message — and treating them as "gone"
/// would drop the id, leaving a pinned index that only a `rescan` could find.
pub fn message_is_gone(err: &anyhow::Error) -> bool {
    matches!(
        err.downcast_ref::<grammers_mtsender::InvocationError>(),
        Some(grammers_mtsender::InvocationError::Rpc(rpc))
            if rpc.code == 400 && NOTHING_TO_UNPIN.contains(&rpc.name.as_str())
    )
}
