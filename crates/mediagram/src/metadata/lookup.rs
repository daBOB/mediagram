//! Single TMDB lookups `resolve` makes once it knows what to ask: a TMDB id
//! behind an IMDb or TVDB id, and an episode's title.

use anyhow::{Context, Result, bail};
use mediagram_tmdb::tmdb_client::TmdbApi;
use mediagram_tmdb::tmdb_types::{EpisodeDetails, FindResponse};
use mlib_spec::Kind;

/// Resolves an external id (`imdb_id`/`tvdb_id`) to a TMDB id via `/find`.
pub(super) async fn find_by_external(api: &impl TmdbApi, id: &str, source: &str, kind: Kind) -> Result<u64> {
    let query = [("external_source", source.to_string())];
    let value = api.get_json(&format!("/find/{id}"), &query).await?;
    let found: FindResponse =
        serde_json::from_value(value).context("invalid tmdb find response")?;
    let hit = match kind {
        Kind::Movie => found.movie_results.into_iter().next(),
        Kind::Ep => found.tv_results.into_iter().next(),
        Kind::Tut | Kind::Doc => {
            bail!("a course has no TMDB entry; courses are described by hand")
        }
    };
    hit.map(|h| h.id)
        .ok_or_else(|| anyhow::anyhow!("no tmdb match found for {source} {id}"))
}

/// Fetches an episode's title. Failures here are non-fatal to the caller.
pub(crate) async fn fetch_episode_title(
    api: &impl TmdbApi,
    show_id: u64,
    season: u32,
    episode: u32,
) -> Result<Option<String>> {
    let path = format!("/tv/{show_id}/season/{season}/episode/{episode}");
    let value = api.get_json(&path, &[]).await?;
    let details: EpisodeDetails =
        serde_json::from_value(value).context("invalid tmdb episode response")?;
    Ok(details.name)
}
