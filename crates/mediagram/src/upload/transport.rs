//! Abstraction over "send bytes as a captioned document" and "list recent
//! captions", so the pipeline can be tested without a live Telegram
//! connection. [`TelegramTransport`] is the real implementation.

use anyhow::{Context, Result, bail};
use grammers_client::Client;
use grammers_client::media::Media;
use grammers_client::message::InputMessage;
use grammers_session::types::PeerRef;
use mlib_spec::caption::{Caption, Part};

use super::part_reader::PartReader;
use crate::telegram::client::Tg;
use crate::telegram::retry::with_flood_wait_only;

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
// (`impl Transport`), never as a trait object, matching `metadata::tmdb_client::TmdbApi`.
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
        let uploaded = self
            .client
            .upload_stream(reader, len as usize, name)
            .await
            .map_err(|err| anyhow::anyhow!("uploading part bytes: {err}"))?;
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

        let doc_id = match message.media() {
            Some(Media::Document(doc)) => doc.id(),
            _ => bail!("sent part message has no document media"),
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
            let doc_id = match message.media() {
                Some(Media::Document(doc)) => Some(doc.id()),
                _ => None,
            };
            out.push(Seen {
                message_id: i64::from(message.id()),
                doc_id,
                caption: message.text().to_string(),
            });
        }
        Ok(out)
    }

    fn chat_id(&self) -> i64 {
        self.channel
            .id
            .bot_api_dialog_id()
            .unwrap_or_else(|| self.channel.id.bare_id_unchecked())
    }
}
