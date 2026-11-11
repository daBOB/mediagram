//! `Core::game_night_titles`/`textless_poster`: what this app hands a games
//! app that hosts the night itself. The library, the credits and the TMDB key
//! stay here; the games app gets the titles as JSON and the posters as files.

use std::collections::HashSet;
use std::sync::Arc;

use crate::dto::{NightTitlesConfig, SetSummary};
use crate::error::CoreError;
use crate::game_posters::{CACHE_DIR, poster_lookup};
use crate::game_titles::{NightTitle, game_titles, poster_keys};
use crate::shows::title_of;

use super::{Core, store};

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The titles a night may show, as a JSON array of [`NightTitle`]s.
    /// The caller decides the kids and section rules; the core only applies them.
    pub async fn game_night_titles(self: Arc<Self>, config: NightTitlesConfig) -> Result<String, CoreError> {
        let NightTitlesConfig { kids_set_ids, visible_set_ids, kids_locked } = config;
        let kids: HashSet<String> = kids_set_ids.into_iter().collect();
        let visible: HashSet<String> = visible_set_ids.into_iter().collect();
        let mut titles = self
            .blocking(move |core| store::list_sets(core).map(|sets| night_titles(core, &sets, &kids, &visible)))
            .await?;
        if kids_locked {
            titles.retain(|t| t.kids_ok);
        }
        serde_json::to_string(&titles).map_err(CoreError::io("writing the night's titles"))
    }

    /// The textless poster of the title `title_key` names, as a file in this
    /// app's cache, fetched from TMDB on a first ask. `None` without a TMDB
    /// key, for a title TMDB has none for, or when it cannot be fetched.
    pub async fn textless_poster(self: Arc<Self>, title_key: String, tmdb_key: Option<String>) -> Option<String> {
        let tmdb_key = tmdb_key.filter(|key| !key.trim().is_empty())?;
        let keys = self
            .blocking(|core| store::list_sets(core).map(|sets| poster_keys(&sets)))
            .await
            .inspect_err(|err| tracing::warn!(error = %err, "textless poster: the catalog could not be read"))
            .ok()?;
        let lookup = poster_lookup(tmdb_key, self.data_dir.join(CACHE_DIR), keys)?;
        lookup(title_key).await?.into_os_string().into_string().ok()
    }
}

/// The night's titles from `sets`, credited from the catalog this device holds.
pub(super) fn night_titles(
    core: &Core,
    sets: &[SetSummary],
    kids: &HashSet<String>,
    visible: &HashSet<String>,
) -> Vec<NightTitle> {
    let conn = store::open_installed(core, "a game night's credits");
    let credits = |poster_key: &str| {
        let (kind, id) = title_of(poster_key)?;
        crate::credits::for_title(conn.as_ref()?, kind, id)
            .inspect_err(|err| tracing::warn!(error = %err, "a title's credits could not be read"))
            .ok()
    };
    game_titles(sets, &credits, kids, visible)
}

#[cfg(test)]
#[path = "game_night_export_tests.rs"]
mod tests;
