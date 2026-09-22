//! Persisting the account's authorization across launches.
//!
//! Android drops `SqliteSession` from the build (see the crate's
//! `Cargo.toml`), and an in-memory session remembers nothing between one
//! process and the next — without this, every launch would repeat the whole
//! phone/code/2FA dance, and grammers warns that doing so risks a flood
//! wait. The auth key is the whole authorization, so persistence is just two
//! numbers: which datacentre it belongs to, and the 256 bytes themselves.

use std::io::Write;
use std::os::unix::fs::OpenOptionsExt;
use std::path::Path;
use std::sync::Arc;

use grammers_client::Client;
use grammers_mtsender::{SenderPool, SenderPoolFatHandle};
use grammers_session::SessionData;
use grammers_session::storages::MemorySession;
use grammers_session::updates::UpdatesLike;
use tokio::sync::mpsc::UnboundedReceiver;
use tokio::task::JoinHandle;

use crate::api::{Core, CoreError};

const SESSION_FILE: &str = "session.key";
const AUTH_KEY_LEN: usize = 256;

/// A live connection, kept for the app's lifetime so a later call reuses it
/// instead of paying for a fresh one.
pub(in crate::api) struct ClientHandle {
    pub(in crate::api) client: Client,
    pub(in crate::api) handle: SenderPoolFatHandle,
    /// What Telegram pushes down this connection, until the event listener
    /// takes it. One per sender pool, for the pool's life: the pool's own
    /// reconnects reuse it, and only a new `Core` brings a new one.
    updates: Option<UnboundedReceiver<UpdatesLike>>,
    // Never polled again after `connect`, but dropping it would detach the
    // runner from anything keeping it alive for the compiler's purposes;
    // `Drop` below is what actually stops it.
    _pool_task: JoinHandle<()>,
}

impl Drop for ClientHandle {
    fn drop(&mut self) {
        self.handle.quit();
    }
}

/// Reads the persisted `(dc_id, auth_key)` pair, if a login has ever
/// completed. Any read failure — missing file, wrong length — is reported as
/// simply "not logged in yet" rather than an error: a first launch looks
/// exactly like a corrupt one from here, and both mean "log in".
pub(in crate::api) fn load_auth_key(data_dir: &Path) -> Option<(i32, [u8; AUTH_KEY_LEN])> {
    let bytes = std::fs::read(data_dir.join(SESSION_FILE)).ok()?;
    if bytes.len() != 4 + AUTH_KEY_LEN {
        return None;
    }
    let dc_id = i32::from_be_bytes(bytes[..4].try_into().ok()?);
    let key: [u8; AUTH_KEY_LEN] = bytes[4..].try_into().ok()?;
    Some((dc_id, key))
}

/// Writes the pair, owner-only: this file is the whole authorization.
///
/// `create_new` with the mode baked into the open call, not a plain `write`
/// followed by a `chmod`: the latter has a window, however short, where the
/// file exists at the process's default (world-readable) mode. A relogin
/// removes the old file first rather than truncating it in place, so that
/// window never opens on a second write either.
fn store_auth_key(data_dir: &Path, dc_id: i32, key: &[u8; AUTH_KEY_LEN]) -> Result<(), CoreError> {
    std::fs::create_dir_all(data_dir)
        .map_err(CoreError::io("creating the data directory"))?;
    let path = data_dir.join(SESSION_FILE);
    let mut bytes = Vec::with_capacity(4 + AUTH_KEY_LEN);
    bytes.extend_from_slice(&dc_id.to_be_bytes());
    bytes.extend_from_slice(key);

    let open = || {
        std::fs::OpenOptions::new()
            .write(true)
            .mode(0o600)
            .create_new(true)
            .open(&path)
    };
    let mut file = match open() {
        Ok(file) => file,
        Err(err) if err.kind() == std::io::ErrorKind::AlreadyExists => {
            std::fs::remove_file(&path)
                .map_err(CoreError::io("replacing the session"))?;
            open().map_err(CoreError::io("writing the session"))?
        }
        Err(err) => return Err(CoreError::io("writing the session")(err)),
    };
    file.write_all(&bytes)
        .map_err(CoreError::io("writing the session"))
}

/// A fresh bootstrap session before any login, or one seeded with the
/// persisted auth key after.
fn session_data(data_dir: &Path) -> SessionData {
    let mut data = SessionData::default();
    if let Some((dc_id, key)) = load_auth_key(data_dir) {
        data.home_dc = dc_id;
        if let Some(option) = data.dc_options.get_mut(&dc_id) {
            option.auth_key = Some(key);
        }
    }
    data
}

/// Connects a client against the core's persisted session — or a bootstrap
/// one, before any login — and starts its sender pool, named for the device.
pub(in crate::api) fn connect(core: &Core) -> ClientHandle {
    let session = Arc::new(MemorySession::from(session_data(&core.data_dir)));
    let params = crate::connection_params::connection_params("Android", &core.device_name);
    let SenderPool { runner, handle, updates } = SenderPool::with_configuration(session, core.api_id, params);
    let client = Client::new(handle.clone());
    let pool_task = tokio::spawn(runner.run());
    ClientHandle {
        client,
        handle,
        updates: Some(updates),
        _pool_task: pool_task,
    }
}

/// The one connection this `Core` keeps, opening it on first demand.
///
/// Every call that talks to Telegram goes through here rather than
/// connecting for itself: a second connection would be a second sender pool
/// over the same auth key, and grammers treats one key served to two clients
/// as the two breaking each other until a restart.
pub(in crate::api) async fn client(core: &Core) -> grammers_client::Client {
    let mut state = core.state.lock().await;
    if state.client.is_none() {
        state.client = Some(connect(core));
    }
    state.client.as_ref().expect("just set").client.clone()
}

/// The connection's client together with its update receiver, opening the
/// connection on first demand like [`client`]. The receiver is handed out
/// once per connection; `None` means a listener already holds it.
pub(in crate::api) async fn updates_receiver(
    core: &Core,
) -> (grammers_client::Client, Option<UnboundedReceiver<UpdatesLike>>) {
    let mut state = core.state.lock().await;
    let live = state
        .client
        .get_or_insert_with(|| connect(core));
    (live.client.clone(), live.updates.take())
}

/// Persists whatever auth key the session now holds for its home
/// datacentre. Called right after a sign-in or password check succeeds.
pub(in crate::api) fn persist(handle: &SenderPoolFatHandle, data_dir: &Path) -> Result<(), CoreError> {
    let dc_id = handle
        .session
        .home_dc_id()
        .map_err(CoreError::io("reading the session"))?;
    let key = handle
        .session
        .dc_option(dc_id)
        .map_err(CoreError::io("reading the session"))?
        .and_then(|option| option.auth_key)
        .ok_or_else(|| CoreError::NotAuthorized("sign-in did not yield an auth key".into()))?;
    store_auth_key(data_dir, dc_id, &key)
}

/// Removes the persisted key, after Telegram has said it no longer honours
/// it. Already gone counts as done.
pub(in crate::api) fn forget_auth_key(data_dir: &Path) -> std::io::Result<()> {
    match std::fs::remove_file(data_dir.join(SESSION_FILE)) {
        Err(err) if err.kind() != std::io::ErrorKind::NotFound => Err(err),
        _ => Ok(()),
    }
}

#[cfg(test)]
#[path = "session_tests.rs"]
mod tests;
