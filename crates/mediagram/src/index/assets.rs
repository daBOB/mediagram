//! Text that belongs to a set: subtitles, and a summary when there is one.
//!
//! Kept in the index rather than uploaded as separate channel messages. A
//! whole course's subtitles come to about 2 MB, which rides the published
//! package without noticing, and it lets a player show a summary or attach a
//! subtitle track with no Telegram round trip at all.

use anyhow::{Context, Result, bail};
use rusqlite::{Connection, OptionalExtension, params};

/// Largest single asset. Generous for a subtitle track or a written summary,
/// small enough that no one file can push a package towards its 64 MB
/// ceiling before anyone notices.
pub const MAX_ASSET_BYTES: usize = 1024 * 1024;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Subtitle,
    Summary,
}

impl Kind {
    pub fn as_str(self) -> &'static str {
        match self {
            Kind::Subtitle => "subtitle",
            Kind::Summary => "summary",
        }
    }
}

/// Stores one asset, replacing any it already had of the same kind and
/// language. Re-uploading a lesson corrects its subtitle rather than
/// accumulating copies.
pub fn put(conn: &Connection, set_id: &str, kind: Kind, lang: &str, body: &str) -> Result<()> {
    if body.len() > MAX_ASSET_BYTES {
        bail!(
            "{} for {set_id} is {} bytes; the limit is {MAX_ASSET_BYTES}",
            kind.as_str(),
            body.len()
        );
    }
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(set_id, kind, lang) DO UPDATE SET body = excluded.body",
        params![set_id, kind.as_str(), lang, body],
    )
    .with_context(|| format!("storing the {} for {set_id}", kind.as_str()))?;
    Ok(())
}

pub fn get(conn: &Connection, set_id: &str, kind: Kind, lang: &str) -> Result<Option<String>> {
    conn.query_row(
        "SELECT body FROM assets WHERE set_id = ?1 AND kind = ?2 AND lang = ?3",
        params![set_id, kind.as_str(), lang],
        |row| row.get(0),
    )
    .optional()
    .with_context(|| format!("reading the {} for {set_id}", kind.as_str()))
}

/// Subtitle languages held for a set, in a stable order.
pub fn languages(conn: &Connection, set_id: &str) -> Result<Vec<String>> {
    let mut stmt = conn
        .prepare("SELECT lang FROM assets WHERE set_id = ?1 AND kind = 'subtitle' ORDER BY lang")
        .context("preparing the subtitle language query")?;
    let rows = stmt
        .query_map([set_id], |row| row.get(0))
        .with_context(|| format!("listing subtitle languages for {set_id}"))?;
    rows.collect::<rusqlite::Result<Vec<String>>>()
        .context("reading a subtitle language")
}

/// Whether a set has a summary, without reading it.
pub fn has_summary(conn: &Connection, set_id: &str) -> Result<bool> {
    let found: Option<i64> = conn
        .query_row(
            "SELECT 1 FROM assets WHERE set_id = ?1 AND kind = 'summary'",
            [set_id],
            |row| row.get(0),
        )
        .optional()
        .with_context(|| format!("checking for a summary for {set_id}"))?;
    Ok(found.is_some())
}

/// Every asset in the index. Used by the export budget and by tests.
pub fn count(conn: &Connection) -> Result<i64> {
    conn.query_row("SELECT COUNT(*) FROM assets", [], |row| row.get(0))
        .context("counting assets")
}
