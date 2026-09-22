//! Keeping local, blocking work — SQLite, the file system — off the async
//! executor. Kotlin awaits these calls on the same runtime that pumps
//! Telegram downloads, so a slow disk under a catalog query must not stall
//! a read that is streaming a film.

use std::sync::Arc;

use super::Core;

impl Core {
    /// Runs `work` on tokio's blocking pool and awaits it.
    pub(super) async fn blocking<T: Send + 'static>(
        self: &Arc<Self>,
        work: impl FnOnce(&Core) -> T + Send + 'static,
    ) -> T {
        let core = Arc::clone(self);
        tokio::task::spawn_blocking(move || work(&core))
            .await
            // Nothing aborts these tasks, so a join error is `work` having
            // panicked: carried on here as if it had run inline.
            .unwrap_or_else(|err| std::panic::resume_unwind(err.into_panic()))
    }
}
