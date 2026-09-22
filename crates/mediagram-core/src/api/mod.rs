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

mod account;
mod store;
mod channel;
pub mod enrich;
pub mod http;
mod read;
mod refresh;

use std::path::PathBuf;
use std::sync::Arc;

use tokio::sync::Mutex as AsyncMutex;

use account::auth::{PendingLogin, PendingPassword};
use account::session::ClientHandle;

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
/// [`library`] — so a caller holding one learns nothing about where the
/// bytes live, which is the same rule the byte path is held to.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct LibraryChoice {
    pub handle: String,
    pub title: String,
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
    /// The chosen channel does not hold one readable index. Its own variant
    /// because it is neither a network fault nor a missing file: the channel
    /// answered, and what it holds is not a library yet — which is something
    /// a person can go and fix.
    #[error("library error: {0}")]
    Library(String),
}

impl CoreError {
    /// `Io(what)` for a failure whose cause is logged rather than returned.
    ///
    /// The message Kotlin sees stays the plain `what`: no variant may carry a
    /// chat, message or document id, and a cause is free to name one. The
    /// cause still matters to whoever diagnoses the failure — permission
    /// denied and disk full read the same without it — so it goes to the log.
    pub(crate) fn io<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(%cause, "{what}");
            CoreError::Io(what.into())
        }
    }

    /// [`CoreError::io`]'s counterpart for a failure on the network.
    pub(crate) fn network<E: std::fmt::Display>(what: &str) -> impl FnOnce(E) -> CoreError + '_ {
        move |cause| {
            tracing::warn!(%cause, "{what}");
            CoreError::Network(what.into())
        }
    }
}

/// State a running app keeps between calls: the connection once opened,
/// whichever login step is in flight, and where the set being played lives.
#[derive(Default)]
struct State {
    client: Option<ClientHandle>,
    pending_login: Option<PendingLogin>,
    pending_password: Option<PendingPassword>,
    documents: DocumentCache,
}

/// Which Telegram document each part of one set is, resolved once.
///
/// Resolving costs a round trip, and a set is read a few hundred times
/// while it plays — every one of them for the same handful of parts. The
/// document a message holds is fixed, so the answer cannot turn into
/// different bytes. The file reference inside the handle does expire, which
/// is why a read that fails on a held handle forgets it and resolves the
/// part again before giving up.
///
/// Held for one set, because that is what a player reads. Opening another
/// replaces it, which is what keeps this from growing with every set ever
/// played.
#[derive(Default)]
struct DocumentCache {
    set_id: String,
    by_message: std::collections::HashMap<i64, grammers_client::media::Document>,
}

impl DocumentCache {
    fn get(&self, set_id: &str, message_id: i64) -> Option<grammers_client::media::Document> {
        if self.set_id != set_id {
            return None;
        }
        self.by_message.get(&message_id).cloned()
    }

    fn forget(&mut self, set_id: &str, message_id: i64) {
        if self.set_id == set_id {
            self.by_message.remove(&message_id);
        }
    }

    fn put(&mut self, set_id: &str, message_id: i64, document: grammers_client::media::Document) {
        if self.set_id != set_id {
            self.set_id = set_id.to_string();
            self.by_message.clear();
        }
        self.by_message.insert(message_id, document);
    }
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
    pub fn list_sets(&self) -> Result<Vec<crate::dto::SetSummary>, CoreError> {
        store::list_sets(self)
    }

    pub fn poster_path(&self, poster_key: String) -> Option<String> {
        store::poster_path(self, poster_key)
    }

    /// What is known about a title, or nothing. The index answers first and
    /// what this device fetched fills the gaps — see [`enrich::details::show_info`].
    /// A course has no provider entry and a library assembled without a TMDB
    /// key has no rows at all; both are ordinary, so neither is an error.
    pub fn show_info(&self, poster_key: String) -> Option<crate::dto::ShowInfo> {
        enrich::details::show_info(self, poster_key)
    }

    pub fn total_size(&self, set_id: String) -> Result<u64, CoreError> {
        store::total_size(self, set_id)
    }

    /// What the installed catalog is, for the screen that says so.
    ///
    /// Total failure is reported as zeroes rather than an error: this is
    /// read to draw a screen, and a screen that cannot draw because a count
    /// failed is worse than one that says a library is empty.
    pub fn catalog_facts(&self) -> crate::dto::CatalogFacts {
        store::facts(self)
    }

    pub async fn read(&self, set_id: String, offset: u64, len: u32) -> Result<Vec<u8>, CoreError> {
        read::read(self, set_id, offset, len).await
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

#[cfg(test)]
mod tests {
    use super::*;

    /// The emptiest document the bindings will build. This cache stores
    /// whatever it is handed and never looks inside, so what it holds does
    /// not matter here — only which key answers with it.
    fn document() -> grammers_client::media::Document {
        use grammers_tl_types as tl;
        grammers_client::media::Document::from_raw_media(tl::types::MessageMediaDocument {
            nopremium: false,
            spoiler: false,
            video: false,
            round: false,
            voice: false,
            document: None,
            alt_documents: None,
            video_cover: None,
            video_timestamp: None,
            ttl_seconds: None,
        })
    }

    /// The cache exists because resolving a part is a round trip and a
    /// playing set asks for the same handful of parts a few hundred times.
    #[test]
    fn a_part_resolved_once_is_answered_from_memory_after() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        assert!(cache.get("set-a", 100).is_some());
    }

    #[test]
    fn a_part_never_resolved_is_not_guessed_at() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        assert!(cache.get("set-a", 999).is_none());
    }

    /// Two sets can hold the same message id only by accident, and
    /// answering one set's read with another's document would serve the
    /// wrong film's bytes. Opening a set is also what bounds this: without
    /// it the map would grow with every set ever played.
    #[test]
    fn opening_another_set_forgets_the_one_before_it() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        cache.put("set-b", 200, document());

        assert!(cache.get("set-a", 100).is_none(), "one set's parts must not answer another's");
        assert!(cache.get("set-b", 200).is_some());
    }

    /// A handle whose file reference has expired is forgotten on its own:
    /// the next read resolves that part afresh, and the rest stay held.
    #[test]
    fn a_forgotten_part_is_resolved_again_and_the_others_stay_held() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());
        cache.put("set-a", 101, document());

        cache.forget("set-a", 100);
        cache.forget("set-b", 101);

        assert!(cache.get("set-a", 100).is_none());
        assert!(cache.get("set-a", 101).is_some(), "forgetting another set's part touches nothing");
    }
}
