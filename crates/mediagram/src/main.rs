//! mediagram: uploads a personal video library to one private Telegram channel
//! using the mlib v2 caption + index spec.

use std::path::PathBuf;

use anyhow::Result;
use clap::{Parser, Subcommand};
use mediagram::commands::args::AddArgs;
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
    },
    /// Rebuild library.db from channel captions
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
    match cli.cmd {
        Cmd::Login => commands::login::run(&cfg).await,
        Cmd::Whoami => commands::whoami::run(&cfg).await,
        Cmd::Add(args) => commands::add::run(&cfg, args).await,
        Cmd::Resume { no_push } => commands::resume::run(&cfg, no_push).await,
        Cmd::PushIndex => commands::push_index::run(&cfg).await,
        Cmd::Verify { set_id, all, full } => commands::verify::run(&cfg, set_id, all, full).await,
        Cmd::Rescan => commands::rescan::run(&cfg).await,
        Cmd::SmokeUpload { file } => commands::smoke_upload::run(&cfg, &file).await,
    }
}
