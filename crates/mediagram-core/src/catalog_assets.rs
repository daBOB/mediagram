//! Text that belongs to a set: a summary, and subtitles per language — the
//! `assets` rows the uploader writes from files sitting beside each video.
//! A port of `web/src/assets.ts`.

use std::collections::{HashMap, HashSet};

use rusqlite::{Connection, OptionalExtension, params};

/// Every set's subtitle languages, sorted — the same order
/// `subtitleLanguages` in `web/src/assets.ts` returns them in.
///
/// One query for the whole catalog rather than one per row: `list_sets`
/// builds every row in one pass, the same reason `shows::certifications`
/// reads every rating at once rather than per title.
pub fn subtitle_languages(conn: &Connection) -> rusqlite::Result<HashMap<String, Vec<String>>> {
    let mut stmt =
        conn.prepare("SELECT set_id, lang FROM assets WHERE kind = 'subtitle' ORDER BY set_id, lang")?;
    let rows = stmt.query_map([], |row| Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?)))?;
    let mut by_set: HashMap<String, Vec<String>> = HashMap::new();
    for row in rows {
        let (set_id, lang) = row?;
        by_set.entry(set_id).or_default().push(lang);
    }
    Ok(by_set)
}

/// Every set that carries a summary row, so `list_sets` can flag it without
/// a query per row.
pub fn summaries(conn: &Connection) -> rusqlite::Result<HashSet<String>> {
    let mut stmt = conn.prepare("SELECT set_id FROM assets WHERE kind = 'summary' AND lang = ''")?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// One asset's text: a summary (`lang` empty, ignored) or a subtitle track
/// in the language asked for. `None` for a `kind` this store does not know,
/// a set with no such row, or a set that does not exist — none of those is
/// a distinction worth telling apart here.
pub fn text(conn: &Connection, set_id: &str, kind: &str, lang: &str) -> rusqlite::Result<Option<String>> {
    match kind {
        "summary" => conn
            .query_row(
                "SELECT body FROM assets WHERE set_id = ?1 AND kind = 'summary' AND lang = ''",
                [set_id],
                |row| row.get(0),
            )
            .optional(),
        "subtitle" => conn
            .query_row(
                "SELECT body FROM assets WHERE set_id = ?1 AND kind = 'subtitle' AND lang = ?2",
                params![set_id, lang],
                |row| row.get(0),
            )
            .optional(),
        _ => Ok(None),
    }
}

#[cfg(test)]
#[path = "catalog_assets_tests.rs"]
mod tests;
