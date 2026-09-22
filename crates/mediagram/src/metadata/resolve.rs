//! Turns a file name plus optional explicit ids into a `ResolvedItem`,
//! preferring explicit ids, then a TMDB search seeded by the filename guess,
//! prompting only when the search is ambiguous. `--manual` bypasses TMDB.

use anyhow::{Context, Result, bail};
use mediagram_tmdb::tmdb_client::TmdbApi;
use mediagram_tmdb::tmdb_types::{DetailsResponse, EpisodeDetails, FindResponse};
use mlib_spec::filename::{Guess, parse_filename};
use mlib_spec::ids::normalize_imdb;
use mlib_spec::{Episode, Kind, ProviderIds};

use super::prompt::Prompter;
use super::search::search_and_resolve;

/// Everything `resolve` needs beyond the file name; mirrors `AddArgs`
/// without depending on the CLI or config types.
#[derive(Debug, Clone, Default)]
pub struct ResolveInput {
    pub file_name: String,
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    pub imdb: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    pub abs: Option<u32>,
    pub manual: bool,
}

/// Metadata resolved for one file, ready to seed a `Caption`.
#[derive(Debug, Clone, PartialEq)]
pub struct ResolvedItem {
    pub kind: Kind,
    pub ids: ProviderIds,
    /// Movie title, or episode title for `Kind::Ep`.
    pub title: Option<String>,
    pub show: Option<String>,
    pub year: Option<u16>,
    pub season: Option<u32>,
    pub episode: Option<Episode>,
    pub abs: Option<u32>,
}

/// Resolve one file: explicit id > filename-guess search > interactive
/// prompt, or `--manual` to bypass TMDB entirely.
pub async fn resolve(
    api: &impl TmdbApi,
    input: &ResolveInput,
    ui: &mut dyn Prompter,
) -> Result<ResolvedItem> {
    let guess = parse_filename(&input.file_name).unwrap_or_else(|| Guess {
        title: input.file_name.clone(),
        ..Guess::default()
    });

    if input.manual {
        return ui.manual_entry(&guess);
    }

    let kind = determine_kind(input, &guess);

    let mut item = if let Some(id) = input.tmdb {
        fetch_details(api, id, kind).await?
    } else if let Some(imdb) = &input.imdb {
        let imdb =
            normalize_imdb(imdb).ok_or_else(|| anyhow::anyhow!("invalid imdb id `{imdb}`"))?;
        let id = find_by_external(api, &imdb, "imdb_id", kind).await?;
        fetch_details(api, id, kind).await?
    } else if let Some(tvdb) = input.tvdb {
        let id = find_by_external(api, &tvdb.to_string(), "tvdb_id", kind).await?;
        fetch_details(api, id, kind).await?
    } else {
        search_and_resolve(api, &guess, kind, ui).await?
    };

    // The flag is authoritative: v1 never queries TVDB directly, so whatever
    // the caller supplied wins over anything TMDB's external_ids returned.
    if let Some(tvdb) = input.tvdb {
        item.ids.tvdb = Some(tvdb);
    }
    if let Some(imdb) = input.imdb.as_deref().and_then(normalize_imdb) {
        item.ids.imdb = Some(imdb);
    }
    item.season = input.season.or(guess.season);
    item.episode = episode_value(input, &guess);
    item.abs = input.abs.or(guess.abs);

    if kind == Kind::Ep {
        if let (Some(show_id), Some(season), Some(episode)) =
            (item.ids.tmdb, item.season, item.episode)
        {
            if let Ok(Some(title)) =
                fetch_episode_title(api, show_id, season, episode.first()).await
            {
                item.title = Some(title);
            }
        }
    }

    Ok(item)
}

/// Movie unless an episode marker was given explicitly or parsed from the name.
fn determine_kind(input: &ResolveInput, guess: &Guess) -> Kind {
    if input.season.is_some()
        || input.episode.is_some()
        || input.abs.is_some()
        || guess.is_episode()
    {
        Kind::Ep
    } else {
        Kind::Movie
    }
}

/// Explicit `--episode` wins over the filename guess; a guessed end episode
/// only applies when the flag didn't override the start episode.
fn episode_value(input: &ResolveInput, guess: &Guess) -> Option<Episode> {
    let first = input.episode.or(guess.episode)?;
    match guess.episode_end {
        Some(end) if input.episode.is_none() => Some(Episode::Range([first, end])),
        _ => Some(Episode::Single(first)),
    }
}

/// Fetches `/movie/{id}` or `/tv/{id}` with `external_ids` appended.
pub(super) async fn fetch_details(api: &impl TmdbApi, id: u64, kind: Kind) -> Result<ResolvedItem> {
    let path = match kind {
        Kind::Movie => format!("/movie/{id}"),
        Kind::Ep => format!("/tv/{id}"),
        Kind::Tut | Kind::Doc => {
            bail!("a course has no TMDB entry; courses are described by hand")
        }
    };
    let query = [("append_to_response", "external_ids".to_string())];
    let value = api.get_json(&path, &query).await?;
    let details: DetailsResponse = serde_json::from_value(value)
        .with_context(|| format!("invalid tmdb response for {path}"))?;
    let ext = details.external_ids.clone().unwrap_or_default();

    if matches!(kind, Kind::Tut | Kind::Doc) {
        bail!("a course has no TMDB entry; courses are described by hand");
    }
    Ok(match kind {
        // Guarded immediately above; a course never reaches TMDB.
        Kind::Tut | Kind::Doc => bail!("a course has no TMDB entry"),
        Kind::Movie => ResolvedItem {
            kind,
            ids: ProviderIds {
                tmdb: Some(id),
                tvdb: None,
                imdb: ext.imdb_id,
            },
            title: details.display_title(),
            show: None,
            year: details.year(),
            season: None,
            episode: None,
            abs: None,
        },
        Kind::Ep => ResolvedItem {
            kind,
            ids: ProviderIds {
                tmdb: Some(id),
                tvdb: ext.tvdb_id,
                imdb: ext.imdb_id,
            },
            title: None,
            show: details.display_title(),
            year: details.year(),
            season: None,
            episode: None,
            abs: None,
        },
    })
}

/// Resolves an external id (`imdb_id`/`tvdb_id`) to a TMDB id via `/find`.
async fn find_by_external(api: &impl TmdbApi, id: &str, source: &str, kind: Kind) -> Result<u64> {
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

/// Metadata for a course lesson. No lookup: TMDB has no courses, so
/// everything comes from what the caller passed and from the file name.
pub fn lesson(course: &str, input: &ResolveInput) -> ResolvedItem {
    let (_, title) =
        crate::course::plan::split_number_and_title(crate::course::plan::stem(&input.file_name));
    ResolvedItem {
        kind: Kind::Tut,
        ids: ProviderIds::default(),
        show: Some(course.to_string()),
        title,
        year: None,
        season: input.season.or(Some(1)),
        episode: input.episode.map(Episode::Single),
        abs: input.abs,
    }
}
