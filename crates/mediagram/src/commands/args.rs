//! Arguments for `mediagram add`. Kept separate so the CLI surface is stable
//! while the command body is implemented.

use std::path::PathBuf;

use clap::Args;

#[derive(Args, Debug, Clone)]
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
}
