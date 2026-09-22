//! What the index knows about work in progress.
//!
//! Read-only, and inferred rather than declared: nothing records that a bulk
//! add is running, so this reports what is unfinished and how far each show
//! has got. That is enough to watch an `add-show` from another terminal, and
//! it cannot know that a run means to stop early — which is the thing a real
//! queue would record.

use anyhow::{Context, Result};
use mlib_spec::{Episode, Kind};
use rusqlite::{Connection, params};

use crate::index::columns::decoded_or_null;
use crate::index::status::{PartStatus, SetStatus};

/// A set whose parts are not all in the channel yet.
#[derive(Debug, Clone, PartialEq)]
pub struct Unfinished {
    pub set_id: String,
    pub kind: String,
    pub show: Option<String>,
    pub title: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<Episode>,
    pub parts_done: u32,
    pub parts_total: u32,
    pub bytes_done: u64,
    pub bytes_total: u64,
    pub created_at: i64,
}

impl Unfinished {
    /// Whether any part has reached the channel.
    ///
    /// A set with none is one an interrupted run never started rather than
    /// one it left half done, and `resume` treats the two the same way.
    pub fn started(&self) -> bool {
        self.parts_done > 0
    }
}

/// Every set still waiting on parts, oldest first.
pub fn unfinished(conn: &Connection) -> Result<Vec<Unfinished>> {
    let mut stmt = conn
        .prepare(
            "SELECT s.set_id, s.kind, s.show, s.title, s.season, s.episode, s.total, s.created_at,
                    COUNT(p.idx),
                    COALESCE(SUM(p.status = ?1), 0),
                    COALESCE(SUM(CASE WHEN p.status = ?1 THEN p.byte_length ELSE 0 END), 0)
               FROM sets s LEFT JOIN parts p USING(set_id)
              WHERE s.status != ?2
              GROUP BY s.set_id
              ORDER BY s.created_at",
        )
        .context("preparing the unfinished query")?;
    let rows = stmt
        .query_map(params![PartStatus::Done, SetStatus::Complete], |row| {
            Ok(Unfinished {
                set_id: row.get(0)?,
                kind: row.get(1)?,
                show: row.get(2)?,
                title: row.get(3)?,
                season: row.get(4)?,
                episode: decoded_or_null(row, "episode", |text| serde_json::from_str(text))?,
                bytes_total: row.get(6)?,
                created_at: row.get(7)?,
                parts_total: row.get(8)?,
                parts_done: row.get(9)?,
                bytes_done: row.get(10)?,
            })
        })
        .context("listing unfinished sets")?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading an unfinished row")
}

/// How much of a show is in the channel, against how much exists.
#[derive(Debug, Clone, PartialEq)]
pub struct ShowProgress {
    pub show: String,
    pub held: u32,
    /// What the provider says exists, when `mediagram metadata` has asked.
    pub total: Option<u32>,
}

/// Every show with at least one complete episode, by name.
///
/// The total comes from the `shows` table, so a library that has not run
/// `metadata` gets counts without a denominator rather than no answer.
pub fn shows(conn: &Connection) -> Result<Vec<ShowProgress>> {
    let mut stmt = conn
        .prepare(
            "SELECT s.show, COUNT(*), MAX(w.total_episodes)
               FROM sets s
               LEFT JOIN shows w ON w.source = 'tmdb' AND w.kind = 'tv' AND w.id = s.tmdb
              WHERE s.kind = ?1 AND s.status = ?2 AND s.show IS NOT NULL
              GROUP BY s.show
              ORDER BY s.show",
        )
        .context("preparing the show progress query")?;
    let rows = stmt
        .query_map(params![Kind::Ep.as_str(), SetStatus::Complete], |row| {
            Ok(ShowProgress {
                show: row.get(0)?,
                held: row.get(1)?,
                total: row.get(2)?,
            })
        })
        .context("listing show progress")?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a show progress row")
}

/// A film in the library, by the name it is filed under.
#[derive(Debug, Clone, PartialEq)]
pub struct Film {
    pub title: String,
    pub year: Option<u32>,
}

/// Every film wholly in the channel, by title.
pub fn films(conn: &Connection) -> Result<Vec<Film>> {
    let mut stmt = conn
        .prepare(
            "SELECT COALESCE(title, set_id), year FROM sets
              WHERE kind = ?1 AND status = ?2
              ORDER BY COALESCE(title, set_id)",
        )
        .context("preparing the film query")?;
    let rows = stmt
        .query_map(params![Kind::Movie.as_str(), SetStatus::Complete], |row| {
            Ok(Film {
                title: row.get(0)?,
                year: row.get(1)?,
            })
        })
        .context("listing films")?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a film row")
}

/// A course, and how many lessons of it are held.
#[derive(Debug, Clone, PartialEq)]
pub struct Course {
    pub name: String,
    pub lessons: u32,
}

/// Every course with at least one lesson in the channel.
///
/// Counted rather than listed: a course is 162 lessons and naming them would
/// bury everything else in the report.
pub fn courses(conn: &Connection) -> Result<Vec<Course>> {
    let mut stmt = conn
        .prepare(
            "SELECT COALESCE(show, group_key, 'course'), COUNT(*) FROM sets
              WHERE kind = ?1 AND status = ?2
              GROUP BY COALESCE(show, group_key, 'course')
              ORDER BY 1",
        )
        .context("preparing the course query")?;
    let rows = stmt
        .query_map(params![Kind::Tut.as_str(), SetStatus::Complete], |row| {
            Ok(Course {
                name: row.get(0)?,
                lessons: row.get(1)?,
            })
        })
        .context("listing courses")?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a course row")
}

/// What the channel holds: how many sets, and how many bytes across them.
pub fn library(conn: &Connection) -> Result<(u64, u64)> {
    conn.query_row(
        "SELECT COUNT(*), COALESCE(SUM(total), 0) FROM sets WHERE status = ?1",
        [SetStatus::Complete],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )
    .context("counting the library")
}
