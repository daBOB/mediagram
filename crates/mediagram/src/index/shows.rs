//! What a provider says about a title — a film or a whole series — as
//! opposed to about a file. The table is called `shows` for history; it holds
//! films too.
//!
//! The rest of the index describes files: a set is bytes with a name on them.
//! A series' synopsis belongs to the whole series and would be a lie repeated
//! on every episode, so it lives in its own table keyed the way a poster key
//! is.
//!
//! Every field here comes out of the TMDB payload `add` already fetched to
//! resolve a title, so filling this table for a library that predates it is a
//! read of the cache on disk, not a round of requests.

use anyhow::{Context, Result};
use mediagram_tmdb::details::TitleDetailsRow;
use mediagram_tmdb::posters::kind_key;
use mlib_spec::Kind;
use rusqlite::Connection;

/// Writes a show's entry, replacing whatever was there; see
/// [`mediagram_core::shows::upsert`], the table's one writer.
pub fn upsert(conn: &Connection, row: &TitleDetailsRow) -> Result<()> {
    mediagram_core::shows::upsert(conn, row)
        .with_context(|| format!("recording {} {}", kind_key(row.kind), row.id))
}

/// One show's entry, or `None` when nothing has been recorded for it.
pub fn get(conn: &Connection, kind: Kind, id: u64) -> Result<Option<TitleDetailsRow>> {
    mediagram_core::shows::get(conn, kind, id).context("reading a show entry")
}

/// How many shows have an entry. What `metadata` reports having done.
pub fn count(conn: &Connection) -> Result<i64> {
    conn.query_row("SELECT COUNT(*) FROM shows", [], |row| row.get(0))
        .context("counting show entries")
}
