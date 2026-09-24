//! The one loop that turns a set's planned reads into bytes, shared by the
//! HTTP server and the Android core so the two cannot drift apart on how a
//! part is resolved, cached or retried.

use anyhow::{Result, anyhow};
use grammers_client::Client;
use grammers_client::client::DownloadIter;
use grammers_client::media::Document;
use grammers_session::types::PeerRef;
use tokio::sync::mpsc;

use super::document::is_stale_reference;
use super::documents::PartDocuments;
use super::stream::{ChunkSource, StepCursor, pump_chunks};
use crate::catalog::PartLocation;
use crate::range::Step;

/// What a read needs besides its steps: the connection, the set's parts,
/// where each part's channel is, and the documents resolved so far.
pub struct Parts<'a> {
    pub client: &'a Client,
    pub documents: &'a PartDocuments,
    pub locations: &'a [PartLocation],
    pub channel_of: &'a (dyn Fn(&PartLocation) -> PeerRef + Sync),
}

impl Parts<'_> {
    /// Sends the bytes of `steps` to `out`, in order.
    ///
    /// Stops quietly once `out` is closed: a viewer who seeked or left is not
    /// a failure, and bytes nobody will read are not worth paying for.
    pub async fn fetch(&self, steps: &[Step], out: &mpsc::Sender<Result<Vec<u8>>>) -> Result<()> {
        self.documents.keep_only(self.locations);
        for step in steps {
            if out.is_closed() {
                return Ok(());
            }
            let location = self
                .locations
                .iter()
                .find(|location| location.span.idx == step.part_idx)
                .ok_or_else(|| anyhow!("part {} has no message", step.part_idx))?;
            self.fetch_step(location, *step, out).await?;
        }
        Ok(())
    }

    /// One step, retried once on a stale file reference — a film paused for
    /// hours comes back to one. The retry resolves the part again for a fresh
    /// reference and resumes after whatever was already delivered; a second
    /// refusal is real.
    async fn fetch_step(
        &self,
        location: &PartLocation,
        step: Step,
        out: &mpsc::Sender<Result<Vec<u8>>>,
    ) -> Result<()> {
        let io = TelegramPart {
            client: self.client,
            documents: self.documents,
            channel: (self.channel_of)(location),
        };
        fetch_step(&io, location, step, out).await
    }
}

/// Document/cache IO and raw downloads. Retry policy stays in `fetch_step`.
trait PartIo: Sync {
    type Document: Send + Sync;
    type Chunks: ChunkSource + Send;

    fn resolve(
        &self,
        location: &PartLocation,
    ) -> impl Future<Output = Result<Self::Document>> + Send;
    fn evict(&self, location: &PartLocation);
    fn download(&self, document: &Self::Document, skip_chunks: u32) -> Self::Chunks;
}

struct TelegramPart<'a> {
    client: &'a Client,
    documents: &'a PartDocuments,
    channel: PeerRef,
}

impl PartIo for TelegramPart<'_> {
    type Document = Document;
    type Chunks = DownloadIter;

    async fn resolve(&self, location: &PartLocation) -> Result<Document> {
        self.documents
            .resolve(self.client, self.channel, location)
            .await
    }

    fn evict(&self, location: &PartLocation) {
        self.documents.evict(location);
    }

    fn download(&self, document: &Document, skip_chunks: u32) -> DownloadIter {
        self.client
            .iter_download(document)
            .skip_chunks(i32::try_from(skip_chunks).unwrap_or(i32::MAX))
    }
}

async fn fetch_step(
    io: &impl PartIo,
    location: &PartLocation,
    step: Step,
    out: &mpsc::Sender<Result<Vec<u8>>>,
) -> Result<()> {
    let mut cursor = StepCursor::new(&step);
    let document = io.resolve(location).await?;
    match pump_chunks(
        io.download(&document, step.skip_chunks),
        &step,
        &mut cursor,
        out,
    )
    .await
    {
        Err(err) if is_stale_reference(&err) => {
            io.evict(location);
            let rest = step.after(step.take - cursor.remaining());
            let mut cursor = StepCursor::new(&rest);
            let fresh = io.resolve(location).await?;
            pump_chunks(
                io.download(&fresh, rest.skip_chunks),
                &rest,
                &mut cursor,
                out,
            )
            .await
        }
        result => result,
    }
}

#[cfg(test)]
#[path = "fetch_tests.rs"]
mod tests;
