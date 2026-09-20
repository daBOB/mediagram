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

use super::tmdb_client::TmdbApi;
use super::tmdb_types::DetailsResponse;

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
