//! What `add` is asked to do with one file, however it was asked.

use std::path::PathBuf;

use crate::commands::args::AddArgs;

/// One file to add, however it was asked for: the `add` command's flags, or
/// one entry of a walked show or course.
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
    /// Set when this is a lesson, which is described by hand rather than
    /// looked up.
    pub lesson: Option<LessonOf>,
}

/// Where a lesson sits in its course.
#[derive(Debug, Default, Clone)]
pub struct LessonOf {
    pub course: String,
    /// Collection id grouping the course's lessons.
    pub cid: String,
    pub chapter: Option<u32>,
    pub chapter_title: Option<String>,
    /// Folders within the course, `/`-separated.
    pub path: Option<String>,
    pub number: Option<u32>,
}

impl From<AddArgs> for NewSet {
    fn from(args: AddArgs) -> NewSet {
        let lesson = args.course.map(|course| LessonOf {
            cid: args.cid.unwrap_or_else(|| mlib_spec::slug::slug(&course)),
            course,
            chapter: args.chapter,
            chapter_title: args.chap,
            path: args.path,
            number: args.lesson,
        });
        NewSet {
            file: args.file,
            tmdb: args.tmdb,
            tvdb: args.tvdb,
            imdb: args.imdb,
            season: args.season,
            episode: args.episode,
            abs: args.abs_no,
            variant: args.variant,
            manual: args.manual,
            no_remux: args.no_remux,
            alang: args.alang,
            slang: args.slang,
            hdr: args.hdr,
            lesson,
        }
    }
}

/// A set written to the index, with its bytes still to send.
pub struct Planned {
    pub set_id: String,
    pub display_name: String,
    pub total: u64,
}
