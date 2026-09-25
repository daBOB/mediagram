//! The HTTP surface: the four rows of the v1 API.
//!
//! `id` and `n` are taken as raw path segments and checked by
//! [`crate::rules`] before either touches the store, so a value that fails
//! the check never reaches a filesystem call — the 404 a bad shape gets is
//! indistinguishable from one a well-formed but absent id gets, which is
//! the point: nothing here confirms whether a set exists to a caller that
//! never proved it holds the pairing token.

use std::sync::Arc;

use axum::body::{Body, Bytes};
use axum::extract::{DefaultBodyLimit, Path, State};
use axum::http::{HeaderMap, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};

use crate::rules;
use crate::store::{ChunkStore, PutOutcome};
use crate::token;

#[derive(Clone)]
struct AppState {
    store: Arc<ChunkStore>,
    token: Arc<str>,
}

/// The v1 router over `store`, PUTs authenticated against `token`.
pub fn router(store: Arc<ChunkStore>, token: String) -> Router {
    Router::new()
        .route(
            "/v1/sets/{id}/chunks/{n}",
            get(get_chunk).head(head_chunk).put(put_chunk),
        )
        .route("/v1/status", get(status))
        .with_state(AppState {
            store,
            token: token.into(),
        })
        // The body is buffered whole before a handler sees it — a PUT's
        // signature covers it, so streaming would mean signing before the
        // body is fully known. A chunk is at most one `CHUNK`, so this
        // never holds more than that in memory at once.
        .layer(DefaultBodyLimit::max(rules::CHUNK as usize))
}

/// Runs a blocking store call off the async executor: [`ChunkStore`] does
/// its own filesystem I/O synchronously, and that must never share a
/// runtime thread with a request another connection is waiting on.
async fn blocking<T: Send + 'static>(f: impl FnOnce() -> T + Send + 'static) -> T {
    tokio::task::spawn_blocking(f)
        .await
        .expect("a store call does not panic")
}

/// `id` and `n` together, once both have passed [`crate::rules`] — `None`
/// for either is answered with a 404 before any filesystem call.
fn validate(raw_id: &str, raw_n: &str) -> Option<(String, u32)> {
    if !rules::valid_id(raw_id) {
        return None;
    }
    let n = rules::parse_chunk_num(raw_n)?;
    Some((raw_id.to_string(), n))
}

async fn get_chunk(
    State(state): State<AppState>,
    Path((raw_id, raw_n)): Path<(String, String)>,
) -> Response {
    let Some((id, n)) = validate(&raw_id, &raw_n) else {
        return empty(StatusCode::NOT_FOUND);
    };
    let store = state.store.clone();
    match blocking(move || store.get(&id, n)).await {
        Ok(Some(bytes)) => body_response(StatusCode::OK, bytes),
        Ok(None) => empty(StatusCode::NOT_FOUND),
        Err(err) => server_error(&err),
    }
}

async fn head_chunk(
    State(state): State<AppState>,
    Path((raw_id, raw_n)): Path<(String, String)>,
) -> Response {
    let Some((id, n)) = validate(&raw_id, &raw_n) else {
        return empty(StatusCode::NOT_FOUND);
    };
    let store = state.store.clone();
    match blocking(move || store.head(&id, n)).await {
        Ok(Some(len)) => Response::builder()
            .status(StatusCode::OK)
            .header(header::CONTENT_LENGTH, len)
            .body(Body::empty())
            .unwrap_or_else(|_| empty(StatusCode::INTERNAL_SERVER_ERROR)),
        Ok(None) => empty(StatusCode::NOT_FOUND),
        Err(err) => server_error(&err),
    }
}

async fn put_chunk(
    State(state): State<AppState>,
    Path((raw_id, raw_n)): Path<(String, String)>,
    headers: HeaderMap,
    body: Bytes,
) -> Response {
    let Some((id, n)) = validate(&raw_id, &raw_n) else {
        return empty(StatusCode::NOT_FOUND);
    };
    let Some(total) = headers
        .get("x-set-total")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.parse::<u64>().ok())
    else {
        return empty(StatusCode::BAD_REQUEST);
    };
    if rules::check_length(n, body.len() as u64, total).is_err() {
        return empty(StatusCode::BAD_REQUEST);
    }

    // The canonical path is rebuilt from the validated id and the parsed
    // chunk number, not the raw request text, so it agrees byte-for-byte
    // with what a client signed from its own `(id, n)` pair.
    let path = format!("/v1/sets/{id}/chunks/{n}");
    let Some(sig) = auth_signature(&headers) else {
        return empty(StatusCode::UNAUTHORIZED);
    };
    if !token::verify(&state.token, sig, "PUT", &path, total, &body) {
        return empty(StatusCode::UNAUTHORIZED);
    }

    let store = state.store.clone();
    let bytes = body.to_vec();
    match blocking(move || store.put(&id, n, total, &bytes)).await {
        Ok(PutOutcome::Created) => empty(StatusCode::CREATED),
        Ok(PutOutcome::AlreadyHeld) => empty(StatusCode::OK),
        Ok(PutOutcome::TotalMismatch { .. }) => empty(StatusCode::CONFLICT),
        Err(err) => server_error(&err),
    }
}

/// `Authorization: MGC1 <hex>` with the scheme stripped, or `None` for
/// anything else — absent, a different scheme, or not valid header text.
fn auth_signature(headers: &HeaderMap) -> Option<&str> {
    headers
        .get(header::AUTHORIZATION)?
        .to_str()
        .ok()?
        .strip_prefix(token::SCHEME)?
        .strip_prefix(' ')
}

async fn status(State(state): State<AppState>) -> Response {
    let store = state.store.clone();
    let status = blocking(move || store.status()).await;
    Json(serde_json::json!({
        "version": env!("CARGO_PKG_VERSION"),
        "held_bytes": status.held_bytes,
        "budget_bytes": status.budget_bytes,
        "chunks": status.chunks,
    }))
    .into_response()
}

fn body_response(status: StatusCode, bytes: Vec<u8>) -> Response {
    Response::builder()
        .status(status)
        .header(header::CONTENT_LENGTH, bytes.len())
        .body(Body::from(bytes))
        .unwrap_or_else(|_| empty(StatusCode::INTERNAL_SERVER_ERROR))
}

/// A response with the status and nothing else. `Content-Length: 0` is
/// stated rather than left to the transport, matching what a HEAD or a
/// body-bearing response on this same route always carries.
fn empty(status: StatusCode) -> Response {
    Response::builder()
        .status(status)
        .header(header::CONTENT_LENGTH, 0)
        .body(Body::empty())
        .expect("a status-only response is well formed")
}

fn server_error(err: &std::io::Error) -> Response {
    tracing::error!("store: {err}");
    empty(StatusCode::INTERNAL_SERVER_ERROR)
}
