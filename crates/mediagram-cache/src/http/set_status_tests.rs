use std::sync::Arc;

use axum::http::header;
use http_body_util::BodyExt;
use tempfile::tempdir;

use super::*;
use crate::store::ChunkStore;

fn state(root: &std::path::Path) -> AppState {
    let store = ChunkStore::open(root.to_path_buf(), 1 << 30).expect("open store");
    AppState {
        store: Arc::new(store),
        token: Arc::from("unused: this route is not authenticated"),
    }
}

async fn ask(state: AppState, id: &str) -> Response {
    set_status(State(state), Path(id.to_string())).await
}

async fn json(response: Response) -> serde_json::Value {
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    serde_json::from_slice(&bytes).expect("a JSON body")
}

#[test]
fn a_well_shaped_id_passes_through_unchanged() {
    assert_eq!(validate_id("abc123").as_deref(), Some("abc123"));
    assert_eq!(validate_id(&"Z".repeat(64)), Some("Z".repeat(64)));
}

/// Anything that could reach past the store root, or that no client would
/// ever send as an id, is refused before a path is ever built from it.
#[test]
fn a_malformed_id_is_refused() {
    for bad in ["", "bad_id", "..", "../etc", "a/b", &"a".repeat(65)] {
        assert_eq!(validate_id(bad), None, "{bad:?}");
    }
}

/// Android reads a 404 from this route as "this server predates it", so a
/// malformed id is the only thing a current server answers with one — and
/// it answers before the store is ever asked.
#[tokio::test]
async fn a_malformed_id_is_an_empty_404() {
    let dir = tempdir().unwrap();
    let response = ask(state(dir.path()), "../etc").await;

    assert_eq!(response.status(), StatusCode::NOT_FOUND);
    assert_eq!(response.headers()[header::CONTENT_LENGTH], "0");
}

#[tokio::test]
async fn a_well_shaped_but_unknown_id_is_200_with_zeros_and_a_null_total() {
    let dir = tempdir().unwrap();
    let response = ask(state(dir.path()), "abc123").await;

    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(
        json(response).await,
        serde_json::json!({ "total": null, "chunks_held": 0, "bytes_held": 0 })
    );
}

#[tokio::test]
async fn a_held_set_reports_its_total_and_what_is_held_of_it() {
    let dir = tempdir().unwrap();
    let state = state(dir.path());
    state.store.put("abc123", 0, 5, b"hello").unwrap();

    let response = ask(state, "abc123").await;

    assert_eq!(response.status(), StatusCode::OK);
    assert_eq!(
        json(response).await,
        serde_json::json!({ "total": 5, "chunks_held": 1, "bytes_held": 5 })
    );
}

/// A store that cannot answer is a server fault, never a reply that the set
/// is absent: a client that took zeros here would start refetching a set
/// the server may well hold.
#[tokio::test]
async fn a_store_that_cannot_read_the_set_is_an_empty_500() {
    let dir = tempdir().unwrap();
    std::fs::create_dir_all(dir.path().join("abc123").join("total")).unwrap();

    let response = ask(state(dir.path()), "abc123").await;

    assert_eq!(response.status(), StatusCode::INTERNAL_SERVER_ERROR);
    assert_eq!(response.headers()[header::CONTENT_LENGTH], "0");
}
