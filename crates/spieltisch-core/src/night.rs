//! The night: one game-night host per process, started, stepped back and
//! stopped from Kotlin. Its film titles and their images come from the media
//! app; without them the games that need films stay blocked in the lobby.
//! Preisfrage's articles and their photos come from the home cache server,
//! which Kotlin reads at the address the media app names.

use std::path::PathBuf;
use std::sync::{Arc, LazyLock};

use mediagram_game::{ArticleList, GameTitle, Host};
use tokio::sync::Mutex as AsyncMutex;

use crate::media_images::{MediaImages, image_lookup, textless_lookup};

const PREFERRED_PORT: u16 = 8080;
/// One file across nights, fetched from the box after a test night.
const GAME_LOG: &str = "grand-allee-log.jsonl";

/// How a night is set up; the media app decides the kids lock and the titles, and the core only applies them.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SpieltischConfig {
    /// Holds the lobby's "Nur für Kinder" on, so nobody can switch it off, and drops every title that is not kid-safe.
    pub kids_locked: bool,
    /// The app's files folder, where the games' decision log is kept.
    pub data_dir: String,
    /// The media app's titles as JSON, in `GameTitle`'s shape; `[]` when it gave none.
    pub titles: String,
    /// Preisfrage's article list as the home cache server holds it; empty when there is none.
    pub articles: String,
}

/// Where the box shows the game and where phones join it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct NightUrls {
    /// The TV page on loopback, `http://127.0.0.1:<port>/tv`.
    pub tv_url: String,
    /// What the QR code shows: the box's current LAN address, since DHCP moves it.
    pub join_url: String,
}

/// Why a night did not start.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum NightError {
    /// The host's server could not bind or start.
    #[error("The game night could not start: {0}")]
    Start(#[from] std::io::Error),
}

static NIGHT: LazyLock<AsyncMutex<Option<Host>>> = LazyLock::new(AsyncMutex::default);

/// Stops a night already running, then hosts a new one over the media app's
/// titles, whose images `images` fetches from it.
#[uniffi::export(async_runtime = "tokio")]
pub async fn start_night(config: SpieltischConfig, images: Arc<dyn MediaImages>) -> Result<NightUrls, NightError> {
    let log = Some(PathBuf::from(config.data_dir).join(GAME_LOG));
    let (titles, articles) = (titles_of(&config.titles), articles_of(&config.articles));
    replace(&NIGHT, titles, articles, config.kids_locked, PREFERRED_PORT, images, log).await
}

/// No titles when the JSON cannot be read: a night without films beats no night.
fn titles_of(json: &str) -> Vec<GameTitle> {
    serde_json::from_str(json).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "the media app's titles could not be read; the night has no films");
        Vec::new()
    })
}

/// No articles when there is no list or it cannot be read: Preisfrage then says why it cannot start.
fn articles_of(json: &str) -> ArticleList {
    if json.is_empty() {
        return ArticleList::default();
    }
    ArticleList::from_json(json).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "the cache server's article list could not be read; no Preisfrage tonight");
        ArticleList::default()
    })
}

/// Ends the running game and returns to the lobby. `false` when there is
/// nothing to end: no night runs, or one runs with no game in progress.
#[uniffi::export(async_runtime = "tokio")]
pub async fn night_back() -> bool {
    back(&NIGHT).await
}

/// Ends the night; nothing to do when none runs.
#[uniffi::export(async_runtime = "tokio")]
pub async fn stop_night() {
    stop(&NIGHT).await
}

/// Hosts a night in `slot`, ending the one it held first so the port is free.
async fn replace(
    slot: &AsyncMutex<Option<Host>>,
    titles: Vec<GameTitle>,
    articles: ArticleList,
    kids_locked: bool,
    port: u16,
    images: Arc<dyn MediaImages>,
    log: Option<PathBuf>,
) -> Result<NightUrls, NightError> {
    let mut held = slot.lock().await;
    if let Some(running) = held.take() {
        running.stop().await;
    }
    let (textless, images) = (Some(textless_lookup(images.clone())), Some(image_lookup(images)));
    let host = mediagram_game::start_game_night(titles, articles, kids_locked, port, textless, images, log).await?;
    let urls = NightUrls { tv_url: host.tv_url(), join_url: host.join_url() };
    *held = Some(host);
    Ok(urls)
}

async fn back(slot: &AsyncMutex<Option<Host>>) -> bool {
    match slot.lock().await.as_ref() {
        Some(host) => host.back().await,
        None => false,
    }
}

async fn stop(slot: &AsyncMutex<Option<Host>>) {
    if let Some(host) = slot.lock().await.take() {
        host.stop().await;
    }
}

#[cfg(test)]
#[path = "night_tests.rs"]
mod tests;
