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

use super::documents::PartDocuments;
use super::fetch::Parts;
use super::stream::{ByteSource, ByteStream};
use crate::catalog::PartLocation;
use crate::range::Step;

/// Chunks held between the download and the socket. Four 512 KiB chunks is
/// enough to keep the download busy across a slow write without letting the
/// response run far ahead of the viewer.
pub const BUFFERED_CHUNKS: usize = 4;

pub struct TelegramSource {
    client: Client,
    channel: PeerRef,
    documents: Arc<PartDocuments>,
}

impl TelegramSource {
    pub fn new(client: Client, channel: PeerRef) -> Arc<Self> {
        Arc::new(TelegramSource {
            client,
            channel,
            documents: Arc::default(),
        })
    }
}

impl ByteSource for TelegramSource {
    fn stream(&self, locations: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
        let (tx, rx) = mpsc::channel(BUFFERED_CHUNKS);
        let client = self.client.clone();
        let channel = self.channel;
        let documents = Arc::clone(&self.documents);

        tokio::spawn(async move {
            let parts = Parts {
                client: &client,
                documents: &documents,
                locations: &locations,
                channel_of: &move |_| channel,
            };
            if let Err(err) = parts.fetch(&steps, &tx).await {
                let _ = tx.send(Err(err)).await;
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
