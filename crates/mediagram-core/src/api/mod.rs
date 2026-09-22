//! The surface Kotlin calls through UniFFI: signing in, choosing and
//! refreshing a library, reading bytes, and fetching what a library lacks.
//!
//! The machinery lives in this crate's other modules — range planning,
//! catalog queries, the byte transport, catalog versions, package
//! decryption, the `shows` stores. What stays here is each call's
//! orchestration over one `Core`, for a caller that never sees a
//! `Connection` or a `Client` and never learns a `chat_id`, a `message_id`
//! or a `doc_id`: no [`CoreError`] variant may carry one, because the player
//! is told what it may play, never where the bytes live.

mod account;
mod blocking;
mod store;
mod channel;
pub mod enrich;
mod read;
mod refresh;

use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::Mutex as AsyncMutex;

use account::auth::{PendingLogin, PendingPassword};
use account::session::ClientHandle;
use crate::transport::documents::PartDocuments;
pub use crate::error::CoreError;

/// Outcome of a completed sign-in step.
#[derive(Debug, Clone, Copy, PartialEq, Eq, uniffi::Enum)]
pub enum AuthOutcome {
    Done,
    PasswordNeeded,
}

/// One library the signed-in account could choose, as the caller sees it.
///
/// A title to render and a handle to send back, and nothing else. The handle
/// is a random name this data directory minted for the channel — see
/// `library` — so a caller holding one learns nothing about where the
/// bytes live, which is the same rule the byte path is held to.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LibraryChoice {
    pub handle: String,
    pub title: String,
}


/// State a running app keeps between calls: the connection once opened,
/// whichever login step is in flight, and where the set being played lives.
#[derive(Default)]
struct State {
    client: Option<ClientHandle>,
    pending_login: Option<PendingLogin>,
    pending_password: Option<PendingPassword>,
    documents: Arc<PartDocuments>,
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
    /// Held by whichever refresh is installing a catalog, so two cannot
    /// assemble in the same staging directory at once.
    installing: AsyncMutex<()>,
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
            installing: AsyncMutex::new(()),
        })
    }

    /// Whether a login has ever completed. Reads the persisted auth key
    /// only: cheap, and needs no connection.
    pub fn is_authorized(&self) -> bool {
        account::session::load_auth_key(&self.data_dir).is_some()
    }

    pub async fn request_code(&self, phone: String) -> Result<String, CoreError> {
        account::auth::request_code(self, phone).await
    }

    pub async fn sign_in(&self, token: String, code: String) -> Result<AuthOutcome, CoreError> {
        account::auth::sign_in(self, token, code).await
    }

    pub async fn check_password(&self, password: String) -> Result<(), CoreError> {
        account::auth::check_password(self, password).await
    }

    /// The libraries this account could choose from — its broadcast
    /// channels, in the order Telegram itself lists them: pinned
    /// conversations first, then most recent.
    pub async fn list_libraries(&self) -> Result<Vec<LibraryChoice>, CoreError> {
        channel::list_libraries(self).await
    }

    /// Refreshes from **the channel**: installs the newest index snapshot the
    /// chosen library's channel holds, and answers how many sets it holds.
    /// The first install and every later refresh are the same call.
    pub async fn refresh_library(&self, handle: String) -> Result<u64, CoreError> {
        channel::refresh_library(self, handle).await
    }

    /// Refreshes from **a published package**: fetches the pointer at
    /// `pointer_url`, then the encrypted package it names, and installs the
    /// index inside it. Kept whole beside [`Core::refresh_library`]: it is
    /// the only source that carries poster art, though nothing in the
    /// first-run flow reaches it any more.
    pub async fn refresh_catalog(
        &self,
        pointer_url: String,
        key_b64: String,
    ) -> Result<u64, CoreError> {
        refresh::refresh_catalog(self, pointer_url, key_b64).await
    }

    /// Every playable set in the current catalog. An empty list, not
    /// `NotFound`, when no catalog is loaded yet: a shelf with nothing on it
    /// is what a first launch shows, whereas the calls that ask about one
    /// named set have nothing sensible to return and say so.
    pub async fn list_sets(self: Arc<Self>) -> Result<Vec<crate::dto::SetSummary>, CoreError> {
        self.blocking(store::list_sets).await
    }

    /// Where a poster's image is on disk, if it is. Sync, unlike the catalog
    /// reads: two `stat`s and no SQLite, which Kotlin already runs off-main.
    pub fn poster_path(&self, poster_key: String) -> Option<String> {
        store::poster_path(self, poster_key)
    }

    /// What is known about a title, or nothing. The index answers first and
    /// what this device fetched fills the gaps — see `enrich::details::title_info`.
    /// A course has no provider entry and a library assembled without a TMDB
    /// key has no rows at all; both are ordinary, so neither is an error.
    pub async fn title_info(self: Arc<Self>, poster_key: String) -> Option<crate::dto::TitleInfo> {
        self.blocking(move |core| enrich::details::title_info(core, poster_key)).await
    }

    pub async fn total_size(self: Arc<Self>, set_id: String) -> Result<u64, CoreError> {
        self.blocking(move |core| store::total_size(core, set_id)).await
    }

    /// What the installed catalog is, for the screen that says so.
    ///
    /// Total failure is reported as zeroes rather than an error: this is
    /// read to draw a screen, and a screen that cannot draw because a count
    /// failed is worse than one that says a library is empty.
    pub async fn catalog_facts(self: Arc<Self>) -> crate::dto::CatalogFacts {
        self.blocking(store::facts).await
    }

    pub async fn read(self: Arc<Self>, set_id: String, offset: u64, len: u32) -> Result<Vec<u8>, CoreError> {
        let locations = self.blocking(move |core| read::locations(core, &set_id)).await?;
        read::read(&self, locations, offset, len).await
    }

    /// Fills in what the library it was handed does not carry, for every
    /// title TMDB can answer about: the poster artwork a channel index has
    /// no room for, and the descriptions of whatever nobody ran `mediagram
    /// metadata` over before pushing it. One run answers both, because they
    /// come from one request per title and a viewer who asked for the
    /// missing pieces did not ask for half of them.
    ///
    /// `language` is only a fallback: the library itself says what language
    /// it was described in, and that is what the provider is asked in.
    ///
    /// The key is used for this call only and never stored — Kotlin owns
    /// holding it, this crate only ever spends it.
    pub async fn fetch_missing(
        &self,
        tmdb_key: String,
        language: String,
    ) -> Result<crate::dto::FetchReport, CoreError> {
        enrich::artwork::fetch_missing(self, tmdb_key, language).await
    }
}
