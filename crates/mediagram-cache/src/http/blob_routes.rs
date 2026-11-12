//! `GET|HEAD|PUT` for TMDB answers (`/v1/tmdb/{key}`) and images
//! (`/v1/art/{size}/{name}`), plus the game routes beside them. Reads are open, like chunk reads; a PUT is
//! signed over the path rebuilt from the validated parts and the body's own
//! length. A malformed name is a 404 before the store is asked anything.

use axum::Router;
use axum::body::{Body, to_bytes};
use axum::extract::{Path, State};
use axum::http::{HeaderMap, StatusCode, header};
use axum::response::Response;
use axum::routing::get;

use super::{AppState, auth_signature, blocking, body_response, empty, server_error};
use crate::blobs::{BlobKind, BlobPut, valid_art_name, valid_art_size, valid_tmdb_key};
use crate::token;

pub(super) const MAX_JSON: usize = 2 * 1024 * 1024;
pub(super) const MAX_IMAGE: usize = 16 * 1024 * 1024;

pub(super) fn routes() -> Router<AppState> {
    Router::new()
        .route(
            "/v1/tmdb/{key}",
            get(get_tmdb).head(head_tmdb).put(put_tmdb),
        )
        .route(
            "/v1/art/{size}/{name}",
            get(get_art).head(head_art).put(put_art),
        )
        .merge(super::game_blob_routes::routes())
}

/// `(kind, store name, canonical path)` once the shape checks out.
pub(super) type Target = (BlobKind, String, String);

fn tmdb_target(key: &str) -> Option<Target> {
    valid_tmdb_key(key).then(|| (BlobKind::Tmdb, key.to_string(), format!("/v1/tmdb/{key}")))
}

fn art_target(size: &str, name: &str) -> Option<Target> {
    (valid_art_size(size) && valid_art_name(name)).then(|| {
        (
            BlobKind::Art,
            format!("{size}/{name}"),
            format!("/v1/art/{size}/{name}"),
        )
    })
}

fn content_type(kind: BlobKind, name: &str) -> &'static str {
    match (kind, name.rsplit('.').next()) {
        (BlobKind::Tmdb, _) | (_, Some("json")) => "application/json",
        (_, Some("png")) => "image/png",
        (_, Some("svg")) => "image/svg+xml",
        _ => "image/jpeg",
    }
}

async fn get_tmdb(State(state): State<AppState>, Path(key): Path<String>) -> Response {
    read(state, tmdb_target(&key)).await
}

async fn get_art(
    State(state): State<AppState>,
    Path((size, name)): Path<(String, String)>,
) -> Response {
    read(state, art_target(&size, &name)).await
}

async fn head_tmdb(State(state): State<AppState>, Path(key): Path<String>) -> Response {
    probe(state, tmdb_target(&key)).await
}

async fn head_art(
    State(state): State<AppState>,
    Path((size, name)): Path<(String, String)>,
) -> Response {
    probe(state, art_target(&size, &name)).await
}

async fn put_tmdb(
    State(state): State<AppState>,
    Path(key): Path<String>,
    headers: HeaderMap,
    body: Body,
) -> Response {
    write(state, tmdb_target(&key), MAX_JSON, headers, body).await
}

async fn put_art(
    State(state): State<AppState>,
    Path((size, name)): Path<(String, String)>,
    headers: HeaderMap,
    body: Body,
) -> Response {
    write(state, art_target(&size, &name), MAX_IMAGE, headers, body).await
}

pub(super) async fn read(state: AppState, target: Option<Target>) -> Response {
    let Some((kind, name, _)) = target else {
        return empty(StatusCode::NOT_FOUND);
    };
    let blobs = state.blobs.clone();
    let stored = name.clone();
    match blocking(move || blobs.get(kind, &stored)).await {
        Ok(Some(bytes)) => {
            let mut response = body_response(StatusCode::OK, bytes);
            response.headers_mut().insert(
                header::CONTENT_TYPE,
                content_type(kind, &name)
                    .parse()
                    .expect("a static media type"),
            );
            response
        }
        Ok(None) => empty(StatusCode::NOT_FOUND),
        Err(err) => server_error(&err),
    }
}

pub(super) async fn probe(state: AppState, target: Option<Target>) -> Response {
    let Some((kind, name, _)) = target else {
        return empty(StatusCode::NOT_FOUND);
    };
    let blobs = state.blobs.clone();
    match blocking(move || blobs.head(kind, &name)).await {
        Ok(Some(len)) => Response::builder()
            .status(StatusCode::OK)
            .header(header::CONTENT_LENGTH, len)
            .body(Body::empty())
            .unwrap_or_else(|_| empty(StatusCode::INTERNAL_SERVER_ERROR)),
        Ok(None) => empty(StatusCode::NOT_FOUND),
        Err(err) => server_error(&err),
    }
}

/// Checked cheapest first — shape, size, then the signature — so a request
/// that can never be accepted is not charged an HMAC over its body. The
/// body is read only once its declared length fits and it names a
/// signature, so an unsigned or oversize request is never buffered.
pub(super) async fn write(
    state: AppState,
    target: Option<Target>,
    max: usize,
    headers: HeaderMap,
    body: Body,
) -> Response {
    let Some((kind, name, path)) = target else {
        return empty(StatusCode::NOT_FOUND);
    };
    let cap = max.min(usize::try_from(state.blobs.status().1).unwrap_or(usize::MAX));
    let declared = headers
        .get(header::CONTENT_LENGTH)
        .and_then(|v| v.to_str().ok()?.parse::<u64>().ok());
    if declared.is_some_and(|len| len > cap as u64) {
        return empty(StatusCode::PAYLOAD_TOO_LARGE);
    }
    let Some(sig) = auth_signature(&headers) else {
        return empty(StatusCode::UNAUTHORIZED);
    };
    // Bounded again while reading: a body may carry no length, or a false one.
    let Ok(body) = to_bytes(body, cap).await else {
        return empty(StatusCode::PAYLOAD_TOO_LARGE);
    };
    if !token::verify(&state.token, sig, "PUT", &path, body.len() as u64, &body) {
        return empty(StatusCode::UNAUTHORIZED);
    }
    let blobs = state.blobs.clone();
    match blocking(move || blobs.put(kind, &name, &body)).await {
        Ok(BlobPut::Created) => empty(StatusCode::CREATED),
        Ok(BlobPut::Replaced | BlobPut::AlreadyHeld) => empty(StatusCode::OK),
        Err(err) => server_error(&err),
    }
}

#[cfg(test)]
#[path = "blob_routes_tests.rs"]
mod tests;
