//! grammers client setup: SqliteSession + SenderPool + Client, channel resolution.
//!
//! [`Tg::connect`] is the single entry point later commands use: it opens the
//! persisted session, spins up the sender pool, runs the interactive login
//! flow if the session is not yet authorized, and resolves the configured
//! upload channel. [`open_client`] and [`super::login::ensure_login`] are exposed
//! separately so `mediagram login` can report whether it just authenticated
//! or reused an existing session, without resolving a channel it doesn't need.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result, bail};
use grammers_client::peer::Peer;
use grammers_client::Client;
use grammers_mtsender::{SenderPool, SenderPoolFatHandle};
use grammers_session::storages::SqliteSession;
use grammers_session::types::{PeerId, PeerRef};
use tokio::task::JoinHandle;

use crate::config::Config;

/// A connected, authorized client plus the resolved upload channel.
pub struct Tg {
    pub client: Client,
    pub channel: PeerRef,
    pub channel_title: String,
    handle: SenderPoolFatHandle,
    pool_task: JoinHandle<()>,
}

impl Tg {
    /// Opens the session, connects, logs in if needed, and resolves `cfg.channel`.
    pub async fn connect(cfg: &Config) -> Result<Tg> {
        let (client, handle, pool_task) = open_client(cfg).await?;
        super::login::ensure_login(&client, cfg).await?;
        let (channel, channel_title) = resolve_channel(&client, &cfg.channel).await?;
        Ok(Tg {
            client,
            channel,
            channel_title,
            handle,
            pool_task,
        })
    }

    /// The id recorded in every `parts.chat_id`: the bot-API dialog id when
    /// the channel exposes one, else the bare peer id. Verification compares
    /// against this, so the derivation lives in one place.
    pub fn chat_id(&self) -> i64 {
        chat_id_of(self.channel)
    }

    /// Signals the sender pool to disconnect and waits for it to stop.
    pub async fn shutdown(self) {
        self.handle.quit();
        let _ = self.pool_task.await;
    }
}

/// Lets the session store configure SQLite before anything else touches it.
///
/// This binary links a single SQLite library used by two crates. `libsql`,
/// under the session store, calls `sqlite3_config(SERIALIZED)` the first time
/// it opens a database and asserts the call succeeded. `sqlite3_config`
/// returns MISUSE once SQLite has been initialized, and `rusqlite`
/// initializes it the moment it opens `library.db`.
///
/// Every command reads the index before it reaches Telegram, so without this
/// the second of the two to start would abort the process. Calling it once at
/// startup makes the order irrelevant, rather than leaving a rule that every
/// future command has to remember.
pub async fn preinit_session_store(data_dir: &Path) -> Result<()> {
    crate::paths::private_dir(data_dir)?;
    let path = data_dir.join("session.sqlite");
    // Opening and dropping is enough: the configuration happens once, inside
    // libsql, on first open.
    let session = SqliteSession::open(&path)
        .await
        .with_context(|| format!("cannot open session {}", path.display()))?;
    drop(session);
    // The session file holds the account's authorization key.
    crate::paths::restrict_file(&path)?;
    Ok(())
}

/// Opens the persisted session and starts its sender pool, without logging
/// in or resolving a channel. Callers must run [`super::login::ensure_login`]
/// before issuing authenticated requests.
pub async fn open_client(cfg: &Config) -> Result<(Client, SenderPoolFatHandle, JoinHandle<()>)> {
    let path = session_path(cfg)?;
    let session = Arc::new(
        SqliteSession::open(&path)
            .await
            .with_context(|| format!("cannot open session {}", path.display()))?,
    );
    // The session file holds the account's authorization key.
    crate::paths::restrict_file(&path)?;

    let SenderPool { runner, handle, .. } = SenderPool::new(Arc::clone(&session), cfg.api_id);
    let client = Client::new(handle.clone());
    let pool_task = tokio::spawn(runner.run());
    Ok((client, handle, pool_task))
}

/// Resolves `channel_cfg` (a numeric channel id, either the bare form or the
/// `-100…` bot-API-style form, or an exact channel title) against the
/// account's dialogs.
pub async fn resolve_channel(client: &Client, channel_cfg: &str) -> Result<(PeerRef, String)> {
    let numeric = channel_cfg.trim().parse::<i64>().ok();
    let wanted = channel_cfg.trim();
    let mut seen = Vec::new();
    let mut dialogs = client.iter_dialogs();
    while let Some(dialog) = dialogs.next().await.context("listing dialogs")? {
        // Only a broadcast channel is a library home: in a group any member
        // can post, and readers only trust the channel's own posts.
        let title = match dialog.peer() {
            Peer::Channel(channel) => channel.title().to_string(),
            Peer::Group(_) | Peer::User(_) => continue,
        };
        let matches = match numeric {
            Some(n) => peer_id_matches(dialog.peer_id(), n),
            None => title.eq_ignore_ascii_case(wanted),
        };
        if matches {
            return Ok((dialog.peer_ref(), title));
        }
        seen.push(title);
    }
    bail!(
        "channel '{channel_cfg}' not found among the account's broadcast channels; channels seen: {}",
        if seen.is_empty() {
            "(none)".to_string()
        } else {
            seen.join(", ")
        }
    )
}

/// Matches a resolved [`PeerId`] against a config-supplied numeric id, in
/// either its bot-API dialog id form (`-100…`) or its bare form.
fn peer_id_matches(id: PeerId, n: i64) -> bool {
    id.bot_api_dialog_id() == Some(n) || id.bare_id() == Some(n)
}

/// Path to the persisted session database under the config's data dir.
fn session_path(cfg: &Config) -> Result<PathBuf> {
    let dir = cfg.data_dir()?;
    // libsql keeps the auth key in WAL/SHM sidecars too, so the directory itself is private.
    crate::paths::private_dir(&dir)?;
    Ok(dir.join("session.sqlite"))
}

/// See [`Tg::chat_id`]; free function so call sites holding only a
/// [`PeerRef`] (the upload transport) derive the id identically.
pub fn chat_id_of(channel: PeerRef) -> i64 {
    channel
        .id
        .bot_api_dialog_id()
        .unwrap_or_else(|| channel.id.bare_id_unchecked())
}
