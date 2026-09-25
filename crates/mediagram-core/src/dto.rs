//! The records Kotlin receives. A set's episode arrives as the index's JSON
//! text; [`summary_from`] hands the boundary two plain numbers instead.

use mlib_spec::caption::Episode;

use crate::catalog::PlayableSet;
use mediagram_tmdb::details::TitleDetailsRow;

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
    /// The age rating in the library's country (`"12"`), or `None` when the
    /// title has none. A series is rated as a show, so every episode carries
    /// its show's. Named as the web player names it.
    pub fsk: Option<String>,
    /// The provider's genres for this title. A series carries its show's,
    /// the way `fsk` does — see `store::list_sets`, which attaches all four
    /// of these by poster key rather than storing them on the row.
    pub genres: Vec<String>,
    /// Languages this set has a subtitle track for, sorted.
    pub subtitles: Vec<String>,
    /// Whether the index holds a plot summary for this set.
    pub has_summary: bool,
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
        // Not on the row: the index keeps these per title or per asset, and
        // the listing attaches them — see `store::list_sets`.
        fsk: None,
        genres: Vec::new(),
        subtitles: Vec::new(),
        has_summary: false,
    }
}

/// Mirrors the web player's `posterKeyFor(kind, tmdb)`: no key at all
/// without a positive TMDB id. A kind this build does not know keys as a
/// series, as it does there — only a film is numbered apart.
fn poster_key_for(kind: &str, tmdb: Option<u64>) -> Option<String> {
    let kind = kind.parse().unwrap_or(mlib_spec::Kind::Ep);
    let key = mediagram_tmdb::posters::poster_key(kind, tmdb?);
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

impl From<TitleDetailsRow> for TitleInfo {
    fn from(record: TitleDetailsRow) -> Self {
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
    /// Where the installed catalogue came from: `"channel"` or `"package"`,
    /// or empty when none is installed or its record cannot be read.
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
/// Every count is a number of titles — what a shelf shows as one card, so a
/// series or a course is one however many episodes it holds. Six counts
/// rather than a verdict, because most of what happens to a title is not a
/// failure and "0 fetched" needs to say which it was.
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

/// One library the signed-in account could choose, as the caller sees it.
///
/// A title to render and a handle to send back, and nothing else. The handle
/// is a random name this data directory minted for the channel — see
/// `api::channel::library` — so a caller holding one learns nothing about where the
/// bytes live, which is the same rule the byte path is held to.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LibraryChoice {
    pub handle: String,
    pub title: String,
}

/// Who the signed-in account is, for a screen that shows the connection.
/// Never the phone number: nothing here needs it, so nothing carries it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct AccountSummary {
    pub name: String,
    pub username: Option<String>,
}

/// Outcome of a completed sign-in step.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AuthOutcome {
    Done,
    PasswordNeeded,
}
