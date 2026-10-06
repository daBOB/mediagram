//! Which subtitle tracks a set offers, and how to read one's content.
//!
//! A track comes from one of two places, and a reader never has to ask
//! which: `subtitle_tracks`, once the uploader has extracted a bundle for
//! the set (`mlib_spec::subtitle_bundle`), or, until then, one per `assets`
//! row an older uploader wrote inline. Both answer the same [`SubtitleTrack`]
//! shape, in the order [`tracks_by_set`] gives them; a set with a bundle
//! never has inline rows left to fall back to — the uploader's merge deletes
//! them once a bundle replaces them.

use std::collections::HashMap;

use mlib_spec::schema::ASSET_SUBTITLE;
use rusqlite::{Connection, OptionalExtension, params};

use crate::dto::SubtitleTrack;
use crate::sqlite_schema::table_exists;

/// Where a set's bundle document lives, once uploaded. Never crosses the
/// binding surface — see `crate::api::subtitles`, the only caller.
pub struct BundleRef {
    pub chat_id: i64,
    pub message_id: i64,
    pub bytes: u64,
    pub sha256: String,
}

/// Every set's subtitle tracks, in the order a picker offers them.
///
/// A set with a `subtitle_files` row reads its own `subtitle_tracks`, in
/// their bundle position. Every other set reads its inline `assets` rows,
/// `ORDER BY lang`, each synthesised into a track at its position in that
/// order: indexes published before the `subtitle_tracks` table existed are
/// still being read, and their sets carry subtitles only as those rows. An
/// index older than v13, or one with no bundles at all yet, simply has no
/// `subtitle_tracks` table: read as no bundled tracks anywhere, not a
/// failure.
pub fn tracks_by_set(conn: &Connection) -> rusqlite::Result<HashMap<String, Vec<SubtitleTrack>>> {
    let mut by_set: HashMap<String, Vec<SubtitleTrack>> = HashMap::new();
    let mut bundled: std::collections::HashSet<String> = std::collections::HashSet::new();

    if table_exists(conn, "subtitle_tracks")? {
        let mut stmt = conn.prepare(
            "SELECT set_id, track, lang, forced, sdh, label FROM subtitle_tracks ORDER BY set_id, track",
        )?;
        let rows = stmt.query_map([], |row| {
            Ok((
                row.get::<_, String>(0)?,
                SubtitleTrack {
                    track: row.get(1)?,
                    lang: row.get(2)?,
                    forced: row.get(3)?,
                    sdh: row.get(4)?,
                    label: row.get(5)?,
                },
            ))
        })?;
        for row in rows {
            let (set_id, track) = row?;
            bundled.insert(set_id.clone());
            by_set.entry(set_id).or_default().push(track);
        }
    }

    let mut stmt =
        conn.prepare("SELECT set_id, lang FROM assets WHERE kind = ?1 ORDER BY set_id, lang")?;
    let rows = stmt.query_map([ASSET_SUBTITLE], |row| {
        Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?))
    })?;
    let mut legacy: HashMap<String, Vec<String>> = HashMap::new();
    for row in rows {
        let (set_id, lang) = row?;
        legacy.entry(set_id).or_default().push(lang);
    }
    for (set_id, langs) in legacy {
        if bundled.contains(&set_id) {
            continue;
        }
        let tracks = langs
            .into_iter()
            .enumerate()
            .map(|(i, lang)| SubtitleTrack {
                track: i as u32,
                label: lang.clone(),
                lang,
                forced: false,
                sdh: false,
            })
            .collect();
        by_set.insert(set_id, tracks);
    }

    Ok(by_set)
}

/// Where a set's bundle document lives, if it has one yet.
pub fn bundle_ref(conn: &Connection, set_id: &str) -> rusqlite::Result<Option<BundleRef>> {
    if !table_exists(conn, "subtitle_files")? {
        return Ok(None);
    }
    conn.query_row(
        "SELECT chat_id, message_id, bytes, sha256 FROM subtitle_files WHERE set_id = ?1",
        [set_id],
        |row| {
            Ok(BundleRef {
                chat_id: row.get(0)?,
                message_id: row.get(1)?,
                bytes: row.get(2)?,
                sha256: row.get(3)?,
            })
        },
    )
    .optional()
}

/// A legacy inline track's own text — what a set with no bundle offers for
/// the `lang` [`tracks_by_set`] synthesised a track from.
pub fn legacy_body(conn: &Connection, set_id: &str, lang: &str) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT body FROM assets WHERE set_id = ?1 AND kind = ?2 AND lang = ?3",
        params![set_id, ASSET_SUBTITLE, lang],
        |row| row.get(0),
    )
    .optional()
}

/// `set_id` plus up to `next` lessons that follow it in its course's own
/// order — same `group_key`, ordered by chapter (`season`) then lesson
/// number (`episode`). A set with no `group_key` (not a course lesson, or an
/// index too old to have one) is the whole answer: nothing "follows" it here.
pub fn course_run(conn: &Connection, set_id: &str, next: usize) -> rusqlite::Result<Vec<String>> {
    let group_key: Option<String> = conn
        .query_row("SELECT group_key FROM sets WHERE set_id = ?1", [set_id], |row| row.get(0))
        .optional()?
        .flatten();
    let Some(group_key) = group_key else {
        return Ok(vec![set_id.to_string()]);
    };

    let mut stmt =
        conn.prepare("SELECT set_id FROM sets WHERE group_key = ?1 ORDER BY season, CAST(episode AS INTEGER)")?;
    let ids: Vec<String> = stmt
        .query_map([&group_key], |row| row.get(0))?
        .collect::<rusqlite::Result<_>>()?;
    let Some(pos) = ids.iter().position(|id| id == set_id) else {
        return Ok(vec![set_id.to_string()]);
    };
    Ok(ids[pos..(pos + 1 + next).min(ids.len())].to_vec())
}

#[cfg(test)]
#[path = "catalog_subtitles_tests.rs"]
mod tests;
