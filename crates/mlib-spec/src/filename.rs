//! Fallback grammar for files without a caption. Provider ids in the caption
//! are always authoritative; this only seeds a TMDB search.

use std::sync::LazyLock;

use regex::{Captures, Regex};

use crate::ids::ProviderIds;

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Guess {
    pub title: String,
    pub year: Option<u16>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    pub episode_end: Option<u32>,
    pub abs: Option<u32>,
    /// Episode title or movie edition, when present after the numbering.
    pub extra: Option<String>,
    pub ids: ProviderIds,
    pub ext: String,
}

impl Guess {
    #[must_use]
    pub fn is_episode(&self) -> bool {
        self.episode.is_some() || self.abs.is_some()
    }
}

/// `{tmdb-123}` / `[tmdbid-123]` / `{imdb-tt123}` anywhere in the stem.
static ID: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)\s*[\{\[](?P<src>tmdb|tvdb|imdb)(?:id)?-(?P<id>[A-Za-z0-9]+)[\}\]]").unwrap()
});
/// Where release junk may start: only after a year or an episode code, so
/// titles like "Internal Affairs" or "Multi-Facial" are never eaten.
static ANCHOR: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)\(?(?:19|20)\d{2}\)?|s\d{1,2}e\d{1,3}").unwrap());
static JUNK: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)[\s\.\-\[\(]*\b(2160p|1080p|720p|480p|4k|uhd|web-?dl|webrip|bluray|blu-ray|bdrip|hdrip|dvdrip|remux|x264|x265|h\.?264|h\.?265|hevc|avc|aac|ac3|eac3|dts(?:-hd)?|truehd|atmos|hdr10\+?|dovi|proper|repack|amzn|nf|dsnp|atvp)\b.*$").unwrap()
});
static EP: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"(?i)^(?P<show>.+?)(?:\s*\((?P<year>\d{4})\))?(?:\s+-\s+|\s+|\.)s(?P<s>\d{1,2})e(?P<e>\d{1,3})(?:-?e(?P<e2>\d{1,3}))?(?:\s*-\s*|\s+)?(?P<extra>.+?)?\s*$").unwrap()
});
static MOVIE: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^(?P<title>.+?)\s*[\(\s](?P<year>(?:19|20)\d{2})\)?(?:\s+-\s+(?P<extra>.+?))?\s*$")
        .unwrap()
});
static ABS: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r"^(?P<show>.+?)\s+-\s+(?P<abs>\d{2,4})(?:\s+-\s+(?P<extra>.+?))?\s*$").unwrap()
});

fn num<T: std::str::FromStr>(c: &Captures, k: &str) -> Option<T> {
    c.name(k).and_then(|m| m.as_str().parse().ok())
}

fn text(c: &Captures, k: &str) -> Option<String> {
    c.name(k)
        .map(|m| m.as_str().trim().trim_end_matches(" -").trim().to_string())
        .filter(|s| !s.is_empty())
}

/// Strip release junk that follows the first year / episode anchor.
fn strip_junk(stem: &str) -> String {
    match ANCHOR.find(stem) {
        Some(m) => format!("{}{}", &stem[..m.end()], JUNK.replace(&stem[m.end()..], "")),
        None => stem.to_string(),
    }
}

/// Parse a bare file name (no directory). Returns `None` when nothing matches.
pub fn parse_filename(name: &str) -> Option<Guess> {
    let (stem, ext) = name.rsplit_once('.').unwrap_or((name, ""));
    let mut stem = stem.to_string();
    if !stem.contains(' ') && stem.matches('.').count() >= 2 {
        stem = stem.replace(['.', '_'], " ");
    }
    let mut ids = ProviderIds::default();
    if let Some(c) = ID.captures(&stem) {
        ids = ProviderIds::from_token(&c["src"].to_ascii_lowercase(), &c["id"]).unwrap_or_default();
        stem = ID.replace(&stem, "").into_owned();
    }
    let stem = strip_junk(&stem).trim().to_string();
    let ext = ext.to_ascii_lowercase();
    let base = Guess {
        ids,
        ext,
        ..Default::default()
    };
    if let Some(c) = EP.captures(&stem) {
        return Some(Guess {
            title: text(&c, "show").unwrap_or_default(),
            year: num(&c, "year"),
            season: num(&c, "s"),
            episode: num(&c, "e"),
            episode_end: num(&c, "e2"),
            extra: text(&c, "extra"),
            ..base
        });
    }
    if let Some(c) = MOVIE.captures(&stem) {
        return Some(Guess {
            title: text(&c, "title").unwrap_or_default(),
            year: num(&c, "year"),
            extra: text(&c, "extra"),
            ..base
        });
    }
    if let Some(c) = ABS.captures(&stem) {
        return Some(Guess {
            title: text(&c, "show").unwrap_or_default(),
            abs: num(&c, "abs"),
            extra: text(&c, "extra"),
            ..base
        });
    }
    (!stem.is_empty()).then_some(Guess {
        title: stem,
        ..base
    })
}
