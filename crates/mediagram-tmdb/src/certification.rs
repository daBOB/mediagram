//! A title's age rating in one country — in Germany, its FSK.
//!
//! Its own request, deliberately not appended to the details one: the cache
//! key is the path plus the query, so appending `release_dates` there would
//! turn every details payload already cached into a miss and send a whole
//! library back to the network. Asked separately, it is cached separately,
//! and a library that predates it costs one request per title, once.
//!
//! Films and series keep the rating in different places: a film per release
//! (`/movie/{id}/release_dates`), a series once (`/tv/{id}/content_ratings`).

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;
use serde_json::Value;

use crate::tmdb_client::TmdbApi;

/// The rating `region` gives the title, or `None` when it gives none.
///
/// `region` is an ISO 3166-1 code such as `DE`; see [`region_of`].
pub async fn certification(api: &impl TmdbApi, kind: Kind, id: u64, region: &str) -> Result<Option<String>> {
    let path = match kind {
        Kind::Movie => format!("/movie/{id}/release_dates"),
        Kind::Ep => format!("/tv/{id}/content_ratings"),
        Kind::Tut | Kind::Doc => bail!("a course has no provider entry"),
    };
    let value = api.get_json(&path, &[]).await.with_context(|| format!("asking for {path}"))?;
    Ok(match kind {
        Kind::Movie => film_rating(&value, region),
        _ => series_rating(&value, region),
    })
}

/// The country whose ratings go with a TMDB language: `de-DE` → `DE`.
///
/// A bare language (`de`) is taken as its own country, which is right for the
/// languages a library is likely to be kept in and harmless for the rest: a
/// country TMDB has no ratings for answers `None`, as an unrated title does.
pub fn region_of(language: &str) -> String {
    language.rsplit(['-', '_']).next().unwrap_or(language).to_ascii_uppercase()
}

/// The first rating any of the film's releases in `region` carries. Releases
/// in one country share one rating in practice; TMDB lists an empty string
/// for a release nobody rated, which is no rating.
fn film_rating(value: &Value, region: &str) -> Option<String> {
    in_region(value, region)?
        .get("release_dates")?
        .as_array()?
        .iter()
        .filter_map(|release| release.get("certification")?.as_str())
        .map(str::trim)
        .find(|rating| !rating.is_empty())
        .map(str::to_string)
}

fn series_rating(value: &Value, region: &str) -> Option<String> {
    let rating = in_region(value, region)?.get("rating")?.as_str()?.trim();
    (!rating.is_empty()).then(|| rating.to_string())
}

fn in_region<'a>(value: &'a Value, region: &str) -> Option<&'a Value> {
    value
        .get("results")?
        .as_array()?
        .iter()
        .find(|entry| entry.get("iso_3166_1").and_then(Value::as_str) == Some(region))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn a_film_is_rated_by_its_first_rated_release_in_the_country() {
        let value = json!({"id": 603, "results": [
            {"iso_3166_1": "US", "release_dates": [{"certification": "R", "type": 3}]},
            {"iso_3166_1": "DE", "release_dates": [
                {"certification": "", "type": 1},
                {"certification": "16", "type": 3}
            ]}
        ]});
        assert_eq!(film_rating(&value, "DE").as_deref(), Some("16"));
        assert_eq!(film_rating(&value, "US").as_deref(), Some("R"));
    }

    #[test]
    fn a_country_with_no_rated_release_gives_none() {
        let value = json!({"results": [{"iso_3166_1": "DE", "release_dates": [{"certification": " "}]}]});
        assert_eq!(film_rating(&value, "DE"), None);
        assert_eq!(film_rating(&value, "FR"), None);
    }

    #[test]
    fn a_series_is_rated_once_per_country() {
        let value = json!({"results": [
            {"iso_3166_1": "DE", "rating": "12"},
            {"iso_3166_1": "US", "rating": "TV-14"}
        ]});
        assert_eq!(series_rating(&value, "DE").as_deref(), Some("12"));
        assert_eq!(series_rating(&json!({"results": []}), "DE"), None);
    }

    #[test]
    fn the_country_comes_from_the_language() {
        assert_eq!(region_of("de-DE"), "DE");
        assert_eq!(region_of("en_US"), "US");
        assert_eq!(region_of("de"), "DE");
    }
}
