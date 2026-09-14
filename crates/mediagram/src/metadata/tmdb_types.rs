//! Minimal serde mirrors of the TMDB response shapes `resolve` consumes.
#![allow(dead_code)] // Some fields exist for shape-completeness; not all are read yet.

use serde::Deserialize;

/// One entry from a `/search/movie`, `/search/tv`, or `/find` response.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct SearchHit {
    pub id: u64,
    /// Present for movies.
    #[serde(default)]
    pub title: Option<String>,
    /// Present for TV shows.
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub release_date: Option<String>,
    #[serde(default)]
    pub first_air_date: Option<String>,
}

impl SearchHit {
    /// Movie title or show name, whichever the payload carried.
    pub fn display_title(&self) -> String {
        self.title
            .clone()
            .or_else(|| self.name.clone())
            .unwrap_or_default()
    }

    /// Year parsed from `release_date` (movies) or `first_air_date` (TV).
    pub fn year(&self) -> Option<u16> {
        self.release_date
            .as_deref()
            .or(self.first_air_date.as_deref())
            .and_then(|d| d.get(0..4))
            .and_then(|y| y.parse().ok())
    }
}

/// `/search/movie` and `/search/tv` share this envelope.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct SearchResponse {
    #[serde(default)]
    pub results: Vec<SearchHit>,
}

/// `/find/{external_id}` groups hits by media type; only movie and TV
/// results are relevant to v1.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct FindResponse {
    #[serde(default)]
    pub movie_results: Vec<SearchHit>,
    #[serde(default)]
    pub tv_results: Vec<SearchHit>,
}

/// The `external_ids` block appended to `/movie/{id}` and `/tv/{id}` via
/// `append_to_response=external_ids`.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct ExternalIds {
    #[serde(default)]
    pub imdb_id: Option<String>,
    /// Only present on `/tv/{id}` responses; TMDB has no TVDB link for movies.
    #[serde(default)]
    pub tvdb_id: Option<u64>,
}

/// `/movie/{id}` or `/tv/{id}`, requested with `append_to_response=external_ids`.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct DetailsResponse {
    pub id: u64,
    #[serde(default)]
    pub title: Option<String>,
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub release_date: Option<String>,
    #[serde(default)]
    pub first_air_date: Option<String>,
    #[serde(default)]
    pub external_ids: Option<ExternalIds>,
}

impl DetailsResponse {
    /// Movie title or show name, whichever the payload carried.
    pub fn display_title(&self) -> Option<String> {
        self.title.clone().or_else(|| self.name.clone())
    }

    /// Year parsed from `release_date` (movies) or `first_air_date` (TV).
    pub fn year(&self) -> Option<u16> {
        self.release_date
            .as_deref()
            .or(self.first_air_date.as_deref())
            .and_then(|d| d.get(0..4))
            .and_then(|y| y.parse().ok())
    }
}

/// `/tv/{id}/season/{s}/episode/{e}`.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct EpisodeDetails {
    #[serde(default)]
    pub name: Option<String>,
}
