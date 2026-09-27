//! Finding the newest index a channel holds, and saying plainly when it
//! holds none.
//!
//! `mediagram push-index` pins each snapshot of `library.db` and unpins the
//! one it replaces, so a healthy channel holds exactly one pinned. But a pin
//! is a separate Telegram operation: a publish whose unpin failed, a second
//! machine publishing to the same channel, or a snapshot pinned by hand all
//! leave the pin pointing somewhere other than the newest snapshot. The
//! timestamp in the caption is a fact about the snapshot itself, so that is
//! what decides, and the pin is only one of the places worth looking.

use grammers_mtsender::InvocationError;

use mlib_spec::index_caption;

use crate::api::CoreError;

pub(in crate::api) const NOTHING_PINNED: &str = concat!(
    "That channel has nothing pinned. The uploader pins its index after ",
    "every completed set — run `mediagram push-index` on the machine that ",
    "uploads to it, then try again.",
);

pub(in crate::api) const NOT_AN_INDEX: &str = concat!(
    "What is pinned in that channel is not a library index. Choose the ",
    "channel `mediagram` uploads to, or run `mediagram push-index` there.",
);

pub(in crate::api) const UNREADABLE: &str = concat!(
    "The index pinned in that channel could not be read as a library. It ",
    "may still be uploading; a fresh `mediagram push-index` replaces it.",
);

pub(in crate::api) const NO_LONGER_VISIBLE: &str = concat!(
    "This account can no longer open that channel. Choose another library ",
    "from the list.",
);

/// Which candidate is the channel's index, or why none of them is.
///
/// The choice itself is the spec's (`index_caption::newest`, shared with the
/// uploader and mirrored by the web player); what this adds is saying which
/// of the two ways of having none a person is in.
pub(in crate::api) fn pick_index(candidates: &[(&str, i64)], now: i64) -> Result<usize, CoreError> {
    match index_caption::newest(candidates, now) {
        Some(idx) => Ok(idx),
        None if candidates.is_empty() => Err(CoreError::Library(NOTHING_PINNED.into())),
        None => Err(CoreError::Library(NOT_AN_INDEX.into())),
    }
}

/// When the snapshot behind `caption` was pushed, for naming the catalogue
/// version it installs. A caption this build cannot read is not a reason to
/// refuse a readable index, so the fallback is simply "now".
pub(in crate::api) fn pushed_at(caption: &str, now: i64) -> i64 {
    index_caption::believed_pushed_at(caption, now).unwrap_or(now)
}

/// Telegram refusing a request about a channel, told apart from the line
/// going down.
///
/// A client error means this account cannot address that channel any more —
/// removed from it, or the channel deleted — and no amount of retrying
/// changes that, so it must not be reported as a network blip the person is
/// invited to wait out.
pub(in crate::api) fn channel_error(err: &InvocationError) -> CoreError {
    match err {
        InvocationError::Rpc(rpc) if rpc.code == 400 || rpc.code == 403 => {
            CoreError::NotAuthorized(NO_LONGER_VISIBLE.into())
        }
        _ => CoreError::Network("the channel could not be read just now".into()),
    }
}

#[cfg(test)]
#[path = "index_tests.rs"]
mod tests;
