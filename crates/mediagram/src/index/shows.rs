//! What a provider says about a show, as opposed to about a file.
//!
//! The rest of the index describes files: a set is bytes with a name on them.
//! A synopsis belongs to the whole show and would be a lie repeated on every
//! episode, so it lives in its own table keyed the way a poster key is.
//!
//! Every field here comes out of the TMDB payload `add` already fetched to
//! resolve a title, so filling this table for a library that predates it is a
//! read of the cache on disk, not a round of requests.

use anyhow::{Context, Result};
use mediagram_core::shows::SOURCE;
use mediagram_tmdb::details::ShowRow;
use mediagram_tmdb::posters::kind_key;
use mlib_spec::Kind;
use rusqlite::{Connection, OptionalExtension, params};

/// Writes a show's entry, replacing whatever was there; see
/// [`mediagram_core::shows::upsert`], the table's one writer.
pub fn upsert(conn: &Connection, row: &ShowRow) -> Result<()> {
    mediagram_core::shows::upsert(conn, row)
        .with_context(|| format!("recording {} {}", kind_key(row.kind), row.id))
}

/// One show's entry, or `None` when nothing has been recorded for it.
pub fn get(conn: &Connection, kind: Kind, id: u64) -> Result<Option<ShowRow>> {
    conn.query_row(
        "SELECT lang, overview, tagline, genres, rating, network, status, first_air, last_air,
                total_seasons, total_episodes
           FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3",
        params![SOURCE, kind_key(kind), id as i64],
        |row| {
            Ok(ShowRow {
                kind,
                id,
                lang: row.get(0)?,
                overview: row.get(1)?,
                tagline: row.get(2)?,
                genres: row.get(3)?,
                rating: row.get(4)?,
                network: row.get(5)?,
                status: row.get(6)?,
                first_air: row.get(7)?,
                last_air: row.get(8)?,
                total_seasons: row.get(9)?,
                total_episodes: row.get(10)?,
            })
        },
    )
    .optional()
    .context("reading a show entry")
}

/// How many shows have an entry. What `metadata` reports having done.
pub fn count(conn: &Connection) -> Result<i64> {
    conn.query_row("SELECT COUNT(*) FROM shows", [], |row| row.get(0))
        .context("counting show entries")
}
