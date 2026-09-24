//! Hand-built collections: create, rename, delete, and add or remove a
//! title. Split out only to keep `state.rs` under the line limit — every
//! rule still lives in `crate::state::lists`, pinned to the web by the
//! shared fixtures.

use std::sync::Arc;

use crate::state::lists;

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    pub async fn create_collection(
        self: Arc<Self>,
        profile_id: String,
        name: String,
    ) -> Option<lists::ListRow> {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| lists::create(conn, &profile_id, &name))
                .flatten()
        })
        .await
    }

    /// `false` for a blank name, an unavailable list, or a storage failure.
    pub async fn rename_collection(
        self: Arc<Self>,
        profile_id: String,
        id: String,
        name: String,
    ) -> bool {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| lists::rename(conn, &profile_id, &id, &name))
                .unwrap_or(false)
        })
        .await
    }

    /// Tombstones the list, retaining its items for sync reconciliation.
    pub async fn delete_collection(self: Arc<Self>, profile_id: String, id: String) -> bool {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| lists::delete(conn, &profile_id, &id))
                .unwrap_or(false)
        })
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
