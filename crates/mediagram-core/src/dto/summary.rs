//! `SetSummary`, the one record most of the boundary is built from. Split
//! out of `dto.rs` to keep that file under the line limit.

use mlib_spec::caption::Episode;

use crate::catalog::PlayableSet;

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
    /// The key this title's backdrop is stored under, present only when the
    /// file actually exists — `store::list_sets` checks disk, the way the
    /// web player's `routes.ts` checks its poster store before ever naming
    /// one. A series carries its show's, like `genres`.
    pub backdrop_key: Option<String>,
    /// The provider's tagline, for the home page's typographic break. A
    /// series carries its show's, like `genres`.
    pub tagline: Option<String>,
    /// The provider's average rating, for the staff pick. A series carries
    /// its show's, like `genres`.
    pub rating: Option<f64>,
    /// The provider's popularity figure, for the trending feature. Absent
    /// from an index written before it was recorded. A series carries its
    /// show's, like `genres`.
    pub popularity: Option<f64>,
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
        poster_key: poster_key_for(&set.kind, set.tmdb, set.show.as_deref(), set.title.as_deref()),
        total: set.total,
        part_count: set.part_count,
        added_at: set.created_at,
        // Not on the row: the index keeps these per title or per asset, and
        // the listing attaches them — see `store::list_sets`.
        fsk: None,
        genres: Vec::new(),
        subtitles: Vec::new(),
        has_summary: false,
        backdrop_key: None,
        tagline: None,
        rating: None,
        popularity: None,
    }
}

/// Mirrors the web player's `posterKeyFor(kind, tmdb)`. A TMDB id, when there
/// is one, always wins — that art overrides a title's own. A kind this build
/// does not know keys as a series, as it does there — only a film is
/// numbered apart.
///
/// Without a TMDB id, only a course (`Kind::Tut`) or a documentary
/// (`Kind::Docu`) gets a key at all, from its show or its own title's slug: a
/// manually-entered film or episode missing its id is not this stable — two
/// such entries sharing a title would collide onto one key.
fn poster_key_for(kind: &str, tmdb: Option<u64>, show: Option<&str>, title: Option<&str>) -> Option<String> {
    let parsed = kind.parse::<mlib_spec::Kind>().unwrap_or(mlib_spec::Kind::Ep);
    if let Some(id) = tmdb {
        let key = mediagram_tmdb::posters::poster_key(parsed, id);
        debug_assert!(mlib_spec::package::poster_key_is_valid(&key));
        return Some(key);
    }
    if !matches!(parsed, mlib_spec::Kind::Tut | mlib_spec::Kind::Docu) {
        return None;
    }
    let name = show.or(title)?;
    let key = mlib_spec::package::title_art_key(name)?;
    debug_assert!(mlib_spec::package::poster_key_is_valid(&key));
    Some(key)
}
