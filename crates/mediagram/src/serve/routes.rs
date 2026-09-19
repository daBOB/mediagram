//! The HTTP surface: what is playable, and the bytes of a set.
//!
//! The router is generic over where bytes come from, so the whole contract —
//! statuses, `Content-Length`, `Content-Range`, part-boundary crossings — is
//! testable without Telegram. [`super::telegram::TelegramSource`] is the real
//! implementation; a test supplies a known file instead.

use std::sync::{Arc, Mutex};

use axum::body::Body;
use axum::extract::{Path, State};
use axum::http::{HeaderMap, Method, StatusCode, header};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use axum::{Json, Router};
use rusqlite::Connection;

use super::catalog;
use super::range::{PartSpan, plan_reads, total_size};
use super::response::plan_response;

pub use mediagram_core::stream::{ByteSource, ByteStream};

#[derive(Clone)]
pub struct ServeState {
    index: Arc<Mutex<Connection>>,
    source: Arc<dyn ByteSource>,
}

pub fn router(index: Connection, source: Arc<dyn ByteSource>) -> Router {
    Router::new()
        .route("/sets", get(list_sets))
        .route("/sets/{set_id}/stream", get(stream_set))
        .with_state(ServeState {
            index: Arc::new(Mutex::new(index)),
            source,
        })
}

async fn list_sets(State(state): State<ServeState>) -> Response {
    let conn = state.index.lock().expect("index lock");
    match catalog::list_playable(&conn) {
        Ok(sets) => Json(sets).into_response(),
        Err(err) => server_error(&err),
    }
}

async fn stream_set(
    State(state): State<ServeState>,
    Path(set_id): Path<String>,
    method: Method,
    headers: HeaderMap,
) -> Response {
    let (set, locations) = {
        let conn = state.index.lock().expect("index lock");
        let set = match catalog::playable_set(&conn, &set_id) {
            Ok(Some(set)) => set,
            // Not there, incomplete, or inconsistent: all the same to a
            // player, and none of them worth telling a caller apart.
            Ok(None) => return empty(StatusCode::NOT_FOUND),
            Err(err) => return server_error(&err),
        };
        match catalog::part_locations(&conn, &set_id) {
            Ok(locations) => (set, locations),
            Err(err) => return server_error(&err),
        }
    };

    let spans: Vec<PartSpan> = locations.iter().map(|l| l.span).collect();
    let total = total_size(&spans);

    // A `Range` header that is not even text is malformed, and answering it
    // with the whole file would be a surprising 200 to a seeking player.
    let raw = headers.get(header::RANGE);
    let range_header = match raw.map(|value| value.to_str()) {
        None => None,
        Some(Ok(text)) => Some(text),
        Some(Err(_)) => return empty(StatusCode::BAD_REQUEST),
    };
    let plan = plan_response(range_header, total);
    let status = StatusCode::from_u16(plan.status).unwrap_or(StatusCode::INTERNAL_SERVER_ERROR);

    let mut response = Response::builder()
        .status(status)
        .header(header::ACCEPT_RANGES, "bytes")
        .header(header::CONTENT_LENGTH, plan.content_length);
    if let Some(content_range) = plan.content_range {
        response = response.header(header::CONTENT_RANGE, content_range);
    }

    // HEAD asks for the headers only: resolving documents and opening a
    // download for a body nobody reads would cost a Telegram round trip per
    // probe, and players probe often.
    let body = match plan.range {
        Some(range) if method != Method::HEAD => {
            Body::from_stream(state.source.stream(locations, plan_reads(&spans, &range)))
        }
        _ => Body::empty(),
    };
    response
        .header(header::CONTENT_TYPE, content_type(&set.container))
        .body(body)
        .unwrap_or_else(|_| empty(StatusCode::INTERNAL_SERVER_ERROR))
}

/// A response with the status and nothing else. `Content-Length: 0` is stated
/// rather than left to the transport, because every response carrying one is
/// the property being relied on downstream.
fn empty(status: StatusCode) -> Response {
    Response::builder()
        .status(status)
        .header(header::CONTENT_LENGTH, 0)
        .body(Body::empty())
        .expect("a status-only response is well formed")
}

fn server_error(err: &anyhow::Error) -> Response {
    tracing::error!("serve: {err:#}");
    empty(StatusCode::INTERNAL_SERVER_ERROR)
}

/// The virtual file is the original file's bytes, so its type is the
/// original container's. A browser that gets this wrong will not even attempt
/// direct play, and will fall back to a transcode it did not need.
fn content_type(container: &str) -> &'static str {
    match container {
        "mkv" => "video/x-matroska",
        "mp4" | "m4v" => "video/mp4",
        "webm" => "video/webm",
        "avi" => "video/x-msvideo",
        "ts" => "video/mp2t",
        _ => "application/octet-stream",
    }
}
