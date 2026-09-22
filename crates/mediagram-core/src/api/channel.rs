//! The catalog the account already has: what it can choose from, and how one
//! choice becomes a library on disk.
//!
//! Nothing is hosted and nothing is pasted. The device is signed in, and the
//! uploader pins exactly one snapshot of `library.db` after every completed
//! set, so the catalog is already sitting in the channel — this module lists
//! the channels, downloads that snapshot, and installs it through the same
//! staging and atomic swap the published-package path uses.

use std::io::Write;

use grammers_client::Client;
use grammers_client::media::Document;
use grammers_client::message::Message;
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use super::channel_index::{self, NOT_AN_INDEX, UNREADABLE};
use super::library::{self, LibraryEntry};
use super::{Core, CoreError, LibraryChoice, catalog, refresh, session};
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

/// A hard ceiling on the snapshot. A real `library.db` for a few hundred sets
/// is a handful of megabytes; this only stops a wrong or hostile pinned
/// document filling a tablet's storage before anything looks at it.
const MAX_INDEX_BYTES: u64 = 256 * 1024 * 1024;

/// Every channel and group the signed-in account can see, newest activity
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
    while let Some(dialog) = dialogs
        .next()
        .await
        .map_err(|err| channel_index::channel_error(&err))?
    {
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

    let (document, caption) = newest_index(&client, peer).await?;
    let version = format!(
        "v-{}",
        channel_index::pushed_at(&caption, refresh::now_unix())
    );
    install(core, &client, &document, &version).await
}

/// The channel or group behind a dialog, with the authority needed to
/// address it later. A peer whose reference cannot be resolved is skipped
/// rather than listed: a title that cannot be opened is worse than absent.
async fn entry_of(peer: &grammers_client::peer::Peer) -> Option<LibraryEntry> {
    use grammers_client::peer::Peer;
    match peer {
        Peer::Channel(_) | Peer::Group(_) => {}
        Peer::User(_) => return None,
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
async fn newest_index(client: &Client, peer: PeerRef) -> Result<(Document, String), CoreError> {
    let mut found: Vec<Message> = Vec::new();

    let mut pinned = client
        .search_messages(peer)
        .filter(MessagesFilter::InputMessagesFilterPinned)
        .limit(MAX_PINNED);
    while let Some(message) = pinned
        .next()
        .await
        .map_err(|err| channel_index::channel_error(&err))?
    {
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

    let candidates: Vec<(&str, i64)> = found
        .iter()
        .map(|message| (message.text(), i64::from(message.id())))
        .collect();
    let chosen = channel_index::pick_index(&candidates, refresh::now_unix())?;
    let message = found.into_iter().nth(chosen).expect("chosen from this list");
    let caption = message.text().to_string();
    match message_document(&message) {
        Some((document, _)) => Ok((document, caption)),
        None => Err(CoreError::Library(NOT_AN_INDEX.into())),
    }
}

/// Stages the snapshot, proves it is a library, and only then lets it become
/// the current one.
async fn install(
    core: &Core,
    client: &Client,
    document: &Document,
    version: &str,
) -> Result<u64, CoreError> {
    let incoming = catalog::dir(core).join("incoming");
    let _ = std::fs::remove_dir_all(&incoming);
    std::fs::create_dir_all(&incoming)
        .map_err(CoreError::io("staging the refreshed catalog"))?;

    download(client, document, &incoming.join(mlib_spec::schema::INDEX_FILE)).await?;
    install_downloaded(core, &incoming, version)
}

/// Makes a downloaded snapshot current once it has proved to be a library.
///
/// Counting is also the check: a file that is not a catalog cannot be
/// counted, and this happens while it is still staged, so a channel with
/// something else pinned in it never replaces a library that works.
fn install_downloaded(
    core: &Core,
    incoming: &std::path::Path,
    version: &str,
) -> Result<u64, CoreError> {
    let sets = catalog::count_playable(incoming).map_err(|_| CoreError::Library(UNREADABLE.into()))?;
    refresh::install_staged(core, incoming, version)?;
    Ok(sets)
}

async fn download(
    client: &Client,
    document: &Document,
    path: &std::path::Path,
) -> Result<(), CoreError> {
    const WRITING: &str = "writing the downloaded index";
    let mut file = std::fs::File::create(path).map_err(CoreError::io(WRITING))?;
    let mut written: u64 = 0;
    let mut chunks = client.iter_download(document);
    while let Some(chunk) = chunks
        .next()
        .await
        .map_err(CoreError::network("the index download was interrupted"))?
    {
        written += chunk.len() as u64;
        if written > MAX_INDEX_BYTES {
            return Err(CoreError::Library(UNREADABLE.into()));
        }
        file.write_all(&chunk).map_err(CoreError::io(WRITING))?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The staged copy is proven to be a library before anything swaps, so a
    /// channel with the wrong document pinned leaves a working catalog alone.
    #[tokio::test]
    async fn a_staged_file_that_is_not_a_library_never_becomes_the_current_one() {
        let dir = tempfile::tempdir().unwrap();
        let core = Core::new(dir.path().display().to_string(), 1, "test-hash".into());
        let stage = |bytes: &[u8]| {
            let incoming = catalog::dir(&core).join("incoming");
            std::fs::create_dir_all(&incoming).unwrap();
            std::fs::write(incoming.join(mlib_spec::schema::INDEX_FILE), bytes).unwrap();
            incoming
        };

        let working = stage(b"");
        let conn = rusqlite::Connection::open(working.join(mlib_spec::schema::INDEX_FILE)).unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        drop(conn);
        install_downloaded(&core, &working, "v-1").unwrap();
        let before = std::fs::canonicalize(catalog::current_dir(&core)).unwrap();

        let refused = install_downloaded(&core, &stage(b"not a database"), "v-2").unwrap_err();

        assert_eq!(refused.to_string(), format!("library error: {UNREADABLE}"));
        assert_eq!(std::fs::canonicalize(catalog::current_dir(&core)).unwrap(), before);
    }
}
