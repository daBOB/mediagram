//! Minimal serde mirrors of the TMDB response shapes `resolve` consumes.

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
    /// Present in every cached details payload; the package export reads it
    /// back rather than re-fetching, so adding the field makes caches that
    /// already exist on disk usable with no network access.
    #[serde(default)]
    pub poster_path: Option<String>,

    // What a provider says about the title rather than about the file. Every
    // one of these is already in the cached payload `add` fetched to resolve
    // the title, so reading them back costs no request and no API key.
    #[serde(default)]
    pub overview: Option<String>,
    #[serde(default)]
    pub tagline: Option<String>,
    #[serde(default)]
    pub genres: Vec<NamedRef>,
    #[serde(default)]
    pub vote_average: Option<f64>,
    #[serde(default)]
    pub networks: Vec<NamedRef>,
    #[serde(default)]
    pub status: Option<String>,
    #[serde(default)]
    pub last_air_date: Option<String>,
    /// How many the provider says exist, which the index cannot know.
    #[serde(default)]
    pub number_of_seasons: Option<u32>,
    #[serde(default)]
    pub number_of_episodes: Option<u32>,
    /// A series' seasons, each with its own artwork. Already in the cached
    /// `/tv/{id}` payload, so season posters cost no request either.
    #[serde(default)]
    pub seasons: Vec<SeasonRef>,
}

/// One entry of a series' `seasons`: its number and its artwork, if any.
#[derive(Debug, Clone, Default, Deserialize)]
pub struct SeasonRef {
    pub season_number: u32,
    #[serde(default)]
    pub poster_path: Option<String>,
}

/// TMDB spells a genre, a network and a company all the same way.
#[derive(Debug, Clone, serde::Deserialize, PartialEq, Eq)]
pub struct NamedRef {
    pub name: String,
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
