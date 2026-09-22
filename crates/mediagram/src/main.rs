//! mediagram: uploads a personal video library to one private Telegram channel
//! using the mlib caption and index spec.

use std::path::PathBuf;

use anyhow::Result;
use clap::{Parser, Subcommand};
use mediagram::commands::args::{AddArgs, AddCourseArgs, AddShowArgs, EditArgs, PrepareArgs};
use mediagram::{commands, config};

#[derive(Parser)]
#[command(name = "mediagram", version, about)]
struct Cli {
    /// Config file (default: $XDG_CONFIG_HOME/mediagram/config.toml)
    #[arg(long, global = true)]
    config: Option<PathBuf>,
    #[command(subcommand)]
    cmd: Cmd,
}

// Parsed once, at startup, and dropped. The size gap between `Login` and
// `Add(AddArgs)` costs a few stack bytes on one value and boxing would only
// obscure the argument types.
#[allow(clippy::large_enum_variant)]
#[derive(Subcommand)]
enum Cmd {
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
    /// Correct a set's metadata, rewriting its captions in the channel
    Edit(EditArgs),
    /// Finish every set left pending by an interrupted `add`
    Resume {
        /// Do not push the index after completing sets
        #[arg(long)]
        no_push: bool,
    },
    /// Upload library.db to the channel and pin it
    PushIndex,
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
    Metadata,
    /// Fetch cover art for the films and series in the index, for a player
    /// reading this machine's index rather than a published package
    Posters,
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

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .init();
    let cli = Cli::parse();
    let cfg_path = match &cli.config {
        Some(p) => p.clone(),
        None => mediagram::paths::config_file()?,
    };
    // First run: `login` asks for api_id, api_hash and the channel and writes
    // the config itself, rather than failing and sending the user off to copy
    // the example. Every other command still expects a config to exist, so a
    // non-interactive one never stalls on a prompt.
    let cfg = if matches!(cli.cmd, Cmd::Login) && !cfg_path.exists() {
        commands::setup::run(&cfg_path)?
    } else {
        config::load(Some(&cfg_path))?
    };
    match cli.cmd {
        Cmd::Login => commands::login::run(&cfg).await,
        Cmd::Whoami => commands::whoami::run(&cfg).await,
        Cmd::Add(args) => commands::add::run(&cfg, args).await,
        Cmd::AddCourse(args) => commands::add_course::run(&cfg, args).await,
        Cmd::AddShow(args) => commands::add_show::run(&cfg, args).await,
        Cmd::Edit(args) => commands::edit::run(&cfg, args).await,
        Cmd::Resume { no_push } => commands::resume::run(&cfg, no_push).await,
        Cmd::PushIndex => commands::push_index::run(&cfg).await,
        Cmd::Verify {
            set_id,
            all,
            full,
            since,
        } => commands::verify::run(&cfg, set_id, all, full, since).await,
        Cmd::Status => commands::status::run(&cfg).await,
        Cmd::Metadata => commands::metadata::run(&cfg).await,
        Cmd::Posters => commands::posters::run(&cfg).await,
        Cmd::ExportPackage {
            out,
            dry_run,
            publish,
        } => commands::export_package::run(&cfg, out, dry_run, publish).await,
        Cmd::Prepare(args) => commands::prepare::run(&cfg, args).await,
        Cmd::AcceptLogin { token } => commands::accept_login::run(&cfg, &token).await,
        Cmd::LoginCode => commands::login_code::run(&cfg).await,
        Cmd::Serve { addr } => commands::serve::run(&cfg, addr).await,
        Cmd::Remove {
            set_id,
            dry_run,
            yes,
        } => commands::remove::run(&cfg, set_id, dry_run, yes).await,
        Cmd::Rescan => commands::rescan::run(&cfg).await,
        Cmd::SmokeUpload { file } => commands::smoke_upload::run(&cfg, &file).await,
        Cmd::FinishSet {
            set_id,
            delete,
            no_push,
        } => commands::finish_set::run(&cfg, &set_id, delete.as_deref(), no_push).await,
    }
}
