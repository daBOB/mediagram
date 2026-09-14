//! `sets` table: one row per upload set (a whole media file split into
//! parts), mirroring the fields carried on every part's caption.

use anyhow::Result;
use mlib_spec::caption::{Caption, Kind};
use rusqlite::{Connection, OptionalExtension, params};

/// One row of the `sets` table.
#[derive(Debug, Clone, PartialEq)]
pub struct SetRow {
    pub set_id: String,
    pub kind: String,
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    pub imdb: Option<String>,
    pub show: Option<String>,
    pub title: Option<String>,
    pub year: Option<u16>,
    pub season: Option<u32>,
    /// JSON-encoded `mlib_spec::Episode`, e.g. `1` or `[1,2]`.
    pub episode: Option<String>,
    pub abs: Option<u32>,
    pub quality: Option<String>,
    pub hdr: Option<String>,
    pub container: String,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    pub alang: Vec<String>,
    pub slang: Vec<String>,
    pub duration: Option<u32>,
    pub variant: Option<String>,
    pub group_key: Option<String>,
    pub total: u64,
    pub part_count: u32,
    pub set_hash: Option<String>,
    pub status: String,
    pub created_at: i64,
    pub spec_version: u32,
}

impl SetRow {
    /// Builds the `sets` row for a freshly planned upload. `caption` carries
    /// every field this row mirrors; `part.n` becomes `part_count`.
    pub fn from_caption(caption: &Caption, created_at: i64) -> Result<SetRow> {
        let episode = caption.e.map(|e| serde_json::to_string(&e)).transpose()?;
        Ok(SetRow {
            set_id: caption.set.clone(),
            kind: match caption.t {
                Kind::Movie => "movie".to_string(),
                Kind::Ep => "ep".to_string(),
            },
            tmdb: caption.ids.tmdb,
            tvdb: caption.ids.tvdb,
            imdb: caption.ids.imdb.clone(),
            show: caption.show.clone(),
            title: caption.title.clone(),
            year: caption.year,
            season: caption.s,
            episode,
            abs: caption.abs,
            quality: caption.q.clone(),
            hdr: caption.hdr.clone(),
            container: caption.container.clone(),
            vcodec: caption.vcodec.clone(),
            acodec: caption.acodec.clone(),
            alang: caption.alang.clone(),
            slang: caption.slang.clone(),
            duration: caption.dur,
            variant: caption.variant.clone(),
            group_key: None,
            total: caption.total,
            part_count: caption.part.n,
            set_hash: None,
            status: "pending".to_string(),
            created_at,
            spec_version: mlib_spec::SPEC_VERSION,
        })
    }

    fn from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<SetRow> {
        // sqlite integers are signed 64-bit; ids and the total size are stored
        // as `i64` and widened back to `u64` here.
        let alang: String = row.get("alang")?;
        let slang: String = row.get("slang")?;
        let tmdb: Option<i64> = row.get("tmdb")?;
        let tvdb: Option<i64> = row.get("tvdb")?;
        let total: i64 = row.get("total")?;
        Ok(SetRow {
            set_id: row.get("set_id")?,
            kind: row.get("kind")?,
            tmdb: tmdb.map(|v| v as u64),
            tvdb: tvdb.map(|v| v as u64),
            imdb: row.get("imdb")?,
            show: row.get("show")?,
            title: row.get("title")?,
            year: row.get("year")?,
            season: row.get("season")?,
            episode: row.get("episode")?,
            abs: row.get("abs")?,
            quality: row.get("quality")?,
            hdr: row.get("hdr")?,
            container: row.get("container")?,
            vcodec: row.get("vcodec")?,
            acodec: row.get("acodec")?,
            alang: serde_json::from_str(&alang).unwrap_or_default(),
            slang: serde_json::from_str(&slang).unwrap_or_default(),
            duration: row.get("duration")?,
            variant: row.get("variant")?,
            group_key: row.get("group_key")?,
            total: total as u64,
            part_count: row.get("part_count")?,
            set_hash: row.get("set_hash")?,
            status: row.get("status")?,
            created_at: row.get("created_at")?,
            spec_version: row.get("spec_version")?,
        })
    }
}

const COLUMNS: &str = "set_id, kind, tmdb, tvdb, imdb, show, title, year, season, episode, abs,
    quality, hdr, container, vcodec, acodec, alang, slang, duration, variant, group_key,
    total, part_count, set_hash, status, created_at, spec_version";

/// Inserts a new set row. Fails if `set_id` already exists.
pub fn insert_set(conn: &Connection, row: &SetRow) -> Result<()> {
    let alang = serde_json::to_string(&row.alang)?;
    let slang = serde_json::to_string(&row.slang)?;
    let tmdb = row.tmdb.map(|v| v as i64);
    let tvdb = row.tvdb.map(|v| v as i64);
    let total = row.total as i64;
    conn.execute(
        &format!(
            "INSERT INTO sets({COLUMNS}) VALUES (
                ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11,
                ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21,
                ?22, ?23, ?24, ?25, ?26, ?27)"
        ),
        params![
            row.set_id,
            row.kind,
            tmdb,
            tvdb,
            row.imdb,
            row.show,
            row.title,
            row.year,
            row.season,
            row.episode,
            row.abs,
            row.quality,
            row.hdr,
            row.container,
            row.vcodec,
            row.acodec,
            alang,
            slang,
            row.duration,
            row.variant,
            row.group_key,
            total,
            row.part_count,
            row.set_hash,
            row.status,
            row.created_at,
            row.spec_version,
        ],
    )?;
    Ok(())
}

/// Updates `status` for one set.
pub fn set_status(conn: &Connection, set_id: &str, status: &str) -> Result<()> {
    conn.execute(
        "UPDATE sets SET status = ?1 WHERE set_id = ?2",
        params![status, set_id],
    )?;
    Ok(())
}

/// Records the final `set_hash` and marks the set `complete`.
pub fn set_hash_and_complete(conn: &Connection, set_id: &str, hash: &str) -> Result<()> {
    conn.execute(
        "UPDATE sets SET set_hash = ?1, status = 'complete' WHERE set_id = ?2",
        params![hash, set_id],
    )?;
    Ok(())
}

/// One set by id, if it exists.
pub fn get_set(conn: &Connection, set_id: &str) -> Result<Option<SetRow>> {
    conn.query_row(
        &format!("SELECT {COLUMNS} FROM sets WHERE set_id = ?1"),
        [set_id],
        SetRow::from_row,
    )
    .optional()
    .map_err(Into::into)
}

/// Every set still `pending`, oldest first (so `resume` finishes older sets before newer ones).
pub fn list_pending(conn: &Connection) -> Result<Vec<SetRow>> {
    let mut stmt = conn.prepare(&format!(
        "SELECT {COLUMNS} FROM sets WHERE status = 'pending' ORDER BY created_at"
    ))?;
    let rows = stmt
        .query_map([], SetRow::from_row)?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(rows)
}

// Covered by `tests/index_state.rs`: insert/get/list_pending/complete round
// trip through a real sqlite file, plus the not-found case.
