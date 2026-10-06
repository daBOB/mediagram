//! The poster key a set summary carries, derived from the row rather than
//! stored. Split out of `summary.rs` to keep it under the line limit.

/// Mirrors the web player's `posterKeyFor(kind, tmdb)`. A TMDB id, when there
/// is one, always wins — that art overrides a title's own. A kind this build
/// does not know keys as a series, as it does there — only a film is
/// numbered apart.
///
/// Without a TMDB id, only a course (`Kind::Tut`) or a documentary
/// (`Kind::Docu`) gets a key at all, from its show or its own title's slug: a
/// manually-entered film or episode missing its id is not this stable — two
/// such entries sharing a title would collide onto one key.
pub(super) fn poster_key_for(
    kind: &str,
    tmdb: Option<u64>,
    show: Option<&str>,
    title: Option<&str>,
) -> Option<String> {
    let parsed = kind
        .parse::<mlib_spec::Kind>()
        .unwrap_or(mlib_spec::Kind::Ep);
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

#[cfg(test)]
#[path = "poster_key_tests.rs"]
mod tests;
