//! `GET /v1/sets/{id}` is the one row that, past the shape check, tells a
//! well-formed id apart from an absent one — unauthenticated, it confirms a set exists and how
//! much of it is held. That is the accepted exposure, not a hole to close:
//! `HEAD` already gives an unauthenticated caller the same proof, one chunk
//! at a time. Answering unknown ids with a uniform 404 here would break the
//! one thing Android's `null` for "an older server" actually leans on —
//! that a 404 from this route means exactly that, nothing else.

use axum::Json;
use axum::extract::{Path, State};
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use super::{AppState, blocking, empty, server_error};
use crate::rules;

/// `id` alone, for the one route with no chunk number — the same 404
/// before any filesystem call as [`super::validate`].
fn validate_id(raw_id: &str) -> Option<String> {
    rules::valid_id(raw_id).then(|| raw_id.to_string())
}

/// `GET /v1/sets/{id}`: a question about one set, not a read of it — polled
/// instead of `HEAD`ing every chunk, so it must never itself change what
/// eviction picks next. An unrecognized but well-shaped id is `200` with
/// zeros and a `null` total; only a malformed id is a 404.
pub(super) async fn set_status(
    State(state): State<AppState>,
    Path(raw_id): Path<String>,
) -> Response {
    let Some(id) = validate_id(&raw_id) else {
        return empty(StatusCode::NOT_FOUND);
    };
    let store = state.store.clone();
    match blocking(move || store.set_status(&id)).await {
        Ok(status) => Json(serde_json::json!({
            "total": status.total,
            "chunks_held": status.chunks_held,
            "bytes_held": status.bytes_held,
        }))
        .into_response(),
        Err(err) => server_error(&err),
    }
}
