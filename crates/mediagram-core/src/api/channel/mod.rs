//! The catalog the account already has: what it can choose from, and how one
//! choice becomes a library on disk.
//!
//! Nothing is hosted and nothing is pasted. The device is signed in, and the
//! uploader pins exactly one snapshot of `library.db` after every completed
//! set, so the catalog is already sitting in the channel — this module lists
//! the channels, downloads that snapshot, and installs it through the same
//! staging and atomic swap the published-package path uses.

pub(super) mod index;
mod install;
pub(super) mod library;
mod responses;
mod search;

use grammers_client::peer::Dialog;
use grammers_mtsender::SenderPoolFatHandle;
use responses::Responses;

use crate::api::account::revoked::checked_for;
use crate::api::account::session;
use crate::api::{Core, CoreError, LibraryChoice};
use crate::versions::now_unix;
use install::install;
use library::LibraryEntry;
use search::newest_index;

/// How far down the dialog list to look. Telegram orders it the way the
/// account's own Telegram app does — pinned first, then most recent — so the
/// library of a device someone is setting up is near the top, and an account
/// in more conversations than this has a scrolling problem, not a missing
/// library.
const MAX_DIALOGS: usize = 500;

/// Every broadcast channel the signed-in account can see, newest activity
/// first, each under a handle that means nothing outside this crate.
///
/// One-to-one conversations are left out: a library lives in a channel the
/// uploader posts to, never in a conversation with a person.
pub(super) async fn list_libraries(core: &Core) -> Result<Vec<LibraryChoice>, CoreError> {
    let (client, owner) = session::connection(core).await;
    list_libraries_with(core, &owner, client.iter_dialogs().limit(MAX_DIALOGS)).await
}

async fn list_libraries_with(
    core: &Core,
    owner: &SenderPoolFatHandle,
    mut dialogs: impl Responses<Item = Dialog>,
) -> Result<Vec<LibraryChoice>, CoreError> {
    let file = library::path(core);
    let mut handles = library::read(&file)?;
    let mut choices = Vec::new();

    while let Some(dialog) = checked_for(
        core,
        owner,
        dialogs.next_response().await,
        index::channel_error,
    )
    .await?
    {
        let Some(entry) = entry_of(dialog.peer()).await else {
            continue;
        };
        let title = entry.title.clone();
        let handle = library::register_or_refresh_library(&mut handles, entry);
        choices.push(LibraryChoice { handle, title });
    }

    library::write(&file, &handles)?;
    Ok(choices)
}

/// Re-reads the newest index the chosen channel holds and installs it as
/// the current catalog, returning how many sets it holds.
pub(super) async fn refresh_library(core: &Core, handle: String) -> Result<u64, CoreError> {
    let entry = library::lookup(core, &handle)?;
    let peer = entry.peer().ok_or_else(|| {
        CoreError::NotFound("this device no longer has that library stored".into())
    })?;
    let (client, owner) = session::connection(core).await;

    let (document, caption) = newest_index(core, &client, &owner, peer).await?;
    let version = format!("v-{}", index::pushed_at(&caption, now_unix()));
    install(core, &client, &owner, &document, &version).await
}

#[cfg(test)]
mod tests;

/// The broadcast channel behind a dialog, with the authority needed to
/// address it later. A peer whose reference cannot be resolved is skipped
/// rather than listed: a title that cannot be opened is worse than absent.
async fn entry_of(peer: &grammers_client::peer::Peer) -> Option<LibraryEntry> {
    use grammers_client::peer::Peer;
    match peer {
        Peer::Channel(_) => {}
        // Any member of a group can post in it, so an index found there says
        // nothing about who published it. Only a broadcast channel, where
        // only its admins post, is offered as a library.
        Peer::Group(_) | Peer::User(_) => return None,
    }
    let reference = peer.to_ref().await.ok().flatten()?;
    Some(LibraryEntry {
        chat: reference.id.bot_api_dialog_id()?,
        auth: reference.auth.hash(),
        title: peer.name().unwrap_or("Untitled channel").to_string(),
    })
}
