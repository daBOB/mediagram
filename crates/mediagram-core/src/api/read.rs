//! Serving one read against a set's virtual file: the same range planning
//! and download loop `mediagram serve` uses, collected into a buffer rather
//! than streamed into an HTTP response body.

use std::collections::HashMap;

use grammers_session::types::{PeerId, PeerRef};
use tokio::sync::mpsc;

use crate::catalog::{self as queries, PartLocation};
use crate::range::{self, ByteRange, PartSpan};
use crate::transport::fetch::Parts;
use crate::transport::source::BUFFERED_CHUNKS;

use super::account::session;
use super::channel::library;
use super::account::revoked;
use super::{Core, CoreError, store};

/// Where a set's parts live, if it may be played. Blocking: a catalog query.
///
/// The gate is the one the catalog lists with and `total_size` answers with.
/// A set that is incomplete, or whose parts do not add up to its total, must
/// not be readable here while being refused everywhere else.
pub(super) fn locations(core: &Core, set_id: &str) -> Result<Vec<PartLocation>, CoreError> {
    let conn = store::open(core)?;
    let playable = queries::playable_set(&conn, set_id).map_err(CoreError::io("reading the catalog"))?;
    let locations = match playable {
        Some(_) => queries::part_locations(&conn, set_id).map_err(CoreError::io("reading the catalog"))?,
        None => Vec::new(),
    };
    if locations.is_empty() {
        return Err(CoreError::NotFound("set not found".into()));
    }
    Ok(locations)
}

/// `len` bytes of the set from `offset`, fetched from the channel parts
/// `locations` names.
pub(super) async fn read(
    core: &Core,
    locations: Vec<PartLocation>,
    offset: u64,
    len: u32,
) -> Result<Vec<u8>, CoreError> {
    let spans: Vec<PartSpan> = locations.iter().map(|location| location.span).collect();
    let total = range::total_size(&spans);
    if total == 0 || offset >= total {
        return Err(CoreError::NotFound("read is past the end of the set".into()));
    }
    // A zero-length request is trivially satisfied, and must return before
    // `end` is computed: `end = offset - 1` below is only ever valid because
    // `len >= 1` guarantees `offset + len - 1 >= offset`. At `len == 0` that
    // guarantee is gone, `end` can land one below `offset`, and subtracting
    // `offset` back out of it underflows a `u64`.
    if len == 0 {
        return Ok(Vec::new());
    }
    let end = offset
        .saturating_add(u64::from(len))
        .saturating_sub(1)
        .min(total - 1);
    let steps = range::plan_reads(&spans, &ByteRange { start: offset, end });

    let client = session::client(core).await;
    // Every channel is looked up before any byte is fetched, so a part this
    // device cannot address fails the read as that, not as a broken download.
    let handles = library::read(&library::path(core))?;
    let mut channels = HashMap::new();
    for location in &locations {
        channels.insert(location.chat_id, channel_ref(&handles, location.chat_id)?);
    }
    let documents = core.state.lock().await.documents.clone();
    let parts = Parts {
        client: &client,
        documents: &documents,
        locations: &locations,
        channel_of: &|location| channels[&location.chat_id],
    };

    // Clamped to what this read can actually return, not to the caller's
    // raw `len`: an out-of-range `UInt` from Kotlin must not become an
    // attempt to reserve up to 4 GiB before a single byte is read.
    let capacity = usize::try_from(end - offset + 1).unwrap_or(usize::MAX);
    let mut out = Vec::with_capacity(capacity);
    // Drained while the download runs, so a read larger than the buffer
    // cannot deadlock against a receiver that waits for the sender to finish.
    let (tx, mut rx) = mpsc::channel(BUFFERED_CHUNKS);
    let fetch = async move { parts.fetch(&steps, &tx).await };
    let drain = async {
        while let Some(chunk) = rx.recv().await {
            out.extend(chunk?);
        }
        anyhow::Ok(())
    };
    let (fetched, drained) = tokio::join!(fetch, drain);
    if let Err(err) = fetched.and(drained) {
        return Err(failed(core, INTERRUPTED, err).await);
    }
    Ok(out)
}

const INTERRUPTED: &str = "the download ended before it finished";

/// What Kotlin is told when a Telegram call fails: `what`, with the cause
/// logged in Rust — or `NotAuthorized` when the login itself was refused.
async fn failed(core: &Core, what: &str, err: anyhow::Error) -> CoreError {
    let fallback = CoreError::network(what)(&err);
    revoked::unless_revoked(core, err.as_ref(), fallback).await
}

/// How to address the channel a part lives in.
///
/// The recorded `access_hash` is the whole of it. Telegram refuses a channel
/// addressed without one — `CHANNEL_INVALID` — and the ambient authority a
/// bare id carries is only ever enough for a bot or a contact, which a
/// library channel is not. So a channel this device has no record of is said
/// to be unaddressable here rather than asked for and refused: the answer is
/// to pick the library again, which is what records it.
fn channel_ref(handles: &library::Handles, chat_id: i64) -> Result<PeerRef, CoreError> {
    if PeerId::from_bot_api_dialog_id(chat_id).is_none() {
        return Err(CoreError::Io("the catalog names an invalid channel".into()));
    }
    library::peer_for_chat(handles, chat_id).ok_or_else(|| {
        CoreError::NotFound(
            "this device has no way to reach the channel that set is in. \
             Choose the library again to record it."
                .into(),
        )
    })
}

#[cfg(test)]
#[path = "read_tests.rs"]
mod tests;
