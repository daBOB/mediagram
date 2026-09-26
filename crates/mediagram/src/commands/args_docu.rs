//! clap argument structs for `add-course`, `add-docu` and `artwork`, split
//! out of `args.rs` to keep that file under this crate's line limit.

use std::path::PathBuf;

use clap::Args;

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

/// Arguments for `mediagram add-docu`.
#[derive(Args, Debug, Clone)]
pub struct AddDocuArgs {
    /// A documentary file, or a folder of them to walk as a collection
    pub path: PathBuf,
    /// Title override: the file for a single documentary, the folder name
    /// for a collection
    #[arg(long)]
    pub title: Option<String>,
    /// Collection id; defaults to a slug of the collection title. Only
    /// meaningful for a folder
    #[arg(long)]
    pub cid: Option<String>,
    /// Show what would be uploaded and stop
    #[arg(long)]
    pub dry_run: bool,
    /// Do not push the index after the upload completes
    #[arg(long)]
    pub no_push: bool,
    /// Variant label applied to every file
    #[arg(long)]
    pub variant: Option<String>,
    /// Skip the MP4 faststart remux
    #[arg(long)]
    pub no_remux: bool,
}

/// Arguments for `mediagram artwork`.
#[derive(Args, Debug, Clone)]
pub struct ArtworkArgs {
    /// A set id, or the show/course/collection title it belongs to
    pub target: String,
    /// Poster image file (.jpg, .jpeg, .png or .webp), up to 1 MB
    #[arg(long)]
    pub poster: Option<PathBuf>,
    /// Backdrop image file, up to 1 MB
    #[arg(long)]
    pub backdrop: Option<PathBuf>,
    /// Remove this title's custom poster and backdrop
    #[arg(long)]
    pub clear: bool,
}
