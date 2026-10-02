//! The newest Android app release pinned in the library channel.

use std::io::Read;
use std::path::Path;

use grammers_client::message::Message;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_tl_types::enums::MessagesFilter;
use sha2::{Digest, Sha256};

use super::download::download_with;
use super::responses::Responses;
use super::{index, library};
use crate::api::account::revoked::checked_for;
use crate::api::account::session;
use crate::api::{Core, CoreError};
use crate::transport::stream::part_document;

/// The same window the index lookup reads pins through.
const MAX_PINNED: usize = 100;

/// The largest APK this build will fetch.
const MAX_APK_BYTES: u64 = 256 * 1024 * 1024;

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
    /// nothing is left at `path` unless it matched.
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
    let document = part_document(&client, channel, release.message_id)
        .await
        .map_err(CoreError::network("resolving the app release"))?;
    write_verified(core, &owner, release, path, client.iter_download(&document)).await
}

async fn write_verified(
    core: &Core,
    owner: &SenderPoolFatHandle,
    release: &AppRelease,
    path: &Path,
    chunks: impl Responses<Item = Vec<u8>>,
) -> Result<(), CoreError> {
    let partial = path.with_extension("part");
    let result = download_with(
        core,
        owner,
        &partial,
        release.bytes,
        "downloading the app release",
        || CoreError::Io("the app release is larger than its caption says".into()),
        chunks,
    )
    .await
    .and_then(|()| verify(&partial, release));
    match result {
        Ok(()) => std::fs::rename(&partial, path)
            .map_err(CoreError::io("keeping the downloaded app release")),
        Err(err) => {
            let _ = std::fs::remove_file(&partial);
            Err(err)
        }
    }
}

/// Size and sha256 against the caption, then durable — both before the
/// rename that makes the file look ready to install.
fn verify(path: &Path, release: &AppRelease) -> Result<(), CoreError> {
    const VERIFYING: &str = "verifying the downloaded app release";
    let mut file = std::fs::File::open(path).map_err(CoreError::io(VERIFYING))?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    let mut copied: u64 = 0;
    loop {
        let n = file.read(&mut buf).map_err(CoreError::io(VERIFYING))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        copied += n as u64;
    }
    if copied != release.bytes || hex::encode(hasher.finalize()) != release.sha256 {
        return Err(CoreError::Io(
            "the downloaded app release does not match its caption".into(),
        ));
    }
    file.sync_all().map_err(CoreError::io(VERIFYING))
}

#[cfg(test)]
#[path = "app_release_tests.rs"]
mod tests;
