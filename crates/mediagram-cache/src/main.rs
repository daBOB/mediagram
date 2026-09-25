//! `mediagram_cache`: a dumb LAN chunk store. No Telegram session, no
//! index, no UI — just chunks on disk, shared between Android devices on
//! the home network.

use std::path::PathBuf;
use std::sync::Arc;

use anyhow::{Context, Result};
use clap::{Parser, Subcommand};
use tokio::net::TcpListener;

use mediagram_cache::{config, http, mdns, store, token};

#[derive(Parser)]
#[command(name = "mediagram_cache", version, about)]
struct Cli {
    /// Config file (default: every field's built-in default)
    #[arg(long, global = true)]
    config: Option<PathBuf>,
    #[command(subcommand)]
    cmd: Option<Cmd>,
}

#[derive(Subcommand)]
enum Cmd {
    /// Print the pairing token, creating it on first run
    Token,
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
        Some(Cmd::Token) => {
            let token = token::ensure(&cfg.state_dir).context("preparing the pairing token")?;
            println!("{token}");
            Ok(())
        }
        None => serve(cfg).await,
    }
}

async fn serve(cfg: config::Config) -> Result<()> {
    let token = token::ensure(&cfg.state_dir).context("preparing the pairing token")?;
    let chunk_store =
        store::ChunkStore::open(cfg.root.clone(), cfg.budget).context("opening the chunk store")?;
    let status = chunk_store.status();
    let store = Arc::new(chunk_store);

    let listener = TcpListener::bind(&cfg.listen)
        .await
        .with_context(|| format!("binding {}", cfg.listen))?;
    let addr = listener.local_addr().context("reading the bound address")?;

    // Kept alive for the process lifetime: dropping it unregisters the
    // service. A failure here is not fatal — Android falls back to a
    // manually entered address — so it is logged and the server still
    // starts.
    let _mdns = if cfg.mdns {
        mdns::register(addr.port())
            .inspect_err(|err| tracing::warn!("mDNS registration failed: {err:#}"))
            .ok()
    } else {
        None
    };

    println!(
        "mediagram_cache listening on http://{addr}, root {}",
        cfg.root.display()
    );
    println!(
        "{} chunks held, {} of {} bytes budgeted",
        status.chunks, status.held_bytes, status.budget_bytes
    );

    axum::serve(listener, http::router(store, token))
        .with_graceful_shutdown(shutdown_signal())
        .await
        .context("serving")
}

/// SIGTERM (systemd's stop signal) or Ctrl-C: either way, axum stops
/// accepting new connections and lets an in-flight PUT finish before the
/// process exits.
async fn shutdown_signal() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let terminate = async {
        let mut sig = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
            .expect("installing a SIGTERM handler");
        sig.recv().await;
    };
    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        () = ctrl_c => {}
        () = terminate => {}
    }
    println!("stopping");
}
