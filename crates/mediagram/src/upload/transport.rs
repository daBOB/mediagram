//! Abstraction over "send bytes as a captioned document" and "list recent
//! captions", so the pipeline can be tested without a live Telegram
//! connection. [`TelegramTransport`] is the real implementation.

use anyhow::{Context, Result, bail};
use grammers_client::Client;
use grammers_client::message::InputMessage;
use grammers_session::types::PeerRef;
use mediagram_core::document;
use mlib_spec::caption::{Caption, Part};

use super::part_reader::PartReader;
use crate::telegram::client::Tg;
use crate::telegram::retry::{self, with_flood_wait_only};

/// Result of successfully sending one part as a document message.
pub struct Sent {
    pub message_id: i64,
    pub doc_id: i64,
}

/// One channel message as seen by a history scan, kept minimal to what
/// `adopt` needs to match it against a pending part.
pub struct Seen {
    pub message_id: i64,
    pub doc_id: Option<i64>,
    pub caption: String,
}

/// Sends one part and lists recent messages to support resume-by-adoption.
///
/// `send_part` takes `caption` with a placeholder `part.sha256` (unknown
/// until the reader is fully drained) and fills in the real hash internally
/// before rendering and sending the caption text; this is what lets the
/// upload and the hash share a single read pass over the part's bytes.
// No `Send` bound is needed: callers always drive this with static dispatch
// (`impl Transport`), never as a trait object, matching `mediagram_tmdb::tmdb_client::TmdbApi`.
#[allow(async_fn_in_trait)]
pub trait Transport {
    async fn send_part(
        &self,
        name: String,
        mime: &str,
        caption: &Caption,
        human: &str,
        reader: &mut PartReader,
        len: u64,
    ) -> Result<Sent>;

    async fn recent_messages(&self, limit: usize) -> Result<Vec<Seen>>;

    fn chat_id(&self) -> i64;
}

/// Real transport over a connected [`Tg`] client.
pub struct TelegramTransport {
    client: Client,
    channel: PeerRef,
    max_attempts: u32,
}

impl TelegramTransport {
    pub fn new(tg: &Tg, max_attempts: u32) -> TelegramTransport {
        TelegramTransport {
            client: tg.client.clone(),
            channel: tg.channel,
            max_attempts,
        }
    }
}

impl Transport for TelegramTransport {
    async fn send_part(
        &self,
        name: String,
        mime: &str,
        caption: &Caption,
        human: &str,
        reader: &mut PartReader,
        len: u64,
    ) -> Result<Sent> {
        // A dropped connection restarts the part rather than ending the set.
        // These bytes go into a fresh, unreferenced file on the server, so a
        // failed attempt leaves nothing behind to clash with — it costs the
        // bytes already sent and nothing else. Not retrying costs the rest of
        // the set: the error ends the process, the upload lock passes to
        // whatever was queued behind it, and this set sits half-sent until
        // somebody notices and runs `resume`.
        let attempts = self.max_attempts.max(1);
        let mut attempt = 0u32;
        let uploaded = loop {
            attempt += 1;
            match self
                .client
                .upload_stream(&mut *reader, len as usize, name.clone())
                .await
            {
                Ok(uploaded) => break uploaded,
                Err(err) if attempt >= attempts => {
                    return Err(anyhow::anyhow!("uploading part bytes: {err}"));
                }
                Err(err) => {
                    let delay = retry::backoff(attempt);
                    tracing::warn!(attempt, ?delay, error = %err, "restarting the part upload");
                    tokio::time::sleep(delay).await;
                    reader
                        .rewind()
                        .await
                        .map_err(|err| anyhow::anyhow!("rereading the part to retry it: {err}"))?;
                }
            }
        };
        if reader.bytes_read() != len {
            bail!(
                "source shrank during upload: read {} of {len} planned bytes",
                reader.bytes_read()
            );
        }

        let final_caption = caption.with_part(Part {
            sha256: reader.finalize(),
            ..caption.part.clone()
        });
        let text = mlib_spec::to_text(&final_caption, human).context("rendering part caption")?;

        let client = self.client.clone();
        let channel = self.channel;
        let mime = mime.to_string();
        // Only FLOOD_WAIT is retried here: any other failure after the server may
        // have committed the send is left to the adopt scan on the next resume.
        let message = with_flood_wait_only(self.max_attempts, move || {
            let client = client.clone();
            let uploaded = uploaded.clone();
            let text = text.clone();
            let mime = mime.clone();
            async move {
                client
                    .send_message(
                        channel,
                        InputMessage::new()
                            .mime_type(&mime)
                            .text(text)
                            .document(uploaded),
                    )
                    .await
            }
        })
        .await
        .context("sending part message")?;

        let doc_id = match document::message_document(&message) {
            Some((_, id)) => id,
            None => bail!("sent part message has no usable document media"),
        };
        Ok(Sent {
            message_id: i64::from(message.id()),
            doc_id,
        })
    }

    async fn recent_messages(&self, limit: usize) -> Result<Vec<Seen>> {
        let mut iter = self.client.iter_messages(self.channel).limit(limit);
        let mut out = Vec::with_capacity(limit);
        while let Some(message) = iter
            .next()
            .await
            .context("scanning recent channel messages")?
        {
            let doc_id = document::message_document(&message).map(|(_, id)| id);
            out.push(Seen {
                message_id: i64::from(message.id()),
                doc_id,
                caption: message.text().to_string(),
            });
        }
        Ok(out)
    }

    fn chat_id(&self) -> i64 {
        crate::telegram::client::chat_id_of(self.channel)
    }
}
