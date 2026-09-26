//! mediagram: uploads a personal video library to one private Telegram channel
//! using the mlib caption and index spec.

mod cli;

use anyhow::Result;
use clap::Parser;
use mediagram::{commands, config};

use cli::{Cli, Cmd};

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
        Cmd::AddDocu(args) => commands::add_docu::run(&cfg, args).await,
        Cmd::Artwork(args) => commands::artwork::run(&cfg, args),
        Cmd::Edit(args) => commands::edit::run(&cfg, args).await,
        Cmd::Resume { no_push } => commands::resume::run(&cfg, no_push).await,
        Cmd::PushIndex(args) => commands::push_index::run(&cfg, args).await,
        Cmd::PullIndex(args) => commands::pull_index::run(&cfg, args).await,
        Cmd::Verify {
            set_id,
            all,
            full,
            since,
        } => commands::verify::run(&cfg, set_id, all, full, since).await,
        Cmd::Status => commands::status::run(&cfg),
        Cmd::Metadata(args) => commands::metadata::run(&cfg, args).await,
        Cmd::Posters { index } => commands::posters::run(&cfg, index.as_deref()).await,
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
