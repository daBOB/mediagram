//! What the player may offer, and where a set's bytes live.
//!
//! "Playable" is not redefined here. `mlib_spec::schema::PLAYABLE_SQL` already
//! says what it means — complete, every part done, lengths summing to the
//! recorded total — and the player asks that rather than forming a second
//! opinion. A second opinion is how a catalog ends up offering titles that
//! stall halfway through.

use anyhow::{Context, Result};
use rusqlite::Connection;

use super::range::PartSpan;

/// One title the player can offer. The codec fields are what let the web app
/// decide between direct play and a transcode without opening the file.
#[derive(Debug, Clone, PartialEq, serde::Serialize)]
pub struct PlayableSet {
    pub set_id: String,
    pub kind: String,
    pub title: Option<String>,
    pub show: Option<String>,
    pub chap: Option<String>,
    /// Where the set sat inside its collection, as `a/b/c` from the top
    /// down. A course nests unevenly, so only the path describes its shape;
    /// `chap` is one rendering of the same thing and cannot be split back.
    pub path: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<String>,
    /// TMDB id, when the title has one. Lets a binding-surface caller derive
    /// a poster key without a second query.
    pub tmdb: Option<u64>,
    pub year: Option<u16>,
    pub container: String,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    /// What the index recorded about the picture. `quality` is a label such
    /// as `1080p`, not a measurement; `hdr` is `HDR10`, `HLG`, `DV` or `SDR`.
    pub quality: Option<String>,
    pub hdr: Option<String>,
    pub duration: Option<u32>,
    pub total: u64,
    pub part_count: u32,
    /// When the uploader recorded this set, as a Unix time. The row has
    /// always carried it — `list_playable` orders by it — but nothing read
    /// it until a surface wanted to say what arrived recently.
    pub created_at: i64,
}

/// Where one part lives: its place in the virtual file, and the message
/// holding its bytes.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PartLocation {
    pub span: PartSpan,
    pub chat_id: i64,
    pub message_id: i64,
}

const COLUMNS: &str =
    "set_id, kind, title, show, chap, path, season, episode, tmdb, year, container,
     vcodec, acodec, quality, hdr, duration, total, part_count, created_at";

fn read_set(row: &rusqlite::Row<'_>) -> rusqlite::Result<PlayableSet> {
    Ok(PlayableSet {
        set_id: row.get("set_id")?,
        kind: row.get("kind")?,
        title: row.get("title")?,
        show: row.get("show")?,
        chap: row.get("chap")?,
        path: row.get("path")?,
        season: row.get("season")?,
        episode: row.get("episode")?,
        tmdb: mlib_spec::ids::id_from_column(row.get("tmdb")?),
        year: row.get("year")?,
        container: row.get("container")?,
        vcodec: row.get("vcodec")?,
        acodec: row.get("acodec")?,
        quality: row.get("quality")?,
        hdr: row.get("hdr")?,
        duration: row.get("duration")?,
        total: row.get("total")?,
        part_count: row.get("part_count")?,
        created_at: row.get("created_at")?,
    })
}

/// Every set a player can play, newest first.
pub fn list_playable(conn: &Connection) -> Result<Vec<PlayableSet>> {
    let sql = format!(
        "SELECT {COLUMNS} FROM sets s WHERE {} ORDER BY created_at DESC",
        mlib_spec::schema::PLAYABLE_SQL
    );
    let mut stmt = conn.prepare(&sql).context("preparing the catalog query")?;
    let rows = stmt
        .query_map([], read_set)
        .context("listing playable sets")?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a catalog row")
}

/// One set, if the player may play it. Asks the same question as
/// [`list_playable`] so a set can never be listed but not streamable, or the
/// other way round.
pub fn playable_set(conn: &Connection, set_id: &str) -> Result<Option<PlayableSet>> {
    let sql = format!(
        "SELECT {COLUMNS} FROM sets s WHERE s.set_id = ?1 AND {}",
        mlib_spec::schema::PLAYABLE_SQL
    );
    let mut stmt = conn.prepare(&sql).context("preparing the set query")?;
    let mut rows = stmt
        .query_map([set_id], read_set)
        .with_context(|| format!("looking up {set_id}"))?;
    rows.next()
        .transpose()
        .with_context(|| format!("reading the row for {set_id}"))
}

/// One set as the search index reads it: its text, and nothing else. A port
/// of `web/src/search/index.ts`'s `Searchable`, restricted to the columns
/// ranking touches — [`PlayableSet`] also carries codec and size fields no
/// search field reads.
#[derive(Debug, Clone, PartialEq)]
pub struct SearchableSet {
    pub set_id: String,
    pub title: Option<String>,
    pub show: Option<String>,
    pub chap: Option<String>,
    pub path: Option<String>,
    pub summary: Option<String>,
}

/// Every playable set with the text a search reads, summary included — a
/// port of `web/src/catalog.ts`'s `listSearchable`. One query rather than
/// [`list_playable`] plus a summary lookup per row: the subquery costs one
/// pass over `assets`, not one round trip per set.
pub fn list_searchable(conn: &Connection) -> Result<Vec<SearchableSet>> {
    let sql = format!(
        "SELECT set_id, title, show, chap, path,
                (SELECT body FROM assets a
                  WHERE a.set_id = s.set_id AND a.kind = 'summary' AND a.lang = '') AS summary
           FROM sets s WHERE {}",
        mlib_spec::schema::PLAYABLE_SQL
    );
    let mut stmt = conn.prepare(&sql).context("preparing the search query")?;
    let rows = stmt
        .query_map([], |row| {
            Ok(SearchableSet {
                set_id: row.get("set_id")?,
                title: row.get("title")?,
                show: row.get("show")?,
                chap: row.get("chap")?,
                path: row.get("path")?,
                summary: row.get("summary")?,
            })
        })
        .context("listing searchable sets")?;
    rows.collect::<rusqlite::Result<Vec<_>>>().context("reading a searchable row")
}

/// A set's parts in order, with the message each one lives in. Only `done`
/// parts: a part without a message has no bytes to serve.
pub fn part_locations(conn: &Connection, set_id: &str) -> Result<Vec<PartLocation>> {
    let mut stmt = conn
        .prepare(
            "SELECT idx, byte_offset, byte_length, chat_id, message_id
             FROM parts
             WHERE set_id = ?1 AND status = ?2
               AND chat_id IS NOT NULL AND message_id IS NOT NULL
             ORDER BY idx",
        )
        .context("preparing the part query")?;
    let rows = stmt
        .query_map([set_id, mlib_spec::schema::PART_DONE], |row| {
            Ok(PartLocation {
                span: PartSpan {
                    idx: row.get("idx")?,
                    off: row.get("byte_offset")?,
                    len: row.get("byte_length")?,
                },
                chat_id: row.get("chat_id")?,
                message_id: row.get("message_id")?,
            })
        })
        .with_context(|| format!("listing parts of {set_id}"))?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a part row")
}
