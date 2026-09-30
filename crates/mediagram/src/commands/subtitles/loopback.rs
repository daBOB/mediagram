//! The player's streaming router on a loopback port of its own, so ffprobe
//! and ffmpeg can read a set in the channel over HTTP Range while this
//! process — the one Telegram session — does the fetching.

use std::sync::Arc;

use anyhow::{Context, Result};
use mediagram_core::transport::stream::ByteSource;
use rusqlite::Connection;
use tokio::net::TcpListener;
use tokio::sync::oneshot;
use tokio::task::JoinHandle;

use crate::serve::routes::router;

pub struct Loopback {
    base: String,
    stop: oneshot::Sender<()>,
    task: JoinHandle<()>,
}

impl Loopback {
    /// `index` must be a read-only connection: the router only looks up parts.
    pub async fn start(index: Connection, source: Arc<dyn ByteSource>) -> Result<Loopback> {
        let listener = TcpListener::bind("127.0.0.1:0")
            .await
            .context("binding a loopback port")?;
        let port = listener
            .local_addr()
            .context("reading the bound port")?
            .port();
        let (stop, stopped) = oneshot::channel::<()>();
        let task = tokio::spawn(async move {
            let served = axum::serve(listener, router(index, source))
                .with_graceful_shutdown(async {
                    let _ = stopped.await;
                })
                .await;
            if let Err(err) = served {
                eprintln!("warning: the loopback server stopped: {err}");
            }
        });
        Ok(Loopback {
            base: format!("http://127.0.0.1:{port}"),
            stop,
            task,
        })
    }

    pub fn stream_url(&self, set_id: &str) -> String {
        format!("{}/sets/{set_id}/stream", self.base)
    }

    pub async fn shutdown(self) {
        let _ = self.stop.send(());
        let _ = self.task.await;
    }
}
