//! The one loop that turns a set's planned reads into bytes, shared by the
//! HTTP server and the Android core so the two cannot drift apart on how a
//! part is resolved, cached or retried.

use anyhow::{Result, anyhow};
use grammers_client::Client;
use grammers_session::types::PeerRef;
use tokio::sync::mpsc;

use super::document::is_stale_reference;
use super::documents::PartDocuments;
use super::stream::{StepCursor, pump_step};
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
        let channel = (self.channel_of)(location);
        let mut cursor = StepCursor::new(&step);
        let document = self.documents.resolve(self.client, channel, location).await?;
        match pump_step(self.client, &document, &step, &mut cursor, out).await {
            Err(err) if is_stale_reference(&err) => {
                self.documents.evict(location);
                let rest = step.after(step.take - cursor.remaining());
                let mut cursor = StepCursor::new(&rest);
                let fresh = self.documents.resolve(self.client, channel, location).await?;
                pump_step(self.client, &fresh, &rest, &mut cursor, out).await
            }
            result => result,
        }
    }
}
