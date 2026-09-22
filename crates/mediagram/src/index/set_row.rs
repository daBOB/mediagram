//! One row of the `sets` table: a whole media file split into parts, mirroring
//! the fields carried on every part's caption.

use anyhow::Result;
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

use crate::index::columns::{decoded, decoded_or_null};
use crate::index::status::SetStatus;

/// One row of the `sets` table.
#[derive(Debug, Clone, PartialEq)]
pub struct SetRow {
    pub set_id: String,
    pub kind: Kind,
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    pub imdb: Option<String>,
    pub show: Option<String>,
    /// Chapter title, for course lessons.
    pub chap: Option<String>,
    /// Folders within the collection, `/`-separated. See `Caption::path`.
    pub path: Option<String>,
    pub title: Option<String>,
    pub year: Option<u16>,
    pub season: Option<u32>,
    /// Stored as the caption's JSON, `1` or `[1,2]`; decoded on read.
    pub episode: Option<Episode>,
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
    pub status: SetStatus,
    pub created_at: i64,
    pub spec_version: u32,
}

impl SetRow {
    /// Builds the `sets` row for a freshly planned upload. `caption` carries
    /// every field this row mirrors; `part.n` becomes `part_count`.
    pub fn from_caption(caption: &Caption, created_at: i64) -> Result<SetRow> {
        Ok(SetRow {
            set_id: caption.set.clone(),
            kind: caption.t,
            tmdb: caption.ids.tmdb,
            tvdb: caption.ids.tvdb,
            imdb: caption.ids.imdb.clone(),
            show: caption.show.clone(),
            chap: caption.chap.clone(),
            path: caption.path.clone(),
            title: caption.title.clone(),
            year: caption.year,
            season: caption.s,
            episode: caption.e,
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
            // A course's lessons group by collection id; nothing else sets
            // one today, so this is None for movies and episodes.
            group_key: caption.cid.clone(),
            total: caption.total,
            part_count: caption.part.n,
            set_hash: None,
            status: SetStatus::Pending,
            created_at,
            spec_version: mlib_spec::SPEC_VERSION,
        })
    }

    pub(crate) fn from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<SetRow> {
        let tmdb: Option<i64> = row.get("tmdb")?;
        let tvdb: Option<i64> = row.get("tvdb")?;
        Ok(SetRow {
            set_id: row.get("set_id")?,
            kind: decoded(row, "kind", str::parse::<Kind>)?,
            tmdb: mlib_spec::ids::id_from_column(tmdb),
            tvdb: mlib_spec::ids::id_from_column(tvdb),
            imdb: row.get("imdb")?,
            show: row.get("show")?,
            chap: row.get("chap")?,
            path: row.get("path")?,
            title: row.get("title")?,
            year: row.get("year")?,
            season: row.get("season")?,
            episode: decoded_or_null(row, "episode", |text| serde_json::from_str(text))?,
            abs: row.get("abs")?,
            quality: row.get("quality")?,
            hdr: row.get("hdr")?,
            container: row.get("container")?,
            vcodec: row.get("vcodec")?,
            acodec: row.get("acodec")?,
            alang: decoded(row, "alang", |text| serde_json::from_str(text))?,
            slang: decoded(row, "slang", |text| serde_json::from_str(text))?,
            duration: row.get("duration")?,
            variant: row.get("variant")?,
            group_key: row.get("group_key")?,
            total: row.get("total")?,
            part_count: row.get("part_count")?,
            set_hash: row.get("set_hash")?,
            status: row.get("status")?,
            created_at: row.get("created_at")?,
            spec_version: row.get("spec_version")?,
        })
    }
}

impl SetRow {
    /// The `episode` column's value: the caption's JSON, `1` or `[1,2]`.
    pub(crate) fn episode_json(&self) -> Result<Option<String>> {
        Ok(self.episode.map(|e| serde_json::to_string(&e)).transpose()?)
    }

    /// The set's `Caption` with a placeholder part block (idx 0, empty hash),
    /// used to derive names and human text that don't depend on which part.
    pub fn caption_template(&self) -> Result<Caption> {
        Ok(Caption {
            // The pipeline builds every part's caption from this template, so
            // dropping these would publish a course with no collection id and
            // no chapter, and `rescan` would have nothing to rebuild from.
            cid: self.group_key.clone(),
            chap: self.chap.clone(),
            path: self.path.clone(),
            t: self.kind,
            ids: ProviderIds {
                tmdb: self.tmdb,
                tvdb: self.tvdb,
                imdb: self.imdb.clone(),
            },
            show: self.show.clone(),
            title: self.title.clone(),
            year: self.year,
            s: self.season,
            e: self.episode,
            abs: self.abs,
            q: self.quality.clone(),
            hdr: self.hdr.clone(),
            container: self.container.clone(),
            vcodec: self.vcodec.clone(),
            acodec: self.acodec.clone(),
            alang: self.alang.clone(),
            slang: self.slang.clone(),
            dur: self.duration,
            variant: self.variant.clone(),
            set: self.set_id.clone(),
            part: Part {
                i: 0,
                n: self.part_count,
                off: 0,
                len: 0,
                sha256: String::new(),
            },
            total: self.total,
        })
    }
}
