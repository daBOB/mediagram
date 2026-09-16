//! mediagram: uploads a personal video library to one private Telegram channel
//! using the mlib v2 caption + index spec.

use std::path::PathBuf;

use anyhow::Result;
use clap::{Parser, Subcommand};
use mediagram::commands::args::{AddArgs, AddCourseArgs, PrepareArgs};
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
    /// Serve the library over HTTP for a player: what is playable, and bytes
    Serve {
        /// Address to listen on; default: 127.0.0.1:8765, or serve_addr
        #[arg(long)]
        addr: Option<String>,
    },
    /// Rebuild library.db from channel captions (additive: never demotes local sets; use verify for that)
    Rescan,
    /// Upload one small file with a smoke caption, print the message id, delete it
    #[command(hide = true)]
    SmokeUpload { file: PathBuf },
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .with_writer(std::io::stderr)
        .init();
    let cli = Cli::parse();
    let cfg = config::load(cli.config.as_deref())?;
    // Before anything opens library.db: the session store has to configure
    // SQLite first, and that configuration fails once SQLite is initialized.
    // See `telegram::client::preinit_session_store`.
    mediagram::telegram::client::preinit_session_store(&cfg.data_dir()?).await?;
    match cli.cmd {
        Cmd::Login => commands::login::run(&cfg).await,
        Cmd::Whoami => commands::whoami::run(&cfg).await,
        Cmd::Add(args) => commands::add::run(&cfg, args).await,
        Cmd::AddCourse(args) => commands::add_course::run(&cfg, args).await,
        Cmd::Resume { no_push } => commands::resume::run(&cfg, no_push).await,
        Cmd::PushIndex => commands::push_index::run(&cfg).await,
        Cmd::Verify {
            set_id,
            all,
            full,
            since,
        } => commands::verify::run(&cfg, set_id, all, full, since).await,
        Cmd::ExportPackage {
            out,
            dry_run,
            publish,
        } => commands::export_package::run(&cfg, out, dry_run, publish).await,
        Cmd::Prepare(args) => commands::prepare::run(&cfg, args).await,
        Cmd::Serve { addr } => commands::serve::run(&cfg, addr).await,
        Cmd::Rescan => commands::rescan::run(&cfg).await,
        Cmd::SmokeUpload { file } => commands::smoke_upload::run(&cfg, &file).await,
    }
}
