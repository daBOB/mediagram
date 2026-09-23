//! The one request that asks a provider about a title.
//!
//! Three callers want this payload — resolving a title, finding its artwork,
//! and recording what it says — and every one of them depends on hitting the
//! cache `add` wrote rather than the network. The cache key is a hash of the
//! path plus the sorted query, so a copy of this that drifts does not fail:
//! it silently starts making requests, and a machine with no API key starts
//! failing. One copy is the only way that stays true.

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;

use crate::tmdb_client::TmdbApi;
use crate::tmdb_types::DetailsResponse;

/// The provider's record for one title.
pub async fn details(api: &impl TmdbApi, kind: Kind, id: u64) -> Result<DetailsResponse> {
    let path = match kind {
        Kind::Movie => format!("/movie/{id}"),
        Kind::Ep => format!("/tv/{id}"),
        Kind::Tut | Kind::Doc => bail!("a course has no provider entry"),
    };
    let query = [("append_to_response", "external_ids".to_string())];
    let value = api
        .get_json(&path, &query)
        .await
        .with_context(|| format!("asking for {path}"))?;
    serde_json::from_value(value).with_context(|| format!("invalid response for {path}"))
}

/// What one show's entry holds. Every field is optional because TMDB answers
/// for an obscure title with a record that is mostly empty.
#[derive(Debug, Clone, PartialEq)]
pub struct TitleDetailsRow {
    pub kind: Kind,
    pub id: u64,
    pub lang: String,
    pub overview: Option<String>,
    pub tagline: Option<String>,
    /// Comma-separated, in the order TMDB lists them.
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
    pub first_air: Option<String>,
    pub last_air: Option<String>,
    /// What the provider says exists, against which a library can be counted.
    pub total_seasons: Option<u32>,
    pub total_episodes: Option<u32>,
    /// The age rating in the library's country — an FSK in Germany: `0`, `6`,
    /// `12`, `16`, `18`. Not part of the details payload; see
    /// [`crate::certification`], which fills it.
    pub certification: Option<String>,
}

/// Reads a details payload into a row, keeping only what a viewer would read.
pub fn from_details(kind: Kind, lang: &str, details: &DetailsResponse) -> TitleDetailsRow {
    let join = |items: &[crate::tmdb_types::NamedRef]| {
        let joined = items
            .iter()
            .map(|item| item.name.as_str())
            .collect::<Vec<_>>()
            .join(", ");
        (!joined.is_empty()).then_some(joined)
    };
    TitleDetailsRow {
        kind,
        id: details.id,
        lang: lang.to_string(),
        // An empty string is TMDB's way of saying it has no synopsis, and is
        // worth no more than a missing one.
        overview: details.overview.clone().filter(|t| !t.trim().is_empty()),
        tagline: details.tagline.clone().filter(|t| !t.trim().is_empty()),
        genres: join(&details.genres),
        // Zero is what an unrated title scores, which is not a rating.
        rating: details.vote_average.filter(|r| *r > 0.0),
        network: join(&details.networks),
        status: details.status.clone().filter(|t| !t.trim().is_empty()),
        first_air: details
            .first_air_date
            .clone()
            .or_else(|| details.release_date.clone())
            .filter(|t| !t.is_empty()),
        last_air: details.last_air_date.clone().filter(|t| !t.is_empty()),
        // Zero seasons is a record nobody has filled in, not a show with none.
        total_seasons: details.number_of_seasons.filter(|n| *n > 0),
        total_episodes: details.number_of_episodes.filter(|n| *n > 0),
        // A separate request; see `crate::certification`.
        certification: None,
    }
}
