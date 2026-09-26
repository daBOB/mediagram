//! clap's argument struct and subcommand enum: the whole CLI surface, split
//! out of `main.rs` to keep that file under this crate's line limit.
//! `main.rs` only parses one of these and dispatches.

use std::path::PathBuf;

use clap::{Parser, Subcommand};

use mediagram::commands::args::{
    AddArgs, AddCourseArgs, AddDocuArgs, AddShowArgs, ArtworkArgs, EditArgs, PrepareArgs,
};
use mediagram::commands::{
    metadata::MetadataArgs, pull_index::PullIndexArgs, push_index::PushIndexArgs,
    sync_index::SyncIndexArgs,
};

#[derive(Parser)]
#[command(name = "mediagram", version, about)]
pub struct Cli {
    /// Config file (default: $XDG_CONFIG_HOME/mediagram/config.toml)
    #[arg(long, global = true)]
    pub config: Option<PathBuf>,
    #[command(subcommand)]
    pub cmd: Cmd,
}

// Parsed once, at startup, and dropped. The size gap between `Login` and
// `Add(AddArgs)` costs a few stack bytes on one value and boxing would only
// obscure the argument types.
#[allow(clippy::large_enum_variant)]
#[derive(Subcommand)]
pub enum Cmd {
    /// Sign in with phone + code (+ 2FA password) and persist the session
    Login,
    /// Print the signed-in account and the resolved library channel
    Whoami,
    /// Split, upload, caption and index one media file
    Add(AddArgs),
    /// Walk a course folder and upload every lesson in it
    AddCourse(AddCourseArgs),
    /// Walk a series folder and upload every episode in it
    AddShow(AddShowArgs),
    /// Upload a documentary, or a folder of them as a collection
    AddDocu(AddDocuArgs),
    /// Set or clear a title's custom poster/backdrop, overriding TMDB's own
    Artwork(ArtworkArgs),
    /// Correct a set's metadata, rewriting its captions in the channel
    Edit(EditArgs),
    /// Finish every set left pending by an interrupted `add`
    Resume {
        /// Do not push the index after completing sets
        #[arg(long)]
        no_push: bool,
    },
    /// Upload library.db to the channel and pin it
    PushIndex(PushIndexArgs),
    /// Merge the channel's index into this one, so either machine can publish everything
    PullIndex(PullIndexArgs),
    /// Pull the channel's index, describe titles, fetch artwork, and push: the
    /// four index commands in one
    SyncIndex(SyncIndexArgs),
    /// Check a set (or all sets); `--full` re-downloads and hashes every part
    Verify {
        set_id: Option<String>,
        #[arg(long)]
        all: bool,
        #[arg(long)]
        full: bool,
        /// With --full, skip parts already verified at or after this unix
        /// timestamp, so an interrupted sweep resumes instead of restarting
        #[arg(long)]
        since: Option<i64>,
    },
    /// Show what the library holds and what is still being uploaded
    Status,
    /// Record what the provider says about each film and series: synopsis,
    /// genres, rating. Reads payloads `add` already cached
    Metadata(MetadataArgs),
    /// Fetch cover art for the films and series in the index, for a player
    /// reading this machine's index rather than a published package
    Posters {
        /// Read the titles from this index instead of this machine's own —
        /// the channel snapshot a web player is serving. The art is still
        /// written beside this machine's index, where that player looks.
        #[arg(long)]
        index: Option<PathBuf>,
    },
    /// Assemble the encrypted prebuilt metadata package for a player
    ExportPackage {
        /// Where to write the package (default: <data dir>/export)
        #[arg(long)]
        out: Option<PathBuf>,
        /// Report what would be exported without writing or downloading
        #[arg(long)]
        dry_run: bool,
        /// Hand the package and pointer to publish_cmd from config
        #[arg(long)]
        publish: bool,
    },
    /// Drop unwanted audio and subtitle tracks so a file fits one upload part
    Prepare(PrepareArgs),
    /// Approve a QR login for the player, the way a phone approves a scan
    AcceptLogin {
        /// The token the player printed
        token: String,
    },
    /// Show the login code Telegram just sent, read from this account's messages
    LoginCode,
    /// Serve the library over HTTP for a player: what is playable, and bytes
    Serve {
        /// Address to listen on; default: 127.0.0.1:8765, or serve_addr
        #[arg(long)]
        addr: Option<String>,
    },
    /// Permanently delete a set: its channel messages and its index rows
    Remove {
        /// One or more set ids
        set_id: Vec<String>,
        /// Show what would be deleted and stop
        #[arg(long)]
        dry_run: bool,
        /// Required to actually delete; there is no undo
        #[arg(long)]
        yes: bool,
    },
    /// Rebuild library.db from channel captions (additive: never demotes local sets; use verify for that)
    Rescan,
    /// Upload one small file with a smoke caption, print the message id, delete it
    #[command(hide = true)]
    SmokeUpload { file: PathBuf },
    /// Finish a set `add` has already planned. This is what `add` starts in
    /// the background; `resume` is the one to reach for by hand
    #[command(hide = true)]
    FinishSet {
        set_id: String,
        /// Delete this file once every part of the set is in the channel
        #[arg(long)]
        delete: Option<PathBuf>,
        #[arg(long)]
        no_push: bool,
    },
}

#[cfg(test)]
#[path = "cli_tests.rs"]
mod tests;
