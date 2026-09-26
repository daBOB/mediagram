//! One file to plan as a set, however it was asked for — `add`'s flags, or
//! one entry of a walked show, course or documentary collection — and what
//! planning it produced.

use std::path::PathBuf;

use mlib_spec::Kind;

/// One file to add, however it was asked for: the `add` command's flags, or
/// one entry of a walked show, course or documentary collection.
#[derive(Debug, Default, Clone)]
pub struct NewSet {
    pub file: PathBuf,
    pub tmdb: Option<u64>,
    pub tvdb: Option<u64>,
    pub imdb: Option<String>,
    pub season: Option<u32>,
    pub episode: Option<u32>,
    /// Absolute episode number (anime).
    pub abs: Option<u32>,
    pub variant: Option<String>,
    /// Enter metadata by hand instead of looking it up.
    pub manual: bool,
    pub no_remux: bool,
    /// Overrides for what the file itself says.
    pub alang: Option<Vec<String>>,
    pub slang: Option<Vec<String>>,
    pub hdr: Option<String>,
    /// Set when this is a lesson or a documentary-collection episode, which
    /// is described by hand rather than looked up.
    pub lesson: Option<LessonOf>,
    /// A standalone documentary (`Kind::Docu`, no course, no TMDB lookup).
    pub docu: bool,
    /// Overrides the title `docu` would otherwise derive from the file name.
    pub docu_title: Option<String>,
}

/// Where a lesson, or a documentary-collection episode, sits in its
/// collection.
#[derive(Debug, Clone)]
pub struct LessonOf {
    pub course: String,
    /// Collection id grouping the collection's entries.
    pub cid: String,
    pub chapter: Option<u32>,
    pub chapter_title: Option<String>,
    /// Folders within the collection, `/`-separated.
    pub path: Option<String>,
    pub number: Option<u32>,
    /// `Kind::Tut` for a course lesson, `Kind::Docu` for a documentary filed
    /// inside a collection folder.
    pub kind: Kind,
}

/// A set written to the index, with its bytes still to send.
pub struct Planned {
    pub set_id: String,
    pub display_name: String,
    pub total: u64,
}
