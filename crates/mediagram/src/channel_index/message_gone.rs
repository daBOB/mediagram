//! Telling an unpin with no pin left to clear from one that failed. Apart
//! from `telegram_remote`, which only makes calls, so the decision is tested
//! without a connection.

/// Errors that mean there is no pin left to clear: `MESSAGE_ID_INVALID` is a
/// message that no longer exists, `MESSAGE_NOT_MODIFIED` one already in the
/// state asked for.
const NOTHING_TO_UNPIN: &[&str] = &["MESSAGE_ID_INVALID", "MESSAGE_NOT_MODIFIED"];

/// Whether an error means the message is gone rather than that the call
/// failed. Matched on the error name, not the 400 class it arrives in: that
/// class also carries `PEER_ID_INVALID`, `CHANNEL_INVALID` and
/// `CHAT_WRITE_FORBIDDEN`, and reading those as "gone" would drop the id and
/// leave a pinned index only a `rescan` could find.
pub(super) fn message_is_gone(err: &anyhow::Error) -> bool {
    matches!(
        err.downcast_ref::<grammers_mtsender::InvocationError>(),
        Some(grammers_mtsender::InvocationError::Rpc(rpc))
            if rpc.code == 400 && NOTHING_TO_UNPIN.contains(&rpc.name.as_str())
    )
}

#[cfg(test)]
#[path = "message_gone_tests.rs"]
mod tests;
