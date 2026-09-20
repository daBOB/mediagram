//! Flattened set metadata for the binding surface.
//!
//! `catalog::PlayableSet` stores a title's episode number as JSON text — the
//! wire encoding of `mlib_spec::caption::Episode` — because that is how the
//! `sets` table carries it. A UniFFI record cannot hold that enum directly
//! (nor should it: `Episode` is an implementation detail of this crate's own
//! schema), so [`summary_from`] parses it here and hands the boundary two
//! plain numbers instead.

use mlib_spec::caption::Episode;

use crate::catalog::PlayableSet;
use crate::shows::ShowRecord;

/// One title, flattened for a player that never sees `Episode`, `set_id`
/// internals, or where the bytes live.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SetSummary {
    pub set_id: String,
    pub kind: String,
    pub title: Option<String>,
    pub show: Option<String>,
    pub chap: Option<String>,
    /// The folder trail inside the collection, `a/b/c` from the top down.
    /// A surface rebuilds a course's tree by splitting it; a set with none
    /// is shelved under its chapter or its season instead.
    pub path: Option<String>,
    pub season: Option<u32>,
    pub episode_first: Option<u32>,
    pub episode_last: Option<u32>,
    pub year: Option<u32>,
    pub container: String,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    pub quality: Option<String>,
    pub hdr: Option<String>,
    pub duration: Option<u32>,
    pub poster_key: Option<String>,
    pub total: u64,
    pub part_count: u32,
}

/// Flattens one catalog row. Never fails: a set whose episode field this
/// build cannot parse — written by a newer uploader, or corrupted — still
/// lists, just without episode numbers, because a set that cannot be
/// numbered is still a set worth offering.
pub fn summary_from(set: &PlayableSet) -> SetSummary {
    let (episode_first, episode_last) = set
        .episode
        .as_deref()
        .and_then(|json| serde_json::from_str::<Episode>(json).ok())
        .map_or((None, None), |e| (Some(e.first()), Some(e.last())));

    SetSummary {
        set_id: set.set_id.clone(),
        kind: set.kind.clone(),
        title: set.title.clone(),
        show: set.show.clone(),
        chap: set.chap.clone(),
        path: set.path.clone(),
        season: set.season,
        episode_first,
        episode_last,
        year: set.year.map(u32::from),
        container: set.container.clone(),
        vcodec: set.vcodec.clone(),
        acodec: set.acodec.clone(),
        quality: set.quality.clone(),
        hdr: set.hdr.clone(),
        duration: set.duration,
        poster_key: poster_key_for(&set.kind, set.tmdb),
        total: set.total,
        part_count: set.part_count,
    }
}

/// Mirrors the web player's `posterKeyFor(kind, tmdb)`: `tmdb-movie-<id>` or
/// `tmdb-tv-<id>`, and no key at all without a positive TMDB id.
fn poster_key_for(kind: &str, tmdb: Option<i64>) -> Option<String> {
    let tmdb = tmdb.filter(|id| *id > 0)?;
    let sub = if kind == "movie" { "movie" } else { "tv" };
    let key = format!("tmdb-{sub}-{tmdb}");
    debug_assert!(mlib_spec::package::poster_key_is_valid(&key));
    Some(key)
}

/// What a provider said about a title, flattened for the binding surface.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ShowInfo {
    pub overview: Option<String>,
    pub tagline: Option<String>,
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
}

impl From<ShowRecord> for ShowInfo {
    fn from(record: ShowRecord) -> Self {
        ShowInfo {
            overview: record.overview,
            tagline: record.tagline,
            genres: record.genres,
            rating: record.rating,
            network: record.network,
            status: record.status,
        }
    }
}
