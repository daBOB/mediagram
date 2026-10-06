//! Clearing the pins of index snapshots a publish has replaced, and proving
//! each one really is cleared.

use anyhow::Result;
use rusqlite::Connection;

use super::remote::{ChannelRemote, Unpin};
use crate::index::pins;

/// How many times one publish will try to clear a single pin.
///
/// Two, because the failure this guards against cleared on the second
/// identical attempt when it was met live. A third would cost two more round
/// trips to tell us what the next publish will find out anyway.
const UNPIN_ATTEMPTS: u32 = 2;

/// Unpins every index message that should no longer be pinned.
///
/// Best-effort about the channel: one that will not unpin is kept on the list
/// so the next publish tries again. Not about the list itself — failing to
/// record it is an error, since that is exactly how an id gets lost.
pub(super) async fn unpin_previous(remote: &impl ChannelRemote, conn: &Connection) -> Result<()> {
    let mut unresolved: Vec<i32> = Vec::new();
    for old_id in pins::pending_unpins(conn)? {
        if !clear_pin(remote, old_id).await {
            unresolved.push(old_id);
        }
    }
    pins::record_unpin_outcome(conn, &unresolved)
}

/// Unpins one message, and reads it back to see whether that worked.
///
/// Telegram answers an unpin with success whenever it raised no error, which
/// is not the same as the message being unpinned: a publish once reported
/// success and left its predecessor pinned, and because that success dropped
/// the id, only a `rescan` could find it again. So the pin flag is read back
/// rather than inferred.
///
/// Returns whether the message is known to be unpinned. "Not known" counts as
/// not unpinned: keeping an id that turned out fine costs one wasted call on
/// the next publish, while dropping one that was not leaves a second pinned
/// index in the channel until someone notices.
async fn clear_pin(remote: &impl ChannelRemote, old_id: i32) -> bool {
    for attempt in 1..=UNPIN_ATTEMPTS {
        match remote.unpin(old_id).await {
            Ok(Unpin::Gone) => return true,
            Ok(Unpin::Sent) => {}
            Err(err) => {
                tracing::warn!(old_id, error = %format_args!("{err:#}"), "failed to unpin index message; will retry on the next publish");
                return false;
            }
        }
        match remote.is_pinned(old_id).await {
            Ok(false) => return true,
            Ok(true) => tracing::warn!(
                old_id,
                attempt,
                "the channel still reports this index message as pinned; unpinning again"
            ),
            // The unpin may well have worked; without a reading we cannot say,
            // and the next publish is cheaper than a wrong answer here.
            Err(err) => {
                tracing::warn!(old_id, error = %format_args!("{err:#}"), "could not confirm the unpin; will retry on the next publish");
                return false;
            }
        }
    }
    false
}

#[cfg(test)]
#[path = "unpin_tests.rs"]
mod tests;
