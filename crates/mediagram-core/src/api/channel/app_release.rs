//! The newest Android app release pinned in the library channel.

use std::path::Path;

use grammers_client::Client;
use grammers_client::message::Message;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use super::responses::Responses;
use super::{index, library};
use verified_file::write_verified;
use crate::api::account::revoked::{self, checked_for};
use crate::api::account::session;
use crate::api::{Core, CoreError};
use crate::transport::stream::part_document;

/// The same window the index lookup reads pins through.
const MAX_PINNED: usize = 100;

/// The largest APK this build will fetch.
const MAX_APK_BYTES: u64 = 256 * 1024 * 1024;

/// The largest dex-metadata file this build will fetch; a real one is tens of kilobytes.
const MAX_PROFILE_BYTES: u64 = 16 * 1024 * 1024;

/// One release, as the channel declares it, and where its APK is.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AppRelease {
    pub version_name: String,
    pub version_code: i64,
    pub bytes: u64,
    pub sha256: String,
    pub chat_id: i64,
    pub message_id: i64,
    /// The APK's dex metadata, a document of its own in the same channel:
    /// Android compiles the app at install when it arrives beside the APK.
    pub profile: Option<AppFile>,
}

/// A document in the release's channel, and how to check it once fetched.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AppFile {
    pub message_id: i64,
    pub bytes: u64,
    pub sha256: String,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The newest Android app release pinned in the chosen library's
    /// channel, or `None` when it holds none.
    pub async fn latest_app_release(
        &self,
        handle: String,
    ) -> Result<Option<AppRelease>, CoreError> {
        latest(self, &handle).await
    }

    /// Downloads `release`'s APK to `path`, verified against its caption;
    /// nothing is left at `path` unless it matched. A profile the caption
    /// names follows to `path` with the extension `dm`, verified the same
    /// way; failing to fetch it never fails the APK, which installs without.
    pub async fn download_app_release(
        &self,
        release: AppRelease,
        path: String,
    ) -> Result<(), CoreError> {
        download(self, &release, Path::new(&path)).await
    }
}

/// The newest release pinned in the library `handle` names, or `None`.
async fn latest(core: &Core, handle: &str) -> Result<Option<AppRelease>, CoreError> {
    let (entry, peer) = library::peer_of(core, handle)?;
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
            profile: release.profile.map(|p| AppFile { message_id: p.message, bytes: p.bytes, sha256: p.sha256 }),
        }),
    )
}

/// Downloads `release`'s APK to `path`. Nothing appears at `path` unless the
/// whole file arrived and matches the caption's size and sha256: it is
/// written beside it as `.part` first and renamed only once checked.
async fn download(core: &Core, release: &AppRelease, path: &Path) -> Result<(), CoreError> {
    if release.bytes > MAX_APK_BYTES || !mlib_spec::subtitle_bundle::valid_sha256(&release.sha256) {
        return Err(CoreError::Library(
            "that app release is not one this build will download".into(),
        ));
    }
    let (client, owner) = session::connection(core).await;
    let handles = library::read(&library::path(core))?;
    let channel = library::peer_for_chat(&handles, release.chat_id).ok_or_else(|| {
        CoreError::NotFound(
            "this device has no way to reach the channel the app release is in".into(),
        )
    })?;
    let apk = AppFile { message_id: release.message_id, bytes: release.bytes, sha256: release.sha256.clone() };
    fetch(core, &client, &owner, channel, &apk, path).await?;
    if let Some(profile) = release.profile.as_ref().filter(|p| p.bytes <= MAX_PROFILE_BYTES)
        && let Err(err) = fetch(core, &client, &owner, channel, profile, &path.with_extension("dm")).await
    {
        tracing::warn!(%err, "the app release goes in without its profile");
    }
    Ok(())
}

async fn fetch(
    core: &Core,
    client: &Client,
    owner: &SenderPoolFatHandle,
    channel: PeerRef,
    file: &AppFile,
    path: &Path,
) -> Result<(), CoreError> {
    let document = match part_document(client, channel, file.message_id).await {
        Ok(document) => document,
        Err(err) => return Err(revoked::failed(core, owner, "resolving the app release", err).await),
    };
    write_verified(core, owner, file.bytes, &file.sha256, path, client.iter_download(&document)).await
}

mod verified_file;

#[cfg(test)]
#[path = "app_release_tests.rs"]
mod tests;
