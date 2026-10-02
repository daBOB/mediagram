//! The newest Android app release pinned in the library channel.

use grammers_client::message::Message;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_tl_types::enums::MessagesFilter;

use super::responses::Responses;
use super::{index, library};
use crate::api::account::revoked::checked_for;
use crate::api::account::session;
use crate::api::{Core, CoreError};

/// The same window the index lookup reads pins through.
const MAX_PINNED: usize = 100;

/// One release, as the channel declares it, and where its APK is.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AppRelease {
    pub version_name: String,
    pub version_code: i64,
    pub bytes: u64,
    pub sha256: String,
    pub chat_id: i64,
    pub message_id: i64,
}

#[uniffi::export]
impl Core {
    /// The newest Android app release pinned in the chosen library's
    /// channel, or `None` when it holds none.
    pub async fn latest_app_release(
        &self,
        handle: String,
    ) -> Result<Option<AppRelease>, CoreError> {
        latest(self, &handle).await
    }
}

/// The newest release pinned in the library `handle` names, or `None`.
async fn latest(core: &Core, handle: &str) -> Result<Option<AppRelease>, CoreError> {
    let entry = library::lookup(core, handle)?;
    let peer = entry.peer().ok_or_else(|| {
        CoreError::NotFound("this device no longer has that library stored".into())
    })?;
    let (client, owner) = session::connection(core).await;
    let pinned = client
        .search_messages(peer)
        .filter(MessagesFilter::InputMessagesFilterPinned)
        .limit(MAX_PINNED);
    latest_with(core, &owner, entry.chat, pinned).await
}

/// Only the channel's own posts count, as for the index: a member's message
/// is not a release anyone published.
async fn latest_with(
    core: &Core,
    owner: &SenderPoolFatHandle,
    chat_id: i64,
    mut pinned: impl Responses<Item = Message>,
) -> Result<Option<AppRelease>, CoreError> {
    let mut posts: Vec<(String, i64)> = Vec::new();
    while let Some(message) = checked_for(
        core,
        owner,
        pinned.next_response().await,
        index::channel_error,
    )
    .await?
    {
        if message.post() {
            posts.push((message.text().to_string(), i64::from(message.id())));
        }
    }
    let candidates: Vec<(&str, i64)> = posts
        .iter()
        .map(|(text, id)| (text.as_str(), *id))
        .collect();
    Ok(
        mlib_spec::app_caption::newest(&candidates).map(|(position, release)| AppRelease {
            version_name: release.version,
            version_code: release.code,
            bytes: release.bytes,
            sha256: release.sha256,
            chat_id,
            message_id: candidates[position].1,
        }),
    )
}

#[cfg(test)]
#[path = "app_release_tests.rs"]
mod tests;
