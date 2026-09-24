//! The watch-state surface Kotlin calls: profiles, positions, the
//! watchlist, Kids and collections. Thin wrappers over `crate::state` —
//! every rule lives there, pinned to the web by the shared fixtures; this
//! file only carries calls across the boundary.
//!
//! **Nothing here throws.** A write that could not be made is logged inside
//! `StateDb::with` and reported back as the value that means "nothing
//! happened" — `false`, `None`, or an unmodified read — never a `CoreError`:
//! a position that failed to save is a bad afternoon, and a player that
//! stops because of it is a broken one.

use std::sync::Arc;

use crate::state::{lists, profiles, rows};

use super::Core;

/// One profile's everything, in one read. The page asks once and holds it.
#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct StateSnapshot {
    pub progress: Vec<rows::ProgressRow>,
    pub watched: Vec<rows::WatchedRow>,
    pub watchlist: Vec<String>,
    pub kids: Vec<String>,
    pub collections: Vec<lists::ListRow>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Who watches this library. Empty until someone says.
    pub async fn profiles(self: Arc<Self>) -> Vec<profiles::Profile> {
        self.blocking(|core| core.state_db.with(profiles::list).unwrap_or_default()).await
    }

    pub async fn create_profile(self: Arc<Self>, name: String) -> Option<profiles::Profile> {
        self.blocking(move |core| core.state_db.with(|conn| profiles::create(conn, &name)).flatten()).await
    }

    /// This install's remembered "who's watching".
    pub async fn chosen_profile(self: Arc<Self>) -> Option<String> {
        self.blocking(|core| core.state_db.with(profiles::chosen).flatten()).await
    }

    /// Records the choice. `false` when `id` names no profile, or nothing
    /// could be written.
    pub async fn choose_profile(self: Arc<Self>, id: String) -> bool {
        self.blocking(move |core| core.state_db.with(|conn| profiles::choose(conn, &id)).unwrap_or(false)).await
    }

    /// Takes everything that was theirs with it — every table cascades.
    /// `chosen_profile` clears itself the moment this was the profile it
    /// named: see `profiles::chosen`, which checks a profile still exists on
    /// every read rather than trusting what was last written.
    pub async fn delete_profile(self: Arc<Self>, id: String) -> bool {
        self.blocking(move |core| core.state_db.with(|conn| profiles::delete(conn, &id)).unwrap_or(false)).await
    }

    /// This profile's positions, watched marks, watchlist, Kids and
    /// collections, in one round trip. Empty throughout on any failure.
    pub async fn snapshot(self: Arc<Self>, profile_id: String) -> StateSnapshot {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| {
                    Ok(StateSnapshot {
                        progress: rows::progress_for(conn, &profile_id)?,
                        watched: rows::watched_for(conn, &profile_id)?,
                        watchlist: rows::watchlist_for(conn, &profile_id)?,
                        kids: rows::kids(conn)?,
                        collections: lists::collections_for(conn, &profile_id)?,
                    })
                })
                .unwrap_or_default()
        })
        .await
    }

    pub async fn set_progress(self: Arc<Self>, profile_id: String, set_id: String, at: f64, duration: Option<f64>) {
        self.blocking(move |core| {
            core.state_db.with(|conn| rows::set_progress(conn, &profile_id, &set_id, at, duration))
        })
        .await;
    }

    /// Forgets a position: started again, or watched to the end.
    pub async fn clear_progress(self: Arc<Self>, profile_id: String, set_id: String) {
        self.blocking(move |core| core.state_db.with(|conn| rows::clear_progress(conn, &profile_id, &set_id)))
            .await;
    }

    pub async fn set_watched(self: Arc<Self>, profile_id: String, set_id: String, finished: bool) {
        self.blocking(move |core| {
            core.state_db.with(|conn| rows::set_watched(conn, &profile_id, &set_id, finished))
        })
        .await;
    }

    pub async fn set_watchlisted(self: Arc<Self>, profile_id: String, set_id: String, listed: bool) {
        self.blocking(move |core| {
            core.state_db.with(|conn| rows::set_watchlisted(conn, &profile_id, &set_id, listed))
        })
        .await;
    }

    /// Marks (or unmarks) a title as a child's. Not scoped to a profile —
    /// see `state::schema` on why.
    pub async fn set_kids(self: Arc<Self>, set_id: String, marked: bool) {
        self.blocking(move |core| core.state_db.with(|conn| rows::set_kids(conn, &set_id, marked))).await;
    }

    pub async fn create_collection(self: Arc<Self>, profile_id: String, name: String) -> Option<lists::ListRow> {
        self.blocking(move |core| core.state_db.with(|conn| lists::create(conn, &profile_id, &name)).flatten())
            .await
    }

    /// Whether the list was there to rename.
    pub async fn rename_collection(self: Arc<Self>, profile_id: String, id: String, name: String) -> bool {
        self.blocking(move |core| {
            core.state_db.with(|conn| lists::rename(conn, &profile_id, &id, &name)).unwrap_or(false)
        })
        .await
    }

    /// Items go with it: `collection_items` cascades.
    pub async fn delete_collection(self: Arc<Self>, profile_id: String, id: String) -> bool {
        self.blocking(move |core| core.state_db.with(|conn| lists::delete(conn, &profile_id, &id)).unwrap_or(false))
            .await
    }

    /// Adds or removes `set_id` from a collection. `false` when the list is
    /// not this profile's.
    pub async fn set_in_collection(
        self: Arc<Self>,
        profile_id: String,
        id: String,
        set_id: String,
        included: bool,
    ) -> bool {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| lists::set_in_collection(conn, &profile_id, &id, &set_id, included))
                .unwrap_or(false)
        })
        .await
    }
}
