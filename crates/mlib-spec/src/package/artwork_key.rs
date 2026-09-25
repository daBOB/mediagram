//! The keys artwork is stored under: a poster, a season's poster, and a
//! backdrop. Each becomes a file name, a manifest path and a tar member name,
//! so the rules live in one place that every writer and reader checks.

use super::charset::{is_digits, is_lower_alpha};

/// A poster key reaches a file name, a manifest path and a tar member name,
/// so it is restricted to `source-kind-digits` before it touches any path.
///
/// A season's artwork adds one more part, `s<digits>` — `tmdb-tv-1396-s2` —
/// so it sits beside its show's poster under the same rules and is never
/// mistaken for a different show: the show's own key has no fourth part.
///
/// A title's backdrop is the same key with the literal `bg` as that fourth
/// part — `tmdb-movie-550-bg`. Seasons have no backdrops of their own.
pub fn poster_key_is_valid(key: &str) -> bool {
    let mut parts = key.split('-');
    let (Some(source), Some(kind), Some(id)) = (parts.next(), parts.next(), parts.next()) else {
        return false;
    };
    let season_ok = match (parts.next(), parts.next()) {
        (None, _) => true,
        (Some(BACKDROP_SUFFIX), None) => true,
        (Some(season), None) => season.strip_prefix('s').is_some_and(is_digits),
        _ => false,
    };
    is_lower_alpha(source) && is_lower_alpha(kind) && is_digits(id) && season_ok
}

/// The fourth key part that marks a title's backdrop rather than its poster.
pub const BACKDROP_SUFFIX: &str = "bg";

/// The key a title's backdrop is stored under, beside its poster's `key`.
#[must_use]
pub fn backdrop_key(key: &str) -> String {
    format!("{key}-{BACKDROP_SUFFIX}")
}

/// Whether `key` names a backdrop rather than a poster.
#[must_use]
pub fn is_backdrop_key(key: &str) -> bool {
    key.strip_suffix(BACKDROP_SUFFIX)
        .is_some_and(|rest| rest.ends_with('-'))
}

/// The key a season's artwork is stored under, beside its show's `show_key`.
#[must_use]
pub fn season_poster_key(show_key: &str, season: u32) -> String {
    format!("{show_key}-s{season}")
}
