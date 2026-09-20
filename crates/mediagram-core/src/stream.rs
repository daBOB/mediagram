//! Turning planned reads into bytes: the half that talks to Telegram.
//!
//! [`StepCursor`] is pure and holds the arithmetic that trims a download to
//! the requested range. [`pump`] drives the transport and is deliberately thin
//! around it, so the part that can corrupt a video is the part under test.

use std::pin::Pin;

use anyhow::{Context, Result, anyhow, bail};
use bytes::Bytes;
use futures::Stream;
use grammers_client::Client;
use grammers_client::media::Document;
use grammers_session::types::PeerRef;
use tokio::sync::mpsc;

use super::catalog::PartLocation;
use super::document::message_document;
use super::range::Step;

/// The bytes of a set's stream, in the order a viewer reads them.
pub type ByteStream = Pin<Box<dyn Stream<Item = Result<Bytes, std::io::Error>> + Send>>;

/// Where a stream's bytes come from. [`crate::telegram::TelegramSource`] is
/// the real implementation; a test can supply a known file instead.
pub trait ByteSource: Send + Sync + 'static {
    /// The bytes of `steps`, in order, for a set whose parts are `locations`.
    fn stream(&self, locations: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream;
}

/// Trims one part's chunk stream to the bytes a [`Step`] asked for.
///
/// Telegram delivers whole 512 KiB chunks. Skipping chunks lands us at or
/// before the first wanted byte, so the first chunk has a head to discard;
/// the range usually ends mid-chunk, so the last has a tail to cut.
pub struct StepCursor {
    head_drop: u64,
    remaining: u64,
}

impl StepCursor {
    pub fn new(step: &Step) -> Self {
        StepCursor {
            head_drop: step.head_drop,
            remaining: step.take,
        }
    }

    /// The slice of `chunk` that belongs in the response. Advances the cursor.
    pub fn take<'a>(&mut self, chunk: &'a [u8]) -> &'a [u8] {
        if self.remaining == 0 {
            return &[];
        }
        if self.head_drop > 0 {
            let drop = self.head_drop.min(chunk.len() as u64);
            self.head_drop -= drop;
            let chunk = &chunk[drop as usize..];
            return self.take_body(chunk);
        }
        self.take_body(chunk)
    }

    fn take_body<'a>(&mut self, chunk: &'a [u8]) -> &'a [u8] {
        let take = self.remaining.min(chunk.len() as u64) as usize;
        self.remaining -= take as u64;
        &chunk[..take]
    }

    pub fn is_done(&self) -> bool {
        self.remaining == 0
    }

    pub fn remaining(&self) -> u64 {
        self.remaining
    }
}

/// The document of a part's message, resolved by message id.
///
/// A `Document` handle carries a file reference that Telegram expires, so the
/// message is re-fetched per stream rather than cached across requests.
pub async fn part_document(client: &Client, channel: PeerRef, message_id: i64) -> Result<Document> {
    let id = i32::try_from(message_id)
        .map_err(|_| anyhow!("message id {message_id} is out of range"))?;
    let messages = client
        .get_messages_by_id(channel, &[id])
        .await
        .with_context(|| format!("fetching message {id}"))?;
    let message = messages
        .into_iter()
        .next()
        .flatten()
        .ok_or_else(|| anyhow!("message {id} no longer exists"))?;
    match message_document(&message) {
        Some((document, _)) => Ok(document),
        None => bail!("message {id} carries no downloadable document"),
    }
}

/// Downloads one step and sends its bytes to `out`, in order.
///
/// Stops as soon as the step is satisfied, so a range near the start of a
/// 3.5 GiB part costs one chunk rather than the part. Returns an error if the
/// download ends early: a short body would be served as a complete one and
/// the player would show a truncated file rather than a failure.
pub async fn pump_step(
    client: &Client,
    document: &Document,
    step: &Step,
    out: &mpsc::Sender<Result<Vec<u8>>>,
) -> Result<()> {
    let mut cursor = StepCursor::new(step);
    let mut chunks = client
        .iter_download(document)
        .skip_chunks(i32::try_from(step.skip_chunks).unwrap_or(i32::MAX));
    while !cursor.is_done() {
        let chunk = match chunks.next().await.context("downloading chunk")? {
            Some(chunk) => chunk,
            None => bail!(
                "download ended with {} bytes of part {} still owed",
                cursor.remaining(),
                step.part_idx
            ),
        };
        let wanted = cursor.take(&chunk);
        if wanted.is_empty() {
            continue;
        }
        // A closed receiver means the viewer went away mid-stream, which is
        // ordinary: stop quietly rather than logging a failure.
        if out.send(Ok(wanted.to_vec())).await.is_err() {
            return Ok(());
        }
    }
    Ok(())
}
