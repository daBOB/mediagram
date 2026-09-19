//! The real source of a stream's bytes: Telegram.
//!
//! One task per response pulls chunks and pushes them down a bounded channel,
//! so memory stays flat whatever the size of the set and a slow viewer slows
//! the download rather than filling a buffer with a 6.5 GiB film.

use std::sync::Arc;

use bytes::Bytes;
use grammers_client::Client;
use grammers_session::types::PeerRef;
use tokio::sync::mpsc;

use super::catalog::PartLocation;
use super::range::Step;
use super::stream::{ByteSource, ByteStream, part_document, pump_step};

/// Chunks held between the download and the socket. Four 512 KiB chunks is
/// enough to keep the download busy across a slow write without letting the
/// response run far ahead of the viewer.
const BUFFERED_CHUNKS: usize = 4;

pub struct TelegramSource {
    client: Client,
    channel: PeerRef,
}

impl TelegramSource {
    pub fn new(client: Client, channel: PeerRef) -> Arc<Self> {
        Arc::new(TelegramSource { client, channel })
    }
}

impl ByteSource for TelegramSource {
    fn stream(&self, locations: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
        let (tx, rx) = mpsc::channel(BUFFERED_CHUNKS);
        let client = self.client.clone();
        let channel = self.channel;

        tokio::spawn(async move {
            for step in steps {
                // The viewer seeked or closed the tab: stop paying for bytes
                // nobody will read.
                if tx.is_closed() {
                    return;
                }
                let Some(location) = locations.iter().find(|l| l.span.idx == step.part_idx) else {
                    let _ = tx
                        .send(Err(anyhow::anyhow!(
                            "part {} has no message",
                            step.part_idx
                        )))
                        .await;
                    return;
                };
                // Resolved per response rather than cached: Telegram expires
                // the file reference inside a document handle.
                let document = match part_document(&client, channel, location.message_id).await {
                    Ok(document) => document,
                    Err(err) => {
                        let _ = tx.send(Err(err)).await;
                        return;
                    }
                };
                if let Err(err) = pump_step(&client, &document, &step, &tx).await {
                    let _ = tx.send(Err(err)).await;
                    return;
                }
            }
        });

        // An error becomes an I/O error on the body, which ends the response
        // mid-stream. With `Content-Length` already sent, the client sees a
        // truncated response and fails, which is the honest outcome: better
        // than silently serving fewer bytes than promised.
        Box::pin(futures::stream::unfold(rx, |mut rx| async move {
            let item = rx.recv().await?;
            let item = item
                .map(Bytes::from)
                .map_err(|err| std::io::Error::other(format!("{err:#}")));
            Some((item, rx))
        }))
    }
}
