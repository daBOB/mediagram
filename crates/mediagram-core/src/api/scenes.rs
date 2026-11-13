//! `Core::mark_scene`/`Core::remove_scene`: the calls across the boundary for
//! `crate::state::scenes`, which holds every rule. Split from `api::state`
//! to keep that file under the line limit.

use std::sync::Arc;

use crate::state::scenes;

use super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Marks a scene: a moment of `set_id`, `at` film seconds in. The new
    /// mark's id, or `None` for an `at` that is no film second or a profile
    /// that is gone.
    pub async fn mark_scene(
        self: Arc<Self>,
        profile_id: String,
        set_id: String,
        at: f64,
    ) -> Option<String> {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| scenes::mark(conn, &profile_id, &set_id, at))
                .flatten()
        })
        .await
    }

    /// Takes a scene mark off. `false` when no live mark has that id.
    pub async fn remove_scene(self: Arc<Self>, profile_id: String, id: String) -> bool {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| scenes::remove(conn, &profile_id, &id))
                .unwrap_or(false)
        })
        .await
    }
}
