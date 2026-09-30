//! What `channel_index` needs from the channel, and nothing it can decide
//! itself.
//!
//! Two adapters: [`super::telegram_remote::TelegramRemote`] in production and
//! an in-memory channel in the integration tests. The policy — which pinned
//! message is the channel index, when to pull again, what to keep trying to
//! unpin — stays in `channel_index`, so the tests exercise the real decisions
//! against a channel that can misbehave on cue.

use std::collections::HashMap;

use anyhow::Result;

/// A message that may be an index snapshot.
#[derive(Clone, Debug)]
pub struct Candidate {
    pub id: i32,
    pub caption: String,
    /// Posted by the channel itself, not slipped in by a member.
    pub own_post: bool,
}

/// How an unpin ended.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Unpin {
    /// The request went through; whether it took is for `is_pinned` to say.
    Sent,
    /// The message no longer exists, or was already unpinned.
    Gone,
}

// Static dispatch only (`impl ChannelRemote`), like `upload::transport`, so
// no `Send` bound is needed.
#[allow(async_fn_in_trait)]
pub trait ChannelRemote {
    /// Every pinned message in the channel, index or not, plus the recent
    /// messages carrying the index marker: the snapshots an interrupted
    /// publish or another machine left unpinned. What the players read too.
    async fn candidates(&self) -> Result<Vec<Candidate>>;

    /// The document a message carries, or `None` when it carries none.
    async fn download(&self, id: i32) -> Result<Option<Vec<u8>>>;

    /// The caption of each message in `ids` that still exists; a missing
    /// key is a deleted message.
    async fn captions(&self, ids: &[i32]) -> Result<HashMap<i32, String>>;

    /// The channel's chat id, as the index records it on every part.
    fn chat_id(&self) -> i64;

    /// Sends `bytes` as a document named `name`, MIME type `mime`, with
    /// `caption`, returning its message id. One send path for every small
    /// document this uploader posts to the channel: the index snapshot, and
    /// a subtitle bundle.
    async fn send_document(
        &self,
        bytes: &[u8],
        name: &str,
        mime: &str,
        caption: &str,
    ) -> Result<i32>;

    /// Deletes one message. A message that is already gone is not an error:
    /// the caller wanted it gone.
    async fn delete_message(&self, id: i32) -> Result<()>;

    async fn pin(&self, id: i32) -> Result<()>;

    async fn unpin(&self, id: i32) -> Result<Unpin>;

    /// Whether the message itself still says it is pinned.
    async fn is_pinned(&self, id: i32) -> Result<bool>;
}
