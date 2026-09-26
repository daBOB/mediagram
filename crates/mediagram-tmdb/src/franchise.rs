//! A film's franchise — TMDB's "collection", the thing eleven `Star Trek`
//! films share one id for.
//!
//! Its own request, separately cached, for the reason `certification` gives
//! for its own: appending this to the details payload would turn a cache
//! full of details entries into a network round trip. `belongs_to_collection`
//! on the details payload already gives an id and a name; only the overview
//! lives here, at `/collection/{id}`, asked once per collection rather than
//! once per film in it.

use anyhow::{Context, Result};
use serde::Deserialize;

use crate::tmdb_client::TmdbApi;

/// One franchise: TMDB's id, its name, and its overview when it has one.
#[derive(Debug, Clone, PartialEq)]
pub struct Franchise {
    pub id: u64,
    pub name: String,
    pub overview: Option<String>,
}

#[derive(Debug, Deserialize)]
struct CollectionResponse {
    id: u64,
    name: String,
    #[serde(default)]
    overview: Option<String>,
}

/// The franchise `id` names.
pub async fn franchise(api: &impl TmdbApi, id: u64) -> Result<Franchise> {
    let path = format!("/collection/{id}");
    let value = api
        .get_json(&path, &[])
        .await
        .with_context(|| format!("asking for {path}"))?;
    let payload: CollectionResponse =
        serde_json::from_value(value).with_context(|| format!("invalid response for {path}"))?;
    Ok(Franchise {
        id: payload.id,
        name: payload.name,
        // An empty string is TMDB's way of saying it wrote no overview, and
        // is worth no more than a missing one — the same reading
        // `details::from_details` gives a title's own overview.
        overview: payload.overview.filter(|t| !t.trim().is_empty()),
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::tmdb_client::TmdbApi;
    use serde_json::{Value, json};

    struct Stub(Value);

    impl TmdbApi for Stub {
        async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
            Ok(self.0.clone())
        }
    }

    #[tokio::test]
    async fn a_collection_carries_its_name_and_overview() {
        let stub = Stub(json!({
            "id": 115, "name": "Star Trek: The Original Series Collection",
            "overview": "The films that started it all."
        }));
        let found = franchise(&stub, 115).await.unwrap();
        assert_eq!(found.id, 115);
        assert_eq!(found.name, "Star Trek: The Original Series Collection");
        assert_eq!(
            found.overview.as_deref(),
            Some("The films that started it all.")
        );
    }

    #[tokio::test]
    async fn an_empty_overview_is_none() {
        let stub = Stub(json!({"id": 1, "name": "A Collection", "overview": "  "}));
        assert_eq!(franchise(&stub, 1).await.unwrap().overview, None);
    }

    #[tokio::test]
    async fn a_missing_overview_is_none() {
        let stub = Stub(json!({"id": 1, "name": "A Collection"}));
        assert_eq!(franchise(&stub, 1).await.unwrap().overview, None);
    }
}
