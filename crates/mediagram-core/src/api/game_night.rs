//! `Core::start_game_night`/`game_night_back`/`stop_game_night`: the party
//! games the box hosts for phones on the same network. A game night reads the
//! catalog and credits but writes nothing to any profile's state.

use std::collections::HashSet;
use std::sync::Arc;

use mediagram_game::{GameTitle, Host};
use tokio::sync::Mutex as AsyncMutex;

use crate::dto::{GameNightConfig, GameNightUrls};
use crate::error::CoreError;
use crate::shows::title_of;

use super::{Core, store};

const PREFERRED_PORT: u16 = 8080;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Stops a night already running, then hosts a new one over the titles
    /// the catalog holds.
    pub async fn start_game_night(self: Arc<Self>, config: GameNightConfig) -> Result<GameNightUrls, CoreError> {
        // Textless posters, which need the key, come with the poster game.
        let GameNightConfig { kids_set_ids, kids_locked, tmdb_key: _tmdb_key } = config;
        let kids: HashSet<String> = kids_set_ids.into_iter().collect();
        let titles = self.blocking(move |core| titles(core, &kids)).await?;
        replace(&self.game_night, titles, kids_locked, PREFERRED_PORT).await
    }

    /// Ends the running game and returns to the lobby; `false` when no night runs.
    pub async fn game_night_back(self: Arc<Self>) -> bool {
        back(&self.game_night).await
    }

    /// Ends the night; nothing to do when none runs.
    pub async fn stop_game_night(self: Arc<Self>) {
        stop(&self.game_night).await
    }
}

fn titles(core: &Core, kids: &HashSet<String>) -> Result<Vec<GameTitle>, CoreError> {
    let sets = store::list_sets(core)?;
    let conn = store::open_installed(core, "a game night's credits");
    let credits = |poster_key: &str| {
        let (kind, id) = title_of(poster_key)?;
        crate::credits::for_title(conn.as_ref()?, kind, id).ok()
    };
    Ok(crate::game_titles::game_titles(&sets, &credits, kids))
}

/// Hosts a night in `slot`, ending the one it held first so the port is free.
async fn replace(
    slot: &AsyncMutex<Option<Host>>,
    titles: Vec<GameTitle>,
    kids_locked: bool,
    port: u16,
) -> Result<GameNightUrls, CoreError> {
    let mut held = slot.lock().await;
    if let Some(running) = held.take() {
        running.stop().await;
    }
    let host = mediagram_game::host::start_game_night(titles, kids_locked, port)
        .await
        .map_err(CoreError::io("starting the game night"))?;
    let urls = GameNightUrls { tv_url: host.tv_url(), join_url: host.join_url() };
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
#[path = "game_night_tests.rs"]
mod tests;
