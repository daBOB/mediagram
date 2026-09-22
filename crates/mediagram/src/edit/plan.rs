//! What a correction changes, and what it must not.
//!
//! Metadata is a guess that can be wrong — a title in the wrong language, a
//! show matched to the wrong id — and the channel is the archive, so
//! correcting it means rewriting captions rather than re-uploading bytes.
//!
//! What is never editable is anything describing those bytes: the set id, the
//! part geometry, the hashes and the total. Those are what `verify` checks
//! and what a player seeks with, and an edit that touched them would turn a
//! correction into corruption.

use anyhow::{Result, bail};

use mlib_spec::{Episode, Kind};

use crate::index::set_row::SetRow;

/// A field that can be emptied.
///
/// Clearing is separate from setting because the wrong kind leaves fields
/// behind that no value would fix: a film filed as a lesson carries a course
/// name and a lesson number, and what it needs is for them to be gone.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Clearable {
    Show,
    Chap,
    Path,
    Year,
    Season,
    Episode,
}

impl Clearable {
    pub fn parse(name: &str) -> Option<Clearable> {
        match name {
            "show" => Some(Clearable::Show),
            "chap" => Some(Clearable::Chap),
            "path" => Some(Clearable::Path),
            "year" => Some(Clearable::Year),
            "season" => Some(Clearable::Season),
            "episode" => Some(Clearable::Episode),
            _ => None,
        }
    }

    fn name(self) -> &'static str {
        match self {
            Clearable::Show => "show",
            Clearable::Chap => "chap",
            Clearable::Path => "path",
            Clearable::Year => "year",
            Clearable::Season => "season",
            Clearable::Episode => "episode",
        }
    }
}

/// Fields a person may correct. `None` leaves a field as it was.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Edits {
    pub kind: Option<Kind>,
    pub title: Option<String>,
    pub show: Option<String>,
    pub year: Option<u16>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    pub chap: Option<String>,
    pub path: Option<String>,
    pub tmdb: Option<u64>,
    pub clear: Vec<Clearable>,
}

impl Edits {
    /// True when nothing was asked for, so callers can refuse a no-op rather
    /// than rewrite a channel's worth of identical captions.
    pub fn is_empty(&self) -> bool {
        *self == Edits::default()
    }

    /// Whether a field is both set and cleared in one command.
    fn contradicts(&self, field: Clearable) -> bool {
        if !self.clear.contains(&field) {
            return false;
        }
        match field {
            Clearable::Show => self.show.is_some(),
            Clearable::Chap => self.chap.is_some(),
            Clearable::Path => self.path.is_some(),
            Clearable::Year => self.year.is_some(),
            Clearable::Season => self.season.is_some(),
            Clearable::Episode => self.episode.is_some(),
        }
    }
}

/// The kinds a set can be moved between. `Doc` is left out on purpose: its
/// bytes are a document and every other kind's are a video, so filing one as
/// the other would give a player something it cannot open.
const EDITABLE_KINDS: [Kind; 3] = [Kind::Movie, Kind::Ep, Kind::Tut];

/// Parses a `--kind` value, refusing a kind the spec does not have (its
/// caption would be unreadable) and one a set cannot be moved to.
pub fn editable_kind(spelling: &str) -> Result<Kind> {
    let names = EDITABLE_KINDS.map(Kind::as_str).join(", ");
    match spelling.parse::<Kind>() {
        Ok(kind) if EDITABLE_KINDS.contains(&kind) => Ok(kind),
        _ => bail!("kind must be one of {names}, not {spelling:?}"),
    }
}

/// Applies `edits`, refusing a request that cannot mean anything.
///
/// Checked rather than best-effort because the alternatives are worse: an
/// unknown kind produces a caption no reader can parse, and a field both set
/// and cleared is a contradiction where guessing is worse than stopping.
pub fn apply_checked(row: &SetRow, edits: &Edits) -> Result<SetRow> {
    for field in [
        Clearable::Show,
        Clearable::Chap,
        Clearable::Path,
        Clearable::Year,
        Clearable::Season,
        Clearable::Episode,
    ] {
        if edits.contradicts(field) {
            bail!("{} is both set and cleared; pick one", field.name());
        }
    }
    Ok(apply(row, edits))
}

/// Applies `edits` to a row, leaving everything they do not mention.
pub fn apply(row: &SetRow, edits: &Edits) -> SetRow {
    let mut edited = row.clone();
    if let Some(kind) = edits.kind {
        edited.kind = kind;
    }
    if let Some(tmdb) = edits.tmdb {
        edited.tmdb = Some(tmdb);
    }
    if let Some(title) = &edits.title {
        edited.title = Some(title.clone());
    }
    if let Some(show) = &edits.show {
        edited.show = Some(show.clone());
    }
    if let Some(year) = edits.year {
        edited.year = Some(year);
    }
    if let Some(season) = edits.season {
        edited.season = Some(season);
    }
    if let Some(episode) = edits.episode {
        edited.episode = Some(Episode::Single(episode));
    }
    if let Some(chap) = &edits.chap {
        edited.chap = Some(chap.clone());
    }
    if let Some(path) = &edits.path {
        edited.path = Some(path.clone());
    }
    // Clearing runs last: a field named in both would have been refused by
    // `apply_checked`, so order only matters for callers that skip the check.
    for field in &edits.clear {
        match field {
            Clearable::Show => edited.show = None,
            Clearable::Chap => edited.chap = None,
            Clearable::Path => edited.path = None,
            Clearable::Year => edited.year = None,
            Clearable::Season => edited.season = None,
            Clearable::Episode => edited.episode = None,
        }
    }
    edited
}

