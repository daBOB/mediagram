//! grammers client setup: SqliteSession + SenderPool + Client, channel resolution.
//!
//! [`Tg::connect`] is the single entry point later commands use: it opens the
//! persisted session, spins up the sender pool, runs the interactive login
//! flow if the session is not yet authorized, and resolves the configured
//! upload channel. [`open_client`] and [`ensure_login`] are exposed
//! separately so `mediagram login` can report whether it just authenticated
//! or reused an existing session, without resolving a channel it doesn't need.

use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use anyhow::{Context, Result, bail};
use dialoguer::{Input, Password};
use grammers_client::peer::Peer;
use grammers_client::{Client, SignInError};
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
        ensure_login(&client, cfg).await?;
        let (channel, channel_title) = resolve_channel(&client, &cfg.channel).await?;
        Ok(Tg {
            client,
            channel,
            channel_title,
            handle,
            pool_task,
        })
    }

    /// Signals the sender pool to disconnect and waits for it to stop.
    pub async fn shutdown(self) {
        self.handle.quit();
        let _ = self.pool_task.await;
    }
}

/// Opens the persisted session and starts its sender pool, without logging
/// in or resolving a channel. Callers must run [`ensure_login`] before
/// issuing authenticated requests.
pub async fn open_client(cfg: &Config) -> Result<(Client, SenderPoolFatHandle, JoinHandle<()>)> {
    let path = session_path(cfg)?;
    let session = Arc::new(
        SqliteSession::open(&path)
            .await
            .with_context(|| format!("cannot open session {}", path.display()))?,
    );
    restrict_session_permissions(&path)?;

    let SenderPool { runner, handle, .. } = SenderPool::new(Arc::clone(&session), cfg.api_id);
    let client = Client::new(handle.clone());
    let pool_task = tokio::spawn(runner.run());
    Ok((client, handle, pool_task))
}

/// Ensures `client` is authorized, running the interactive phone/code/2FA
/// flow if not. Returns `true` if a fresh login just happened, `false` if
/// the session was already authorized.
pub async fn ensure_login(client: &Client, cfg: &Config) -> Result<bool> {
    if client
        .is_authorized()
        .await
        .context("checking Telegram authorization")?
    {
        return Ok(false);
    }
    login_interactive(client, cfg).await?;
    Ok(true)
}

/// Runs the phone number → login code → optional 2FA password prompts.
async fn login_interactive(client: &Client, cfg: &Config) -> Result<()> {
    let phone: String = Input::new()
        .with_prompt("Phone number (e.g. +15551234567)")
        .interact_text()
        .context("reading phone number")?;

    let token = client
        .request_login_code(&phone, &cfg.api_hash)
        .await
        .context("requesting login code")?;

    let code: String = Input::new()
        .with_prompt("Login code")
        .interact_text()
        .context("reading login code")?;

    match client.sign_in(&token, &code).await {
        Ok(_user) => Ok(()),
        Err(SignInError::PasswordRequired(password_token)) => {
            let hint = password_token.hint().unwrap_or("none");
            let password = Password::new()
                .with_prompt(format!("2FA password (hint: {hint})"))
                .interact()
                .context("reading 2FA password")?;
            client
                .check_password(password_token, password.into_bytes())
                .await
                .map(drop)
                .map_err(|err| anyhow::anyhow!("2FA check failed: {err}"))
        }
        Err(err) => Err(anyhow::anyhow!("sign-in failed: {err}")),
    }
}

/// Resolves `channel_cfg` (a numeric channel id, either the bare form or the
/// `-100…` bot-API-style form, or an exact channel title) against the
/// account's dialogs.
pub async fn resolve_channel(client: &Client, channel_cfg: &str) -> Result<(PeerRef, String)> {
    let numeric = channel_cfg.trim().parse::<i64>().ok();
    let mut dialogs = client.iter_dialogs();
    while let Some(dialog) = dialogs.next().await.context("listing dialogs")? {
        let Peer::Channel(channel) = dialog.peer() else {
            continue;
        };
        let title = channel.title();
        let matches = match numeric {
            Some(n) => peer_id_matches(dialog.peer_id(), n),
            None => title == channel_cfg,
        };
        if matches {
            return Ok((dialog.peer_ref(), title.to_string()));
        }
    }
    bail!("channel '{channel_cfg}' not found among the account's dialogs; is the account a member?")
}

/// Matches a resolved [`PeerId`] against a config-supplied numeric id, in
/// either its bot-API dialog id form (`-100…`) or its bare form.
fn peer_id_matches(id: PeerId, n: i64) -> bool {
    id.bot_api_dialog_id() == Some(n) || id.bare_id() == Some(n)
}

/// Path to the persisted session database under the config's data dir.
fn session_path(cfg: &Config) -> Result<PathBuf> {
    let dir = cfg.data_dir()?;
    std::fs::create_dir_all(&dir)
        .with_context(|| format!("creating data dir {}", dir.display()))?;
    // libsql keeps the auth key in WAL/SHM sidecars too, so the directory itself is private.
    std::fs::set_permissions(&dir, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting permissions on {}", dir.display()))?;
    Ok(dir.join("session.sqlite"))
}

/// Restricts the session file to owner read/write, since it holds the
/// account's authorization key.
fn restrict_session_permissions(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting permissions on {}", path.display()))
}
