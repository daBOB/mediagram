//! The file the games' log lines go to: appended by the room task after
//! every event and tick, and served to the LAN, since a release build's data
//! folder cannot be read from outside the box.

use std::fs::OpenOptions;
use std::io::Write;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

use axum::extract::State;
use axum::http::{StatusCode, header};
use axum::response::{IntoResponse, Response};

use crate::server::AppState;

/// Appends `lines` to the file at `path`, each with the wall-clock time; a
/// failed write is only warned about, so the game never stops for the disk.
pub(crate) fn append_log(path: &Path, lines: Vec<serde_json::Value>) {
    if lines.is_empty() {
        return;
    }
    let unix_ms = SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.as_millis() as u64);
    let text: String = lines
        .into_iter()
        .map(|mut line| {
            if let Some(fields) = line.as_object_mut() {
                fields.insert("unix_ms".into(), unix_ms.into());
            }
            line.to_string() + "\n"
        })
        .collect();
    let written = OpenOptions::new().create(true).append(true).open(path).and_then(|mut f| f.write_all(text.as_bytes()));
    if let Err(e) = written {
        tracing::warn!("the game log could not be written to {}: {e}", path.display());
    }
}

/// The games' log, fetched over the LAN because a release build's data folder is out of reach.
pub(crate) async fn serve_log(State(state): State<AppState>) -> Response {
    let Some(path) = &state.log else {
        return StatusCode::NOT_FOUND.into_response();
    };
    match tokio::fs::read(path).await {
        Ok(bytes) => ([(header::CONTENT_TYPE, "text/plain; charset=utf-8"), (header::CACHE_CONTROL, "no-store")], bytes).into_response(),
        Err(_) => StatusCode::NOT_FOUND.into_response(),
    }
}

#[cfg(test)]
#[path = "game_log_tests.rs"]
mod tests;
