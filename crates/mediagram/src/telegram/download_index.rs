//! Downloading the channel's newest pinned `#mlib-index` snapshot.
//!
//! Shared by the push guard (`index_guard`), which compares it against the
//! local index before publishing, and `pull-index`, which merges it in. Both
//! want the same file and the same "newest by `pushed_at`" rule, so it lives
//! in one place rather than two.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use grammers_client::message::Message;

use crate::telegram::client::Tg;
use mediagram_core::transport::document::message_document;

/// Downloads the channel's newest pinned index snapshot into `scratch`,
/// named uniquely per process so two commands running at once cannot
/// collide. `None` when the channel has no index pinned yet, or the pinned
/// message carries no document — neither is an error, since a fresh channel
/// or one mid-first-push simply has nothing to compare or merge against.
pub async fn download_channel_index(tg: &Tg, scratch: &Path) -> Result<Option<PathBuf>> {
    let Some(message) = newest_pinned_index(tg).await? else {
        return Ok(None);
    };
    let Some((document, _)) = message_document(&message) else {
        return Ok(None);
    };
    let path = scratch.join(format!("library.channel.{}.db", std::process::id()));
    let mut bytes = Vec::new();
    let mut chunks = tg.client.iter_download(&document);
    while let Some(chunk) = chunks
        .next()
        .await
        .context("downloading the channel's index")?
    {
        bytes.extend_from_slice(&chunk);
    }
    std::fs::write(&path, &bytes).with_context(|| format!("writing {}", path.display()))?;
    Ok(Some(path))
}

/// The newest pinned index by the time its caption says it was pushed, the
/// rule every reader uses; the message id breaks a tie.
async fn newest_pinned_index(tg: &Tg) -> Result<Option<Message>> {
    let mut newest: Option<(i64, i32, Message)> = None;
    let mut pinned = tg
        .client
        .search_messages(tg.channel)
        .filter(grammers_tl_types::enums::MessagesFilter::InputMessagesFilterPinned);
    while let Some(message) = pinned.next().await.context("listing pinned messages")? {
        if !mlib_spec::index_caption::is_index(message.text()) {
            continue;
        }
        let at = mlib_spec::index_caption::pushed_at(message.text()).unwrap_or(0);
        let key = (at, message.id());
        if newest.as_ref().is_none_or(|(a, id, _)| key > (*a, *id)) {
            newest = Some((at, message.id(), message));
        }
    }
    Ok(newest.map(|(_, _, message)| message))
}
