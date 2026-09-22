//! Serving one read against a set's virtual file: the same range-planning
//! code `mediagram serve` uses, driving the Telegram transport directly
//! rather than through an HTTP response body.

use anyhow::Context;
use grammers_session::types::{PeerId, PeerRef};
use tokio::sync::mpsc;

use crate::catalog::{self as queries, PartLocation};
use crate::document::is_stale_reference;
use crate::range::{self, ByteRange, PartSpan};
use crate::stream;

use super::account::session;
use super::channel::library;
use super::account::revoked;
use super::{Core, CoreError, store};

/// Chunks buffered between the download and this call's own accumulation.
/// Matches [`crate::telegram`]'s buffer: enough to keep the download busy
/// without letting it run far ahead of a caller that stopped reading.
const BUFFERED_CHUNKS: usize = 4;

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
    set_id: String,
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
    // Read once, not per part: every part of a set lives in the same
    // channel, and this is a file on disk.
    let handles = library::read(&library::path(core))?;

    // Clamped to what this read can actually return, not to the caller's
    // raw `len`: an out-of-range `UInt` from Kotlin must not become an
    // attempt to reserve up to 4 GiB before a single byte is read.
    let capacity = usize::try_from(end - offset + 1).unwrap_or(usize::MAX);
    let mut out = Vec::with_capacity(capacity);
    for step in steps {
        let location = locations
            .iter()
            .find(|location| location.span.idx == step.part_idx)
            .ok_or_else(|| CoreError::NotFound("set not found".into()))?;
        let channel = channel_ref(&handles, location.chat_id)?;
        let document = document_for(core, &client, channel, &set_id, location.message_id).await?;

        let start = out.len();
        match fetch_step(&client, document, step, &mut out).await {
            Ok(()) => {}
            // The held handle's file reference has expired — a film paused
            // for hours comes back to one. Resolve the part again for a
            // fresh reference and retry once; a second refusal is real.
            Err(err) if is_stale_reference(&err) => {
                out.truncate(start);
                core.state.lock().await.documents.evict(&set_id, location.message_id);
                let fresh = document_for(core, &client, channel, &set_id, location.message_id).await?;
                if let Err(err) = fetch_step(&client, fresh, step, &mut out).await {
                    return Err(failed(core, INTERRUPTED, err).await);
                }
            }
            Err(err) => return Err(failed(core, INTERRUPTED, err).await),
        }
    }
    Ok(out)
}

/// Downloads one step onto the end of `out`.
///
/// A separate task drains while `pump_step` runs, so a step whose bytes
/// outgrow the buffer cannot deadlock against a receiver that only starts
/// reading once the sender is done. The pump's own error is returned intact,
/// so the caller can tell a stale file reference from a dropped connection.
async fn fetch_step(
    client: &grammers_client::Client,
    document: grammers_client::media::Document,
    step: range::Step,
    out: &mut Vec<u8>,
) -> anyhow::Result<()> {
    let (tx, mut rx) = mpsc::channel(BUFFERED_CHUNKS);
    let pump_client = client.clone();
    let pump = tokio::spawn(async move { stream::pump_step(&pump_client, &document, &step, &tx).await });
    while let Some(chunk) = rx.recv().await {
        out.extend(chunk?);
    }
    pump.await
        .context("the download task did not finish cleanly")?
}

const INTERRUPTED: &str = "the download ended before it finished";

/// What Kotlin is told when a Telegram call fails: `what`, with the cause
/// logged in Rust — or `NotAuthorized` when the login itself was refused.
async fn failed(core: &Core, what: &str, err: anyhow::Error) -> CoreError {
    let fallback = CoreError::network(what)(&err);
    revoked::unless_revoked(core, err.as_ref(), fallback).await
}

/// The document a part's bytes live in, resolved once per set.
///
/// A player reads the same few parts a few hundred times, and resolving is
/// a round trip every time — a third of the cost of a read that otherwise
/// fetches two chunks.
async fn document_for(
    core: &Core,
    client: &grammers_client::Client,
    channel: PeerRef,
    set_id: &str,
    message_id: i64,
) -> Result<grammers_client::media::Document, CoreError> {
    if let Some(held) = core.state.lock().await.documents.get(set_id, message_id) {
        return Ok(held);
    }
    let document = match stream::part_document(client, channel, message_id).await {
        Ok(document) => document,
        Err(err) => return Err(failed(core, "the part could not be resolved", err).await),
    };
    core.state
        .lock()
        .await
        .documents
        .put(set_id, message_id, document.clone());
    Ok(document)
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
