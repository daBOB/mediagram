//! clap arguments for `mediagram edit`, split out of `args.rs` to keep that
//! file under this crate's line limit.

use clap::Args;

/// Arguments for `mediagram edit`.
#[derive(Args, Debug, Clone)]
pub struct EditArgs {
    /// The set to correct
    pub set_id: String,
    /// Ask the provider again, in the configured `tmdb_language`. What the
    /// ids point at is right; the words may be in the wrong language
    #[arg(long)]
    pub refresh: bool,
    /// Move the set to another shelf: movie, ep, tut or docu
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
    /// Force a title in or out of the Anime department, or drop back to the
    /// automatic genre-and-language rule. Index-only: no caption rewrite
    #[arg(
        long,
        conflicts_with_all = [
            "refresh", "kind", "tmdb", "clear", "title", "show", "year",
            "season", "episode", "chap", "path",
        ],
    )]
    pub anime: Option<crate::edit::anime::AnimeChoice>,
    /// Give this unit — a course, a documentary collection or a standalone
    /// documentary — a category, filed into a row on its department page.
    /// Index-only: no caption rewrite, no Telegram
    #[arg(
        long,
        conflicts_with_all = [
            "refresh", "kind", "tmdb", "clear", "title", "show", "year",
            "season", "episode", "chap", "path", "anime", "clear_category",
        ],
    )]
    pub category: Option<String>,
    /// Remove this unit's category
    #[arg(
        long,
        conflicts_with_all = [
            "refresh", "kind", "tmdb", "clear", "title", "show", "year",
            "season", "episode", "chap", "path", "anime", "category",
        ],
    )]
    pub clear_category: bool,
}
