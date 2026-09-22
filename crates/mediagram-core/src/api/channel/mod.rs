//! The catalog the account already has: what it can choose from, and how one
//! choice becomes a library on disk.
//!
//! Nothing is hosted and nothing is pasted. The device is signed in, and the
//! uploader pins exactly one snapshot of `library.db` after every completed
//! set, so the catalog is already sitting in the channel — this module lists
//! the channels, downloads that snapshot, and installs it through the same
//! staging and atomic swap the published-package path uses.

pub(super) mod index;
pub(super) mod library;
mod install;

use grammers_client::Client;
use grammers_client::media::Document;
use grammers_client::message::Message;
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use index::NOT_AN_INDEX;
use install::install;
use library::LibraryEntry;
use crate::api::account::revoked::checked;
use crate::api::account::session;
use crate::api::{Core, CoreError, LibraryChoice, refresh};
use crate::document::message_document;

/// How far down the dialog list to look. Telegram orders it the way the
/// account's own Telegram app does — pinned first, then most recent — so the
/// library of a device someone is setting up is near the top, and an account
/// in more conversations than this has a scrolling problem, not a missing
/// library.
const MAX_DIALOGS: usize = 500;

/// How many pinned messages to read before deciding. A channel with more
/// pins than this is not one `push-index` maintains.
const MAX_PINNED: usize = 100;

/// How far back to look for index snapshots the pins do not name. A channel
/// accumulates one per publish, so this is generous for finding the newest
/// few without paging years of history onto a phone.
const MAX_INDEX_CANDIDATES: usize = 50;


/// Every broadcast channel the signed-in account can see, newest activity
/// first, each under a handle that means nothing outside this crate.
///
/// One-to-one conversations are left out: a library lives in a channel the
/// uploader posts to, never in a conversation with a person.
pub(super) async fn list_libraries(core: &Core) -> Result<Vec<LibraryChoice>, CoreError> {
    let client = session::client(core).await;
    let file = library::path(core);
    let mut handles = library::read(&file)?;
    let mut choices = Vec::new();

    let mut dialogs = client.iter_dialogs().limit(MAX_DIALOGS);
    while let Some(dialog) = checked(core, dialogs.next().await, index::channel_error).await? {
        let Some(entry) = entry_of(dialog.peer()).await else {
            continue;
        };
        let title = entry.title.clone();
        let handle = library::handle_for(&mut handles, entry);
        choices.push(LibraryChoice { handle, title });
    }

    library::write(&file, &handles)?;
    Ok(choices)
}

/// Re-reads the newest index the chosen channel holds and installs it as
/// the current catalog, returning how many sets it holds.
pub(super) async fn refresh_library(core: &Core, handle: String) -> Result<u64, CoreError> {
    let entry = library::lookup(core, &handle)?;
    let peer = entry
        .peer()
        .ok_or_else(|| CoreError::NotFound("this device no longer has that library stored".into()))?;
    let client = session::client(core).await;

    let (document, caption) = newest_index(core, &client, peer).await?;
    let version = format!(
        "v-{}",
        index::pushed_at(&caption, refresh::now_unix())
    );
    install(core, &client, &document, &version).await
}

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

/// The newest index snapshot the channel holds, with its caption.
///
/// Two searches, not one. The pinned list is where a healthy channel keeps
/// its index and is the cheapest thing to ask for; a text search for the
/// marker finds the snapshots an interrupted publish or a second machine
/// publishing to the same channel left unpinned. Reading only the pins is
/// what lets a reader sit on a library older than the one the channel
/// actually holds, with nothing on screen to say so.
///
/// The text search is allowed to fail: a channel where it is unavailable
/// still has its pins, and refusing the whole refresh over the cheaper half
/// being unavailable would trade a stale library for no library.
async fn newest_index(
    core: &Core,
    client: &Client,
    peer: PeerRef,
) -> Result<(Document, String), CoreError> {
    let mut found: Vec<Message> = Vec::new();

    let mut pinned = client
        .search_messages(peer)
        .filter(MessagesFilter::InputMessagesFilterPinned)
        .limit(MAX_PINNED);
    while let Some(message) = checked(core, pinned.next().await, index::channel_error).await? {
        found.push(message);
    }

    let mut marked = client
        .search_messages(peer)
        .query(mlib_spec::index_caption::PREFIX)
        .limit(MAX_INDEX_CANDIDATES);
    while let Ok(Some(message)) = marked.next().await {
        if !found.iter().any(|seen| seen.id() == message.id()) {
            found.push(message);
        }
    }

    // Only the channel's own posts: a message a member slipped in — through
    // a discussion group, say, or into a library chosen before groups
    // stopped being offered — is not a snapshot anyone published.
    found.retain(Message::post);

    let candidates: Vec<(&str, i64)> = found
        .iter()
        .map(|message| (message.text(), i64::from(message.id())))
        .collect();
    let chosen = index::pick_index(&candidates, refresh::now_unix())?;
    let message = found.into_iter().nth(chosen).expect("chosen from this list");
    let caption = message.text().to_string();
    match message_document(&message) {
        Some((document, _)) => Ok((document, caption)),
        None => Err(CoreError::Library(NOT_AN_INDEX.into())),
    }
}
