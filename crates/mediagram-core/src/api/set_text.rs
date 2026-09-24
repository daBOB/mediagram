//! `Core::set_text`: a summary or subtitle track already sitting in the
//! index, the way the uploader wrote it from the file beside each video.
//! Never a Telegram round trip.

use std::sync::Arc;

use crate::api::{Core, CoreError, store};
use crate::catalog_assets;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// `kind` is `"summary"` or `"subtitle"`; anything else answers `None`
    /// without touching the database — the same refusal `catalog_assets::text`
    /// applies to a kind it does not know.
    pub async fn set_text(self: Arc<Self>, set_id: String, kind: String, lang: String) -> Option<String> {
        self.blocking(move |core| text(core, &set_id, &kind, &lang)).await
    }
}

fn text(core: &Core, set_id: &str, kind: &str, lang: &str) -> Option<String> {
    let conn = match store::open(core) {
        Ok(conn) => conn,
        // No catalog installed yet: nothing to read, and nothing wrong.
        Err(CoreError::NotFound(_)) => return None,
        Err(err) => {
            tracing::warn!(error = %err, "the index could not be opened for a set's text");
            return None;
        }
    };
    catalog_assets::text(&conn, set_id, kind, lang).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "a set's text could not be read");
        None
    })
}
