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
mod http;
mod read;
mod refresh;
mod refresh_fetch;
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
///
/// `api_id`/`api_hash` identify the *application* to Telegram, not the
/// account — leaking them lets someone impersonate the app, never sign in as
/// a user. An Android process has no settable environment to read them from,
/// so Kotlin passes them in from `BuildConfig`, itself populated at build
/// time from `local.properties`.
#[derive(uniffi::Object)]
pub struct Core {
    data_dir: PathBuf,
    api_id: i32,
    api_hash: String,
    state: AsyncMutex<State>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    #[uniffi::constructor]
    pub fn new(data_dir: String, api_id: i32, api_hash: String) -> Arc<Self> {
        Arc::new(Core {
            data_dir: PathBuf::from(data_dir),
            api_id,
            api_hash,
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
        refresh::refresh_catalog(self, pointer_url, key_b64).await
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
