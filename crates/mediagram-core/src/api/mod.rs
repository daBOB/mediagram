//! Kotlin's UniFFI surface: authentication, library refresh, playback and
//! enrichment. Calls orchestrate domain modules over one [`Core`].
//!
//! Kotlin never receives connections, clients, or Telegram chat/message/doc
//! IDs, including through [`CoreError`]: the player learns what it can play,
//! never where the bytes live.

mod account;
mod blocking;
mod channel;
pub mod enrich;
mod events;
mod preferences;
mod read;
mod refresh;
mod search;
mod set_text;
mod state;
mod state_sync;
#[cfg(test)]
mod test_support;
mod store;

pub use crate::dto::{AuthOutcome, LibraryChoice};
pub use crate::error::CoreError;
use crate::transport::documents::PartDocuments;
use account::auth::{PendingLogin, PendingPassword};
use account::session::ClientHandle;
use std::path::PathBuf;
use std::sync::Arc;
use tokio::sync::Mutex as AsyncMutex;

/// State a running app keeps between calls: the connection once opened,
/// whichever login step is in flight, and where the set being played lives.
#[derive(Default)]
struct State {
    client: Option<ClientHandle>,
    login_attempt: Option<Arc<()>>,
    pending_login: Option<PendingLogin>,
    pending_password: Option<PendingPassword>,
    documents: Arc<PartDocuments>,
}

/// One player's whole Telegram surface, kept alive by Kotlin for the life of
/// the app.
///
/// `api_id`/`api_hash` identify the Telegram application, not a signed-in
/// account. Kotlin passes the identity stored by the app's setup flow.
#[derive(uniffi::Object)]
pub struct Core {
    data_dir: PathBuf,
    api_id: i32,
    /// This device's name in the account's session list; only Kotlin knows it.
    device_name: String,
    api_hash: String,
    state: AsyncMutex<State>,
    /// Held by whichever refresh is installing a catalog, so two cannot
    /// assemble in the same staging directory at once.
    installing: AsyncMutex<()>,
    /// The update listener, apart from `state` so waiting never blocks a read.
    events: AsyncMutex<Option<events::Listener>>,
    /// Positions, watched marks, the watchlist, collections and Kids — its
    /// own file beside `catalog/`, opened lazily. See `crate::state`.
    state_db: crate::state::StateDb,
    /// One state-sync round at a time, held for the round; see `state::sync::SyncMemo`.
    sync_memo: AsyncMutex<crate::state::sync::SyncMemo>,
    /// The folded catalog `Core::search` ranks against — blocking, like `state_db`.
    search_cache: std::sync::Mutex<search::cache::SearchCache>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    #[uniffi::constructor]
    pub fn new(data_dir: String, api_id: i32, api_hash: String, device_name: String) -> Arc<Self> {
        let data_dir = PathBuf::from(data_dir);
        Arc::new(Core {
            state_db: crate::state::StateDb::new(data_dir.clone()),
            data_dir,
            api_id,
            device_name,
            api_hash,
            state: AsyncMutex::new(State::default()),
            installing: AsyncMutex::new(()),
            events: AsyncMutex::new(None),
            sync_memo: AsyncMutex::new(crate::state::sync::SyncMemo::default()),
            search_cache: std::sync::Mutex::new(search::cache::SearchCache::default()),
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

    /// Installs the encrypted package named by `pointer_url`, including its
    /// poster art. Unlike [`Core::refresh_library`], this source is not used
    /// by the first-run flow.
    pub async fn refresh_catalog(
        &self,
        pointer_url: String,
        key_b64: String,
    ) -> Result<u64, CoreError> {
        refresh::refresh_catalog(self, pointer_url, key_b64).await
    }

    /// Every playable set in the current catalog, or an empty list before
    /// the first catalog is loaded. Calls for a named set use `NotFound`.
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
        self.blocking(move |core| enrich::details::title_info(core, poster_key))
            .await
    }

    pub async fn total_size(self: Arc<Self>, set_id: String) -> Result<u64, CoreError> {
        self.blocking(move |core| store::total_size(core, set_id))
            .await
    }

    /// Installed-catalog status; unavailable counts are reported as zeroes
    /// so the display remains usable when its local data cannot be read.
    pub async fn catalog_facts(self: Arc<Self>) -> crate::dto::CatalogFacts {
        self.blocking(store::facts).await
    }

    /// Reads at most `len` bytes from `offset`, clamping the result at EOF.
    /// `NotFound` covers absent/unplayable sets or offsets at/beyond EOF, even
    /// for `len == 0`. An in-range empty request returns no bytes without
    /// resolving channels. Nonempty reads also return `NotFound` for missing
    /// channel addresses. Storage, transport and authorization errors propagate;
    /// failed downloads never return a partial buffer.
    pub async fn read(
        self: Arc<Self>,
        set_id: String,
        offset: u64,
        len: u32,
    ) -> Result<Vec<u8>, CoreError> {
        let locations = self
            .blocking(move |core| read::locations(core, &set_id))
            .await?;
        read::read(&self, locations, offset, len).await
    }

    /// Fetches missing TMDB posters and descriptions together, once per title.
    ///
    /// The library's language takes precedence over the `language` fallback.
    ///
    /// Kotlin owns the key; this call uses it without storing it.
    pub async fn fetch_missing(
        &self,
        tmdb_key: String,
        language: String,
    ) -> Result<crate::dto::FetchReport, CoreError> {
        enrich::artwork::fetch_missing(self, tmdb_key, language).await
    }
}
