//! `SetSummary`, the one record most of the boundary is built from. Split
//! out of `dto.rs` to keep that file under the line limit.

use mlib_spec::caption::Episode;

use super::SubtitleTrack;
use crate::catalog::PlayableSet;

mod poster_key;
use poster_key::poster_key_for;

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
    /// Where [`poster_key`](Self::poster_key)'s file actually sits. Every
    /// set carries its own answer, but a listing works out any one key's
    /// answer at most once, however many sets share it (many episodes of a
    /// show share its poster key) — see `store::list_sets`. `None` when
    /// there is no key, or the key names no file this device holds yet.
    pub poster_path: Option<String>,
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
    /// Subtitle tracks this set offers, from its bundle once it has one, or
    /// its inline rows until then. See `catalog_subtitles::tracks_by_set`.
    pub subtitles: Vec<SubtitleTrack>,
    /// This set's own audio languages, from the file's tracks.
    pub alang: Vec<String>,
    /// This set's own subtitle languages, from the file's tracks — distinct
    /// from `subtitles`, which is what the uploader extracted as a track.
    pub slang: Vec<String>,
    /// Whether the index holds a plot summary for this set.
    pub has_summary: bool,
    /// Where this title's backdrop is on disk, present when the file already
    /// exists or `store::list_sets` can materialise one from the index's
    /// `artwork` table — the same rule `poster_path` is held to, and the
    /// same parity with the web player's own `has()`, which counts a
    /// backdrop the table alone carries exactly as it does a poster. A
    /// series carries its show's, like `genres`.
    pub backdrop_path: Option<String>,
    /// Where this episode's season carries its own poster, present only for
    /// an episode whose season has one — resolved and materialised the same
    /// way `poster_path` is, `None` for anything that is not an episode.
    pub season_poster_path: Option<String>,
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
    /// What TMDB currently says this title's status is (`Ended`, `Returning
    /// Series`, `Released`, …), for the series page's status line. Absent
    /// from an index written before v9 recorded it. A series carries its
    /// show's, like `genres`.
    pub show_status: Option<String>,
    /// The franchise (TMDB "collection") a film belongs to. Absent for a
    /// series, and from an index written before v9. A series carries its
    /// show's, like `genres` — though a series is never itself in one.
    pub collection_id: Option<u64>,
    pub collection_name: Option<String>,
    /// What TMDB calls a series: `Scripted`, `Miniseries`, … Absent for a
    /// film, and from an index written before v9.
    pub series_type: Option<String>,
    /// Whether this title is shelved in Anime; see `shows::is_anime`. A
    /// series carries its show's, like `genres`.
    pub anime: bool,
    /// The hand-set category of the unit this set belongs to — a course, a
    /// documentary collection, or a standalone documentary; see
    /// `mlib_spec::category_key::category_key`. `None` for a film or an
    /// episode, for an unfiled unit, or for an index written before v12.
    pub category: Option<String>,
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
        poster_key: poster_key_for(
            &set.kind,
            set.tmdb,
            set.show.as_deref(),
            set.title.as_deref(),
        ),
        // Resolved by `store::list_sets`/`store::media_set`, not here: doing
        // it in this flattening step would mean a filesystem check per set
        // even for a caller that never enriches the result at all.
        poster_path: None,
        total: set.total,
        part_count: set.part_count,
        added_at: set.created_at,
        // Not on the row: the index keeps these per title or per asset, and
        // the listing attaches them — see `store::list_sets`.
        fsk: None,
        genres: Vec::new(),
        subtitles: Vec::new(),
        alang: set.alang.clone(),
        slang: set.slang.clone(),
        has_summary: false,
        backdrop_path: None,
        season_poster_path: None,
        tagline: None,
        rating: None,
        popularity: None,
        show_status: None,
        collection_id: None,
        collection_name: None,
        series_type: None,
        // Resolved by `store::editorial::enrich`, not here: it needs the
        // index's genres, original language and overrides, none of which
        // this flattening step reads.
        anime: false,
        // Resolved by `store::editorial::enrich`, not here: it needs the
        // index's own `categories` table, which this flattening step never
        // opens.
        category: None,
    }
}

#[cfg(test)]
#[path = "summary_tests.rs"]
mod tests;
