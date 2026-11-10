//! The night: one game-night host per process, started, stepped back and
//! stopped from Kotlin. Spieltisch on its own has no film titles, so the
//! games that need them stay blocked in the lobby, and no textless posters.

use std::path::PathBuf;
use std::sync::LazyLock;

use mediagram_game::Host;
use tokio::sync::Mutex as AsyncMutex;

const PREFERRED_PORT: u16 = 8080;
/// One file across nights, fetched from the box after a test night.
const GAME_LOG: &str = "grand-allee-log.jsonl";

/// How a night is set up; Kotlin decides the kids lock and the core only applies it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SpieltischConfig {
    /// Holds the lobby's "Nur für Kinder" on, so nobody can switch it off.
    pub kids_locked: bool,
    /// The app's files folder, where the games' decision log is kept.
    pub data_dir: String,
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

/// Stops a night already running, then hosts a new one.
#[uniffi::export(async_runtime = "tokio")]
pub async fn start_night(config: SpieltischConfig) -> Result<NightUrls, NightError> {
    let log = Some(PathBuf::from(config.data_dir).join(GAME_LOG));
    replace(&NIGHT, config.kids_locked, PREFERRED_PORT, log).await
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
    kids_locked: bool,
    port: u16,
    log: Option<PathBuf>,
) -> Result<NightUrls, NightError> {
    let mut held = slot.lock().await;
    if let Some(running) = held.take() {
        running.stop().await;
    }
    let host = mediagram_game::start_game_night(Vec::new(), kids_locked, port, None, log).await?;
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
