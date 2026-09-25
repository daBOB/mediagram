//! `Core::preferences`/`Core::set_preference`: what a viewer chose for a
//! show, so they do not choose it again. A thin wrapper over
//! `crate::state::preferences` — every rule lives there; this file only
//! carries calls across the boundary, the split `api::state` already uses
//! for the rest of `state.db`.

use std::sync::Arc;

use crate::state::preferences;

use super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Every choice this profile has made, in one round trip: there are a
    /// handful of these per show, and a page needs one the instant a title
    /// opens — exactly when it has no time to ask for it.
    pub async fn preferences(self: Arc<Self>, profile_id: String) -> Vec<preferences::PreferenceRow> {
        self.blocking(move |core| {
            core.state_db.with(|conn| preferences::list_for(conn, &profile_id)).unwrap_or_default()
        })
        .await
    }

    /// Remembers a choice, or forgets it (`value: None`). `false` when
    /// `scope` or `name` has nothing left after trimming, or nothing could
    /// be written.
    pub async fn set_preference(
        self: Arc<Self>,
        profile_id: String,
        scope: String,
        name: String,
        value: Option<String>,
    ) -> bool {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| preferences::set(conn, &profile_id, &scope, &name, value.as_deref()))
                .unwrap_or(false)
        })
        .await
    }
}
