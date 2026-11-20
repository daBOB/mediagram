//! A position write and the watch time it adds. Split out to keep `state.rs`
//! under the line limit; the counting rules live in `crate::state::stats`.

use std::sync::Arc;

use crate::state::profiles;

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// This device's own position write, and the watch time it adds to the
    /// viewer's stats. `local_day` is today where the viewer is, `YYYY-MM-DD`
    /// (Kotlin's `LocalDate.now()`) — the day that watch time counts on.
    pub async fn set_progress(
        self: Arc<Self>,
        profile_id: String,
        set_id: String,
        at: f64,
        duration: Option<f64>,
        local_day: String,
    ) {
        self.blocking(move |core| {
            let now = profiles::now_ms();
            // Listening to a book is not screen time: it keeps its title's
            // total but stays out of the day a kid's limit is counted on.
            let counts_today = !crate::api::store::is_book(core, &set_id);
            core.state_db.set_progress_counted(
                &profile_id,
                &set_id,
                at,
                duration,
                &local_day,
                counts_today,
                now,
            )
        })
        .await;
    }
}
