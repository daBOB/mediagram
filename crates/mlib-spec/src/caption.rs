//! The caption record. Field order is the wire order: serialization must stay
//! deterministic because captions are compared byte-for-byte in tests and
//! rewritten in place when metadata is corrected.

use serde::{Deserialize, Serialize};

use crate::ids::ProviderIds;

#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Kind {
    Movie,
    Ep,
    /// One lesson of a course. Course, chapter and lesson map onto `show`,
    /// `s`/`chap` and `e`/`title`, so ordering, resume and the playable
    /// invariant work without a second set of rules.
    Tut,
    /// A document belonging to a course: a handout beside a lesson, or a
    /// workbook in a folder holding no video at all.
    ///
    /// Numbered within its chapter exactly as a lesson is, because a course
    /// page orders a level by the number each entry leads with. A handout
    /// named `03 Signal.pdf` therefore lands on the row beside lesson 3
    /// without anything having to pair the two.
    ///
    /// Carries no `dur`, `vcodec`, `acodec`, `q` or `hdr`: none of them mean
    /// anything about a document, and a reader that finds them absent is
    /// looking at something it must not try to play.
    Doc,
}

/// Episode number: a single episode or an inclusive range for multi-episode files.
#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq, Eq)]
#[serde(untagged)]
pub enum Episode {
    Single(u32),
    Range([u32; 2]),
}

impl Episode {
    #[must_use]
    pub fn first(self) -> u32 {
        match self {
            Episode::Single(e) | Episode::Range([e, _]) => e,
        }
    }
    #[must_use]
    pub fn last(self) -> u32 {
        match self {
            Episode::Single(e) | Episode::Range([_, e]) => e,
        }
    }
}

/// Position of this message's bytes inside the logical file.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq, Eq)]
pub struct Part {
    pub i: u32,
    pub n: u32,
    pub off: u64,
    pub len: u64,
    /// Hex sha256 of exactly these `len` bytes.
    pub sha256: String,
}

/// One caption = one part message. Every part of a set carries the full record;
/// only `part` differs between them.
#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub struct Caption {
    pub t: Kind,
    pub ids: ProviderIds,
    /// Collection id: a stable grouping anchor for sets that belong together
    /// but have no provider id. Courses are its first user. Carried in the
    /// caption, not derived from the title, so renaming a course does not
    /// scatter its lessons and `rescan` can rebuild the grouping from the
    /// channel alone.
    #[serde(default)]
    pub cid: Option<String>,
    /// Course title for `Kind::Tut`, show title for `Kind::Ep`.
    pub show: Option<String>,
    /// Chapter title. Only meaningful for `Kind::Tut`.
    #[serde(default)]
    pub chap: Option<String>,
    /// Folders this set came from, within its collection, `/`-separated:
    /// `"Ausbildung Trading/1. Grundlagen/1. Trading"`.
    ///
    /// A real course does not nest to a fixed depth. The one this was built
    /// for runs from one to four folders deep and has a folder holding videos
    /// beside a subfolder, which chapter-and-lesson cannot describe. Carrying
    /// the path lets a player rebuild any tree by splitting on `/`, and keeps
    /// `chap` meaning what it says: a title.
    ///
    /// A label, never a path to open. `caption_codec` refuses `..`, absolute
    /// paths and empty segments so that a reader which forgets that cannot be
    /// aimed at a filesystem.
    #[serde(default)]
    pub path: Option<String>,
    /// Movie title, episode title for `Kind::Ep`, lesson title for `Kind::Tut`.
    pub title: Option<String>,
    pub year: Option<u16>,
    pub s: Option<u32>,
    pub e: Option<Episode>,
    /// Absolute episode number (anime); may coexist with `s`/`e` or replace them.
    pub abs: Option<u32>,
    pub q: Option<String>,
    pub hdr: Option<String>,
    pub container: String,
    pub vcodec: Option<String>,
    pub acodec: Option<String>,
    pub alang: Vec<String>,
    pub slang: Vec<String>,
    /// Duration in whole seconds.
    pub dur: Option<u32>,
    pub variant: Option<String>,
    /// ULID minted when the set was added. Never derived from content.
    pub set: String,
    pub part: Part,
    /// Logical file size in bytes; equals the sum of all part lengths.
    pub total: u64,
}

impl Caption {
    /// Same record with a different part block.
    #[must_use]
    pub fn with_part(&self, part: Part) -> Caption {
        Caption {
            part,
            ..self.clone()
        }
    }

    /// Human-readable label, e.g. `Dune: Part Two (2024)` or `Severance S02E01`.
    #[must_use]
    pub fn display_name(&self) -> String {
        match self.t {
            Kind::Movie => match (self.title.as_deref(), self.year) {
                (Some(t), Some(y)) => format!("{t} ({y})"),
                (Some(t), None) => t.to_string(),
                (None, _) => self.set.clone(),
            },
            Kind::Ep => {
                let show = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e, self.abs) {
                    (Some(s), Some(e), _) => format!("{show} {}", episode_code(s, e)),
                    (_, _, Some(a)) => format!("{show} #{a:03}"),
                    _ => show.to_string(),
                }
            }
            Kind::Tut => {
                let course = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e) {
                    (Some(c), Some(l)) => format!("{course} {}", lesson_code(c, l)),
                    _ => course.to_string(),
                }
            }
            Kind::Doc => {
                let course = self.show.as_deref().unwrap_or("?");
                match (self.s, self.e) {
                    (Some(c), Some(d)) => format!("{course} {}", document_code(c, d)),
                    _ => course.to_string(),
                }
            }
        }
    }
}

/// `C02L02`. Chapter and lesson rather than season and episode, so a course
/// never reads as a television series.
#[must_use]
pub fn lesson_code(chapter: u32, lesson: Episode) -> String {
    format!("C{chapter:02}L{:02}", lesson.first())
}

/// `C02D03`. A document is numbered inside its chapter the way a lesson is,
/// so `D` rather than `L` is the only thing that says which of the two a row
/// is before anything reads the container.
#[must_use]
pub fn document_code(chapter: u32, document: Episode) -> String {
    format!("C{chapter:02}D{:02}", document.first())
}

/// `S02E01` or `S02E01-E02`.
#[must_use]
pub fn episode_code(season: u32, e: Episode) -> String {
    match e {
        Episode::Single(n) => format!("S{season:02}E{n:02}"),
        Episode::Range([a, b]) => format!("S{season:02}E{a:02}-E{b:02}"),
    }
}

#[cfg(test)]
#[path = "caption_tests.rs"]
mod tests;
