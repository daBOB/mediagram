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
}

/// Episode number: a single episode or an inclusive range for multi-episode files.
#[derive(Serialize, Deserialize, Debug, Clone, Copy, PartialEq, Eq)]
#[serde(untagged)]
pub enum Episode {
    Single(u32),
    Range([u32; 2]),
}

impl Episode {
    pub fn first(self) -> u32 {
        match self {
            Episode::Single(e) | Episode::Range([e, _]) => e,
        }
    }
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
    pub show: Option<String>,
    /// Movie title, or episode title for `Kind::Ep`.
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
    pub fn is_movie(&self) -> bool {
        self.t == Kind::Movie
    }

    /// Same record with a different part block.
    pub fn with_part(&self, part: Part) -> Caption {
        Caption {
            part,
            ..self.clone()
        }
    }

    /// Human-readable label, e.g. `Dune: Part Two (2024)` or `Severance S02E01`.
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
        }
    }
}

/// `S02E01` or `S02E01-E02`.
pub fn episode_code(season: u32, e: Episode) -> String {
    match e {
        Episode::Single(n) => format!("S{season:02}E{n:02}"),
        Episode::Range([a, b]) => format!("S{season:02}E{a:02}-E{b:02}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ep() -> Caption {
        Caption {
            t: Kind::Ep,
            ids: ProviderIds {
                tmdb: Some(95396),
                tvdb: None,
                imdb: None,
            },
            show: Some("Severance".into()),
            title: Some("Hello, Ms. Cobel".into()),
            year: Some(2022),
            s: Some(2),
            e: Some(Episode::Single(1)),
            abs: None,
            q: Some("1080p".into()),
            hdr: Some("SDR".into()),
            container: "mkv".into(),
            vcodec: None,
            acodec: None,
            alang: vec!["en".into()],
            slang: vec![],
            dur: None,
            variant: None,
            set: "01JQ8F2K9M4XZ".into(),
            part: Part {
                i: 0,
                n: 1,
                off: 0,
                len: 10,
                sha256: "ab".into(),
            },
            total: 10,
        }
    }

    #[test]
    fn display_and_episode_code() {
        assert_eq!(ep().display_name(), "Severance S02E01");
        assert_eq!(episode_code(1, Episode::Range([1, 2])), "S01E01-E02");
        assert_eq!(Episode::Range([3, 5]).last(), 5);
    }

    #[test]
    fn with_part_keeps_everything_else() {
        let c = ep();
        let d = c.with_part(Part {
            i: 1,
            n: 2,
            off: 5,
            len: 5,
            sha256: "cd".into(),
        });
        assert_eq!(d.part.i, 1);
        assert_eq!(d.show, c.show);
    }
}
