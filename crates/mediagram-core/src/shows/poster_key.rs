//! Reading a `shows` entry by the poster key that names it, rather than by
//! its raw `(kind, id)` — split out of `mod.rs` to keep that file under this
//! crate's line limit.

use mediagram_tmdb::details::TitleDetailsRow;
use mlib_spec::Kind;
use rusqlite::Connection;

use super::get;

/// The title a poster key names, or `None` for anything that is not one, so
/// a malformed value is refused here rather than reaching SQL. Every series
/// kind is keyed `tv`, so a series key reads back as `Kind::Ep` and finds the
/// same row any of them would.
pub fn title_of(poster_key: &str) -> Option<(Kind, u64)> {
    if !mlib_spec::package::poster_key_is_valid(poster_key) {
        return None;
    }
    let (kind, id) = poster_key.strip_prefix("tmdb-")?.split_once('-')?;
    let kind = match kind {
        "movie" => Kind::Movie,
        "tv" => Kind::Ep,
        _ => return None,
    };
    Some((kind, id.parse().ok()?))
}

/// The entry for the title a poster key names.
pub fn read(conn: &Connection, poster_key: &str) -> rusqlite::Result<Option<TitleDetailsRow>> {
    match title_of(poster_key) {
        Some((kind, id)) => get(conn, kind, id),
        None => Ok(None),
    }
}
