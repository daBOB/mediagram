//! Descriptions this device fetched for itself, kept beside the index rather
//! than in it.
//!
//! The index arrives from the channel already written, and this crate opens
//! it read-only on purpose — see [`super::catalog`]. A description the phone
//! fetches therefore has nowhere to go inside it and needs a store of its
//! own: the same `shows` table, built from the same migrations, in a database
//! [`details_db`] puts where a refresh cannot reach it.

use std::path::PathBuf;

use mlib_spec::schema;
use rusqlite::{Connection, params};

use mediagram_tmdb::details::ShowRow;
use mediagram_tmdb::posters::kind_key;

use super::catalog;
use super::{Core, CoreError};

/// The provider these rows come from, spelled the way the index spells it so
/// that one key finds a row in either database. The column exists so a
/// second provider would not need a migration to sit beside this one.
const SOURCE: &str = "tmdb";

/// Where descriptions this device fetched are kept.
///
/// Two things have to be true of this path at once, and naming only the
/// first is how fetched artwork came to be deleted by the very refreshes it
/// was fetched between — counted on a device at 0, then 236, then 0 again
/// across a restart.
///
/// **Out of the version directory**, so a refresh cannot delete it.
/// `install_staged` removes a version wholesale before renaming a fresh
/// download into place, `remove_other_versions` clears every version but the
/// one just published, and a refresh runs on every catalog load. Neither
/// pass touches a sibling: both remove only entries named `v-…` or
/// `incoming`, and a version is always `v-<pushed_at>`.
///
/// **Inside `catalog/`**, so forgetting the library forgets these too.
/// Signing out deletes that directory whole; rows held anywhere else would
/// outlive it, leaving the next account to set this device up reading
/// synopses of the previous one's titles — and growing without bound, since
/// nothing else would ever remove them.
pub fn details_db(core: &Core) -> PathBuf {
    catalog::dir(core).join("details.db")
}

/// Opens the sidecar for writing, creating the file and its schema on first
/// use.
///
/// The schema is the shared one, never a copy: a column added to
/// `mlib_spec`'s migrations reaches this database and the index alike, or
/// the two silently disagree about what a row holds.
///
/// Only the statements above what this file has already recorded are
/// applied. SQLite has no `ADD COLUMN IF NOT EXISTS`, so replaying the whole
/// list over an existing sidecar fails on the first `ALTER TABLE` — which
/// would leave a device that upgraded unable to open its own store at all.
pub fn open_or_create(core: &Core) -> Result<Connection, CoreError> {
    std::fs::create_dir_all(catalog::dir(core)).map_err(|_| preparing())?;
    let conn = Connection::open(details_db(core))
        .map_err(|_| CoreError::Io("opening the description store".into()))?;

    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0)).unwrap_or(0);
    if at < schema::SCHEMA_VERSION {
        let applied = schema::migrations_up_to(at).len();
        for statement in schema::migrations_up_to(schema::SCHEMA_VERSION).into_iter().skip(applied) {
            conn.execute(statement, []).map_err(|_| preparing())?;
        }
        conn.pragma_update(None, "user_version", schema::SCHEMA_VERSION)
            .map_err(|_| preparing())?;
    }
    Ok(conn)
}

fn preparing() -> CoreError {
    CoreError::Io("preparing the description store".into())
}

/// Records what a fetch learned about one title, replacing whatever was
/// there.
///
/// Replacing rather than merging is the point twice over: a later fetch is a
/// correction and not a second opinion, and asking again in another language
/// must not leave half the row in the old one.
pub fn upsert(conn: &Connection, row: &ShowRow) -> Result<(), CoreError> {
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating,
                           network, status, first_air, last_air,
                           total_seasons, total_episodes)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
         ON CONFLICT(source, kind, id) DO UPDATE SET
             lang = excluded.lang, overview = excluded.overview,
             tagline = excluded.tagline, genres = excluded.genres,
             rating = excluded.rating, network = excluded.network,
             status = excluded.status, first_air = excluded.first_air,
             last_air = excluded.last_air, total_seasons = excluded.total_seasons,
             total_episodes = excluded.total_episodes",
        params![
            SOURCE,
            kind_key(row.kind),
            row.id as i64,
            row.lang,
            row.overview,
            row.tagline,
            row.genres,
            row.rating,
            row.network,
            row.status,
            row.first_air,
            row.last_air,
            row.total_seasons,
            row.total_episodes,
        ],
    )
    .map_err(|_| CoreError::Io("recording a description".into()))?;
    Ok(())
}

#[cfg(test)]
#[path = "details_tests.rs"]
mod tests;
