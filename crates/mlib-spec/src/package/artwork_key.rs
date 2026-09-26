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
///
/// A title with no provider id — a documentary, or a course used as a
/// tutorial's cover — is keyed `title-{slug}` instead, with the same `-bg`
/// suffix for its backdrop; see [`title_art_key`].
pub fn poster_key_is_valid(key: &str) -> bool {
    if let Some(rest) = key.strip_prefix("title-") {
        let mut parts: Vec<&str> = rest.split('-').collect();
        // `bg` may appear at most once, and only as the trailing segment —
        // never twice, and never in the slug itself. A title literally
        // slugging to a segment named `bg` collides with the backdrop
        // marker; accepted as a known, narrow limitation rather than
        // designing a second delimiter for a case this unlikely.
        if parts.iter().filter(|p| **p == BACKDROP_SUFFIX).count() > 1 {
            return false;
        }
        if parts.last() == Some(&BACKDROP_SUFFIX) {
            parts.pop();
        } else if parts.contains(&BACKDROP_SUFFIX) {
            return false;
        }
        return is_slug(&parts.join("-"));
    }
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

/// Whether `s` is exactly what [`crate::slug::slug`] would produce: nonempty,
/// lowercase alphanumeric segments joined by single dashes, no leading,
/// trailing or doubled one.
fn is_slug(s: &str) -> bool {
    !s.is_empty()
        && !s.starts_with('-')
        && !s.ends_with('-')
        && s.split('-')
            .all(|part| !part.is_empty() && part.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit()))
}

/// The key a title with no provider id is stored under: `title-{slug}`, the
/// slug taken from its show or course name — the same derivation
/// `add-course` uses for its default collection id ([`crate::slug::slug`]),
/// so a documentary's title and a course's title land on the key a person
/// familiar with either would expect.
///
/// `None` when the name slugs to nothing: a title of only punctuation, or
/// only a script the slug drops.
#[must_use]
pub fn title_art_key(name: &str) -> Option<String> {
    let slug = crate::slug::slug(name);
    (!slug.is_empty()).then(|| format!("title-{slug}"))
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
