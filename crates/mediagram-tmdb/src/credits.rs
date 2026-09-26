//! Who is credited on a title — cast, director(s), and (for a series) its
//! creators — as opposed to what the title is about.
//!
//! Its own request, deliberately not appended to the details one: the same
//! reason `certification` gives for its own — the cache key is the path plus
//! the query, and every cached details payload predates this, so appending it
//! there would turn a whole library's cache into a miss. Films and series
//! keep a director differently: a film's is crew on `/credits`; a series'
//! creators are `created_by` on the details payload itself, not on
//! `/tv/{id}/credits` at all, so this asks for details too — cached already,
//! so no second request.

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;
use serde::Deserialize;

use crate::tmdb_client::TmdbApi;

/// One name credited on a title. `ord` is the position this title lists it
/// in — cast by billing order, then its director(s), then (a series) its
/// creators — and is part of `credits`' primary key, so every row of one
/// title needs its own.
#[derive(Debug, Clone, PartialEq)]
pub struct CreditRow {
    pub person_id: u64,
    pub name: String,
    /// The character played (`dept = "cast"`), or the fixed label
    /// `"Director"`/`"Creator"` (`dept = "crew"`).
    pub role: Option<String>,
    pub dept: String,
    pub ord: u32,
    pub profile_path: Option<String>,
}

/// How many of the top-billed cast are kept — enough to name a title's draw,
/// not so many the table rivals the credits themselves.
const CAST_LIMIT: usize = 12;

/// The credits `crates/mediagram-core/src/credits.rs` writes: the top 12 cast
/// by billing order, the director(s), and — for a series — its creators.
pub async fn credits(api: &impl TmdbApi, kind: Kind, id: u64) -> Result<Vec<CreditRow>> {
    let path = match kind {
        Kind::Movie => format!("/movie/{id}/credits"),
        Kind::Ep => format!("/tv/{id}/credits"),
        Kind::Tut | Kind::Doc | Kind::Docu => bail!("a course has no provider entry"),
    };
    let value = api
        .get_json(&path, &[])
        .await
        .with_context(|| format!("asking for {path}"))?;
    let payload: CreditsResponse =
        serde_json::from_value(value).with_context(|| format!("invalid response for {path}"))?;

    let mut ord = 0u32;
    let mut rows = Vec::new();

    let mut cast = payload.cast;
    cast.sort_by_key(|member| member.order);
    for member in cast.into_iter().take(CAST_LIMIT) {
        rows.push(CreditRow {
            person_id: member.id,
            name: member.name,
            role: member.character.filter(|c| !c.trim().is_empty()),
            dept: "cast".to_string(),
            ord,
            profile_path: member.profile_path,
        });
        ord += 1;
    }

    for director in payload
        .crew
        .into_iter()
        .filter(|member| member.job.as_deref() == Some("Director"))
    {
        rows.push(CreditRow {
            person_id: director.id,
            name: director.name,
            role: Some("Director".to_string()),
            dept: "crew".to_string(),
            ord,
            profile_path: director.profile_path,
        });
        ord += 1;
    }

    // Only a series has creators, and only the details payload carries them
    // — already fetched to resolve the title, so this costs the disk cache,
    // not the network.
    if kind == Kind::Ep {
        let details = crate::details::details(api, kind, id)
            .await
            .with_context(|| format!("asking for /tv/{id} to find its creators"))?;
        for creator in details.created_by {
            let Some(name) = creator.name.filter(|n| !n.trim().is_empty()) else {
                continue;
            };
            rows.push(CreditRow {
                person_id: creator.id,
                name,
                role: Some("Creator".to_string()),
                dept: "crew".to_string(),
                ord,
                profile_path: creator.profile_path,
            });
            ord += 1;
        }
    }

    Ok(rows)
}

#[derive(Debug, Default, Deserialize)]
struct CreditsResponse {
    #[serde(default)]
    cast: Vec<CastMember>,
    #[serde(default)]
    crew: Vec<CrewMember>,
}

#[derive(Debug, Deserialize)]
struct CastMember {
    id: u64,
    name: String,
    #[serde(default)]
    character: Option<String>,
    #[serde(default)]
    order: i64,
    #[serde(default)]
    profile_path: Option<String>,
}

#[derive(Debug, Deserialize)]
struct CrewMember {
    id: u64,
    name: String,
    #[serde(default)]
    job: Option<String>,
    #[serde(default)]
    profile_path: Option<String>,
}

#[cfg(test)]
#[path = "credits_tests.rs"]
mod tests;
