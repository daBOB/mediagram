//! Rewriting captions in the channel.
//!
//! The index and the channel have to agree. `rescan` rebuilds the index from
//! captions, so an index corrected on its own would be undone the next time
//! anyone ran it — the channel is the archive, and it is what has to change.

use anyhow::{Context, Result};
use grammers_client::Client;
use grammers_client::message::InputMessage;
use grammers_session::types::PeerRef;

use crate::edit::captions::CaptionWrite;
use crate::telegram::retry::with_retry;

/// Rewrites each message's caption, in part order.
///
/// Returns how many were written. A failure stops the run and says which part
/// it reached: the remaining messages keep their old captions, and re-running
/// the same edit finishes the job, since writing a caption twice is harmless.
pub async fn write_captions(
    client: &Client,
    channel: PeerRef,
    writes: &[CaptionWrite],
    max_attempts: u32,
) -> Result<usize> {
    let mut written = 0;
    for write in writes {
        let id = i32::try_from(write.message_id)
            .with_context(|| format!("message id {} is out of range", write.message_id))?;
        let text = write.text.clone();

        with_retry(max_attempts, || {
            let client = client.clone();
            let text = text.clone();
            async move {
                client
                    .edit_message(channel, id, InputMessage::new().text(text))
                    .await
            }
        })
        .await
        .with_context(|| {
            format!(
                "editing the caption of part {} (message {}); parts before it are already \
                 rewritten, so re-running this edit finishes the job",
                write.part_idx, write.message_id
            )
        })?;
        written += 1;
    }
    Ok(written)
}
