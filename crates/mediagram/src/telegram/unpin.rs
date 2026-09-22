//! Clearing the pins of index snapshots a push has replaced, and proving
//! each one really is cleared.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::pins;
use crate::telegram::client::Tg;
use crate::telegram::retry::with_retry;

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
pub async fn unpin_previous(
    tg: &Tg,
    max_attempts: u32,
    conn: &Connection,
    channel: grammers_session::types::PeerRef,
) -> Result<()> {
    let mut unresolved: Vec<i32> = Vec::new();
    for old_id in pins::pending_unpins(conn) {
        if !clear_pin(tg, max_attempts, channel, old_id).await {
            unresolved.push(old_id);
        }
    }
    pins::record_unpin_outcome(conn, &unresolved)
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
