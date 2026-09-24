//! Looking for the newest channel index among pinned and marked messages.

use super::responses::Responses;
use grammers_client::Client;
use grammers_client::media::Document;
use grammers_client::message::Message;
use grammers_mtsender::SenderPoolFatHandle;
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use super::index::{self, NOT_AN_INDEX};
use crate::api::account::revoked::{self, checked_for};
use crate::api::{Core, CoreError};
use crate::transport::document::message_document;
use crate::versions::now_unix;

/// How many pinned messages to read before deciding. A channel with more
/// pins than this is not one `push-index` maintains.
const MAX_PINNED: usize = 100;

/// How far back to look for index snapshots the pins do not name. A channel
/// accumulates one per publish, so this is generous for finding the newest
/// few without paging years of history onto a phone.
const MAX_INDEX_CANDIDATES: usize = 50;

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
pub(super) async fn newest_index(
    core: &Core,
    client: &Client,
    owner: &SenderPoolFatHandle,
    peer: PeerRef,
) -> Result<(Document, String), CoreError> {
    let pinned = client
        .search_messages(peer)
        .filter(MessagesFilter::InputMessagesFilterPinned)
        .limit(MAX_PINNED);
    let marked = client
        .search_messages(peer)
        .query(mlib_spec::index_caption::PREFIX)
        .limit(MAX_INDEX_CANDIDATES);
    newest_index_with(core, owner, pinned, marked).await
}

async fn newest_index_with(
    core: &Core,
    owner: &SenderPoolFatHandle,
    mut pinned: impl Responses<Item = Message>,
    mut marked: impl Responses<Item = Message>,
) -> Result<(Document, String), CoreError> {
    let mut found: Vec<Message> = Vec::new();
    while let Some(message) = checked_for(
        core,
        owner,
        pinned.next_response().await,
        index::channel_error,
    )
    .await?
    {
        found.push(message);
    }
    loop {
        let message = match marked.next_response().await {
            Ok(Some(message)) => message,
            Err(err) if revoked::is_revoked(&err) => {
                return Err(revoked::unless_revoked_for(
                    core,
                    owner,
                    &err,
                    index::channel_error(&err),
                )
                .await);
            }
            _ => break,
        };
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
    let chosen = index::pick_index(&candidates, now_unix())?;
    let message = found
        .into_iter()
        .nth(chosen)
        .expect("chosen from this list");
    let caption = message.text().to_string();
    match message_document(&message) {
        Some((document, _)) => Ok((document, caption)),
        None => Err(CoreError::Library(NOT_AN_INDEX.into())),
    }
}

#[cfg(test)]
#[path = "search_tests.rs"]
mod tests;
