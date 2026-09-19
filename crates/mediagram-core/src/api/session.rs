//! Persisting the account's authorization across launches.
//!
//! Android drops `SqliteSession` from the build (see the crate's
//! `Cargo.toml`), and an in-memory session remembers nothing between one
//! process and the next — without this, every launch would repeat the whole
//! phone/code/2FA dance, and grammers warns that doing so risks a flood
//! wait. The auth key is the whole authorization, so persistence is just two
//! numbers: which datacentre it belongs to, and the 256 bytes themselves.

use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::sync::Arc;

use grammers_client::Client;
use grammers_mtsender::{SenderPool, SenderPoolFatHandle};
use grammers_session::SessionData;
use grammers_session::storages::MemorySession;
use tokio::task::JoinHandle;

use super::CoreError;

const SESSION_FILE: &str = "session.key";
const AUTH_KEY_LEN: usize = 256;

/// A live connection, kept for the app's lifetime so a later call reuses it
/// instead of paying for a fresh one.
pub(super) struct ClientHandle {
    pub(super) client: Client,
    pub(super) handle: SenderPoolFatHandle,
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
pub(super) fn load_auth_key(data_dir: &Path) -> Option<(i32, [u8; AUTH_KEY_LEN])> {
    let bytes = std::fs::read(data_dir.join(SESSION_FILE)).ok()?;
    if bytes.len() != 4 + AUTH_KEY_LEN {
        return None;
    }
    let dc_id = i32::from_be_bytes(bytes[..4].try_into().ok()?);
    let key: [u8; AUTH_KEY_LEN] = bytes[4..].try_into().ok()?;
    Some((dc_id, key))
}

/// Writes the pair, owner-only: this file is the whole authorization.
fn store_auth_key(data_dir: &Path, dc_id: i32, key: &[u8; AUTH_KEY_LEN]) -> Result<(), CoreError> {
    std::fs::create_dir_all(data_dir)
        .map_err(|_| CoreError::Io("creating the data directory".into()))?;
    let path = data_dir.join(SESSION_FILE);
    let mut bytes = Vec::with_capacity(4 + AUTH_KEY_LEN);
    bytes.extend_from_slice(&dc_id.to_be_bytes());
    bytes.extend_from_slice(key);
    std::fs::write(&path, &bytes).map_err(|_| CoreError::Io("writing the session".into()))?;
    std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o600))
        .map_err(|_| CoreError::Io("restricting the session".into()))
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

/// Connects a client against `data_dir`'s persisted session — or a
/// bootstrap one, before any login — and starts its sender pool.
pub(super) fn connect(data_dir: &Path, api_id: i32) -> ClientHandle {
    let session = Arc::new(MemorySession::from(session_data(data_dir)));
    let SenderPool { runner, handle, .. } = SenderPool::new(session, api_id);
    let client = Client::new(handle.clone());
    let pool_task = tokio::spawn(runner.run());
    ClientHandle {
        client,
        handle,
        _pool_task: pool_task,
    }
}

/// Persists whatever auth key the session now holds for its home
/// datacentre. Called right after a sign-in or password check succeeds.
pub(super) fn persist(handle: &SenderPoolFatHandle, data_dir: &Path) -> Result<(), CoreError> {
    let dc_id = handle
        .session
        .home_dc_id()
        .map_err(|_| CoreError::Io("reading the session".into()))?;
    let key = handle
        .session
        .dc_option(dc_id)
        .map_err(|_| CoreError::Io("reading the session".into()))?
        .and_then(|option| option.auth_key)
        .ok_or_else(|| CoreError::NotAuthorized("sign-in did not yield an auth key".into()))?;
    store_auth_key(data_dir, dc_id, &key)
}
