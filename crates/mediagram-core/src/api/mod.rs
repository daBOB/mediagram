//! The narrow surface Kotlin calls through UniFFI: authentication, catalog
//! refresh, and byte reads.
//!
//! Everything that actually does the work — range planning, catalog
//! queries, the Telegram transport, package decryption — already lives in
//! this crate's other modules; `Core` only orchestrates them for a caller
//! that never sees a `Connection` or a `Client` directly, and never learns a
//! `chat_id`, a `message_id`, or a `doc_id`: no [`CoreError`] variant may
//! carry one, because the player is told what it may play, never where the
//! bytes live.

mod auth;
mod catalog;
mod read;
mod session;

use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::Mutex as AsyncMutex;

use auth::{PendingLogin, PendingPassword};
use session::ClientHandle;

/// Outcome of a completed sign-in step.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AuthOutcome {
    Done,
    PasswordNeeded,
}

/// Every error this surface can hand to Kotlin.
#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CoreError {
    #[error("network error: {0}")]
    Network(String),
    #[error("not authorized: {0}")]
    NotAuthorized(String),
    #[error("not found: {0}")]
    NotFound(String),
    #[error("cipher error: {0}")]
    Cipher(String),
    #[error("io error: {0}")]
    Io(String),
}

/// State a running app keeps between calls: the connection once opened, and
/// whichever login step is in flight.
#[derive(Default)]
struct State {
    client: Option<ClientHandle>,
    pending_login: Option<PendingLogin>,
    pending_password: Option<PendingPassword>,
}

/// One player's whole Telegram surface, kept alive by Kotlin for the life of
/// the app.
#[derive(uniffi::Object)]
pub struct Core {
    data_dir: PathBuf,
    state: AsyncMutex<State>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    #[uniffi::constructor]
    pub fn new(data_dir: String) -> Arc<Self> {
        Arc::new(Core {
            data_dir: PathBuf::from(data_dir),
            state: AsyncMutex::new(State::default()),
        })
    }

    /// Whether a login has ever completed. Reads the persisted auth key
    /// only: cheap, and needs no connection.
    pub fn is_authorized(&self) -> bool {
        session::load_auth_key(&self.data_dir).is_some()
    }

    pub async fn request_code(&self, phone: String) -> Result<String, CoreError> {
        auth::request_code(self, phone).await
    }

    pub async fn sign_in(&self, token: String, code: String) -> Result<AuthOutcome, CoreError> {
        auth::sign_in(self, token, code).await
    }

    pub async fn check_password(&self, password: String) -> Result<(), CoreError> {
        auth::check_password(self, password).await
    }

    pub async fn refresh_catalog(
        &self,
        pointer_url: String,
        key_b64: String,
    ) -> Result<u64, CoreError> {
        catalog::refresh_catalog(self, pointer_url, key_b64).await
    }

    pub fn list_sets(&self) -> Result<Vec<crate::dto::SetSummary>, CoreError> {
        catalog::list_sets(self)
    }

    pub fn poster_path(&self, poster_key: String) -> Option<String> {
        catalog::poster_path(self, poster_key)
    }

    pub fn total_size(&self, set_id: String) -> Result<u64, CoreError> {
        catalog::total_size(self, set_id)
    }

    pub async fn read(&self, set_id: String, offset: u64, len: u32) -> Result<Vec<u8>, CoreError> {
        read::read(self, set_id, offset, len).await
    }
}

/// The Telegram application's own identity, distinct from a user's session:
/// leaking these lets someone impersonate the application, never sign in as
/// an account. A personal build sets them once in its environment; there is
/// no on-device config file to read them from.
fn api_credentials() -> Result<(i32, String), CoreError> {
    let id = std::env::var("MEDIAGRAM_API_ID")
        .ok()
        .and_then(|v| v.parse::<i32>().ok())
        .ok_or_else(|| CoreError::Io("MEDIAGRAM_API_ID is not configured".into()))?;
    let hash = std::env::var("MEDIAGRAM_API_HASH")
        .map_err(|_| CoreError::Io("MEDIAGRAM_API_HASH is not configured".into()))?;
    Ok((id, hash))
}
