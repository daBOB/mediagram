//! clap argument structs for the commands that take more than a flag or two.

use std::path::PathBuf;

use clap::Args;

#[derive(Args, Debug, Clone, Default)]
pub struct AddArgs {
    pub file: PathBuf,
    /// TMDB id (movie or show)
    #[arg(long)]
    pub tmdb: Option<u64>,
    #[arg(long)]
    pub tvdb: Option<u64>,
    /// IMDb id, with or without the `tt` prefix
    #[arg(long)]
    pub imdb: Option<String>,
    #[arg(long)]
    pub season: Option<u32>,
    #[arg(long)]
    pub episode: Option<u32>,
    /// Absolute episode number (anime)
    #[arg(long, name = "abs")]
    pub abs_no: Option<u32>,
    /// Variant label for alternate cuts/qualities of the same title
    #[arg(long)]
    pub variant: Option<String>,
    /// Enter metadata by hand instead of looking it up
    #[arg(long)]
    pub manual: bool,
    /// Course title. Marks this a tutorial: no provider lookup happens
    #[arg(long)]
    pub course: Option<String>,
    /// Collection id grouping a course's lessons; defaults to a slug of the
    /// course title. Give one explicitly to keep grouping across a rename
    #[arg(long)]
    pub cid: Option<String>,
    /// Chapter number within the course (default 1)
    #[arg(long)]
    pub chapter: Option<u32>,
    /// Chapter title
    #[arg(long)]
    pub chap: Option<String>,
    /// Folders this lesson came from within the course, `/`-separated. Set by
    /// `add-course` from the walk; a course nests unevenly and chapter number
    /// alone cannot say where a lesson sat
    #[arg(long)]
    pub path: Option<String>,
    /// Lesson number within the chapter
    #[arg(long)]
    pub lesson: Option<u32>,
    /// Skip the MP4 faststart remux
    #[arg(long)]
    pub no_remux: bool,
    /// Override detected audio languages, comma separated (e.g. en,de)
    #[arg(long, value_delimiter = ',')]
    pub alang: Option<Vec<String>>,
    /// Override detected subtitle languages
    #[arg(long, value_delimiter = ',')]
    pub slang: Option<Vec<String>>,
    /// Override detected HDR format (SDR, HDR10, HLG, DV)
    #[arg(long)]
    pub hdr: Option<String>,
    /// Do not push the index after this set completes
    #[arg(long)]
    pub no_push: bool,
    /// Delete the file once every part of it is in the channel. The index
    /// is what says so; this does not read the parts back to check
    #[arg(long)]
    pub delete_source: bool,
    /// Stay and show the upload. Without it the upload is handed to a
    /// background process and the command returns as soon as the set is
    /// planned
    #[arg(long)]
    pub watch: bool,
}

/// Arguments for `mediagram add-course`.
#[derive(Args, Debug, Clone)]
pub struct AddCourseArgs {
    /// The course folder: chapters are its subdirectories
    pub dir: PathBuf,
    /// Course title; defaults to the folder name
    #[arg(long)]
    pub course: Option<String>,
    /// Collection id; defaults to a slug of the course title
    #[arg(long)]
    pub cid: Option<String>,
    /// Show what would be uploaded and stop
    #[arg(long)]
    pub dry_run: bool,
    /// Do not push the index after the walk completes
    #[arg(long)]
    pub no_push: bool,
    /// Variant label applied to every lesson
    #[arg(long)]
    pub variant: Option<String>,
    /// Skip the MP4 faststart remux
    #[arg(long)]
    pub no_remux: bool,
}

/// Arguments for `mediagram prepare`.
#[derive(Args, Debug, Clone)]
pub struct PrepareArgs {
    /// A video file, or a folder to walk
    pub path: PathBuf,
    /// Rewrite the files in place. Without this, prepare only reports
    #[arg(long)]
    pub replace: bool,
    /// Audio languages to keep, comma separated
    #[arg(long, default_value = "ger,deu,eng")]
    pub audio: String,
    /// Subtitle languages to keep, comma separated
    #[arg(long, default_value = "ger,deu,eng")]
    pub subs: String,
    /// Size a file must fit in; defaults to the configured part size
    #[arg(long)]
    pub limit: Option<u64>,
    /// Also convert to a browser-playable mp4: Matroska becomes mp4, the
    /// audio becomes AAC, and the index moves to the front. The picture is
    /// still copied untouched
    #[arg(long)]
    pub mp4: bool,
    /// Write the results under this directory, mirroring the source tree,
    /// instead of replacing the originals
    #[arg(long)]
    pub out: Option<PathBuf>,
    /// Delete each source file once its replacement has been written and
    /// checked. Only with --out, and only ever after the check passes
    #[arg(long)]
    pub delete_source: bool,
}

/// Arguments for `mediagram edit`.
#[derive(Args, Debug, Clone)]
pub struct EditArgs {
    /// The set to correct
    pub set_id: String,
    /// Ask the provider again, in the configured `tmdb_language`. What the
    /// ids point at is right; the words may be in the wrong language
    #[arg(long)]
    pub refresh: bool,
    /// Move the set to another shelf: movie, ep or tut
    #[arg(long)]
    pub kind: Option<String>,
    /// Set the TMDB id, so `--refresh` has something to ask about
    #[arg(long)]
    pub tmdb: Option<u64>,
    /// Empty a field, comma separated: show,chap,path,year,season,episode.
    /// The wrong kind leaves fields behind that no value would fix
    #[arg(long, value_delimiter = ',')]
    pub clear: Vec<String>,
    #[arg(long)]
    pub title: Option<String>,
    /// Show title, or course title for a lesson
    #[arg(long)]
    pub show: Option<String>,
    #[arg(long)]
    pub year: Option<u16>,
    #[arg(long)]
    pub season: Option<u32>,
    #[arg(long)]
    pub episode: Option<u32>,
    /// Chapter title
    #[arg(long)]
    pub chap: Option<String>,
    /// Folders within the collection, `/`-separated
    #[arg(long)]
    pub path: Option<String>,
    /// Show what would change and stop
    #[arg(long)]
    pub dry_run: bool,
}

/// `mediagram add-show`: upload a series folder, one set per episode.
#[derive(Args, Debug, Clone)]
pub struct AddShowArgs {
    /// Folder holding the show; season subfolders are walked
    pub dir: PathBuf,
    /// TMDB series id. Required: without it every episode would be resolved
    /// separately, and a release prefix in the file names means answering the
    /// same prompt once per file
    #[arg(long)]
    pub tmdb: Option<u64>,
    /// Report what would be uploaded without uploading it
    #[arg(long)]
    pub dry_run: bool,
    /// Do not push the index when the show is done
    #[arg(long)]
    pub no_push: bool,
    /// Skip confirmation for playback warnings or unknown compatibility
    #[arg(long)]
    pub yes: bool,
    /// Delete each episode once every part of it is in the channel, so a
    /// season frees its disk as it goes rather than all at the end
    #[arg(long)]
    pub delete_source: bool,
}
