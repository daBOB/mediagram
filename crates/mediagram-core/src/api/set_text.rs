//! `Core::set_text`: a summary already sitting in the index, the way the
//! uploader wrote it from the file beside each video. Never a Telegram
//! round trip.

use std::sync::Arc;

use crate::api::{Core, store};
use crate::catalog_assets;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// `kind` is `"summary"`; anything else — including `"subtitle"`, which
    /// now comes from `Core::subtitle_text`, keyed by track rather than
    /// language — answers `None` without touching the database.
    pub async fn set_text(
        self: Arc<Self>,
        set_id: String,
        kind: String,
        lang: String,
    ) -> Option<String> {
        self.blocking(move |core| text(core, &set_id, &kind, &lang))
            .await
    }
}

fn text(core: &Core, set_id: &str, kind: &str, lang: &str) -> Option<String> {
    if kind != "summary" {
        return None;
    }
    let conn = store::open_installed(core, "a set's text")?;
    catalog_assets::text(&conn, set_id, kind, lang).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a set's text could not be read");
        None
    })
}

#[cfg(test)]
#[path = "set_text_tests.rs"]
mod tests;
