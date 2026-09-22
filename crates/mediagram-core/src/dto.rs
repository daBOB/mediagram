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
use crate::shows::TitleDetails;

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
    /// When this set arrived, as a Unix time. Named as the web player names
    /// it, because two surfaces over one library should not need a
    /// translation table for the same fact.
    pub added_at: i64,
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
        added_at: set.created_at,
    }
}

/// Mirrors the web player's `posterKeyFor(kind, tmdb)`: `tmdb-movie-<id>` or
/// `tmdb-tv-<id>`, and no key at all without a positive TMDB id.
fn poster_key_for(kind: &str, tmdb: Option<u64>) -> Option<String> {
    let tmdb = tmdb?;
    let sub = if kind == mlib_spec::Kind::Movie.as_str() { "movie" } else { "tv" };
    let key = format!("tmdb-{sub}-{tmdb}");
    debug_assert!(mlib_spec::package::poster_key_is_valid(&key));
    Some(key)
}

/// What a provider said about a title, flattened for the binding surface.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TitleInfo {
    pub overview: Option<String>,
    pub tagline: Option<String>,
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
}

impl From<TitleDetails> for TitleInfo {
    fn from(record: TitleDetails) -> Self {
        TitleInfo {
            overview: record.overview,
            tagline: record.tagline,
            genres: record.genres,
            rating: record.rating,
            network: record.network,
            status: record.status,
        }
    }
}

/// What the installed catalog is, for the System screen's "Catalogue" block:
/// where it came from, how much it holds, and which schema it was written
/// with. `schema` is this build's own `SCHEMA_VERSION`, not a value read out
/// of the database — it says what the reader understands, not what any one
/// file happens to claim.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct CatalogFacts {
    pub origin: String,
    pub sets: u64,
    pub posters: u64,
    pub schema: u32,
    /// Seconds since the epoch when the installed catalogue was pushed,
    /// read from the installed version's own name. `None` when nothing is
    /// installed, or the name cannot be read.
    pub published_at: Option<i64>,
}

/// What one fetch did, for the screen that reports it.
///
/// One run fills both gaps a library can leave — the artwork a channel index
/// cannot carry, and the descriptions nobody ran `mediagram metadata` for —
/// so the counts come in pairs, and the last two are what neither half could
/// do anything about.
///
/// **Every count is a number of titles.** A title is what a shelf shows as
/// one card: a film, or a whole series or course however many episodes or
/// lessons it holds. Every episode of a series shares one provider id, one
/// poster and one description, so a season of eight is one here and not
/// eight — and a course of 162 lessons is one title without a provider
/// entry, so every count on the screen measures the same thing.
///
/// Six counts rather than a verdict, because most of what can happen to a
/// title is not a failure and a viewer reading "0 fetched" needs to know
/// which of them it was.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, uniffi::Record)]
pub struct FetchReport {
    pub posters_fetched: u32,
    pub posters_already_held: u32,
    pub details_recorded: u32,
    /// Titles something already describes — the index's own row, or one an
    /// earlier run on this device fetched. Left alone for the same reason a
    /// poster already held is not downloaded again.
    pub details_already_known: u32,
    /// Titles the provider numbers nothing of, so neither half could be
    /// asked. A course is one of these, not a failure.
    pub no_provider_id: u32,
    /// Titles this run could not finish: the provider would not describe
    /// them, or their artwork would not download. One title that lost both
    /// is counted once, because these are titles.
    pub failed: u32,
}
