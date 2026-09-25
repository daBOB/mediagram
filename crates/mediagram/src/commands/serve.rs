//! `mediagram serve`: the local playback API.
//!
//! One process holds the Telegram session and a read-only index, and answers
//! Range requests for a set's bytes. Everything downstream — a browser, an
//! ffmpeg transcode — is a client of this and never talks to Telegram itself.

use anyhow::{Context, Result};
use mediagram_core::catalog;
use mediagram_core::transport::source::TelegramSource;
use tokio::net::TcpListener;

use crate::config::Config;
use crate::index::db;
use crate::serve::routes::router;
use crate::telegram::client::Tg;

/// Loopback only. Reaching the library from outside this machine is a
/// deliberate act with its own authentication, not a default.
pub const DEFAULT_ADDR: &str = "127.0.0.1:8765";

pub async fn run(cfg: &Config, addr: Option<String>) -> Result<()> {
    let data_dir = cfg.data_dir()?;
    // Read-only: a serving process must never checkpoint or migrate the
    // index the uploader owns.
    let index = db::open_read_only(&data_dir, "serve")?;
    let playable = catalog::list_playable(&index)?.len();

    let addr = addr
        .or_else(|| cfg.serve_addr.clone())
        .unwrap_or_else(|| DEFAULT_ADDR.to_string());
    let listener = TcpListener::bind(&addr)
        .await
        .with_context(|| format!("binding {addr}"))?;
    let bound = listener.local_addr().context("reading the bound address")?;
    let tg = Tg::connect(cfg).await?;
    println!("channel: {} ({})", tg.channel_title, tg.chat_id());
    let source = TelegramSource::new(tg.client.clone(), tg.channel);

    if !bound.ip().is_loopback() {
        tracing::warn!(
            "serving on {bound}, which is reachable from the network: this API has no authentication"
        );
    }
    println!("serving {playable} playable sets on http://{bound}");

    let result = axum::serve(listener, router(index, source))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
            println!("\nstopping");
        })
        .await
        .context("serving");
    tg.shutdown().await;
    result
}
