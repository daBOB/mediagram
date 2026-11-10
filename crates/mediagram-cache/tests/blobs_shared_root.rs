//! Chunks and blobs share one root; chunk eviction must leave blobs alone.

use std::sync::Arc;

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use mediagram_cache::blobs::BlobStore;
use mediagram_cache::http::router;
use mediagram_cache::store::ChunkStore;
use mediagram_cache::token;
use tower::ServiceExt;

const TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

async fn send(app: &Router, req: Request<Body>) -> (StatusCode, Vec<u8>) {
    let res = app.clone().oneshot(req).await.unwrap();
    let status = res.status();
    (
        status,
        res.into_body().collect().await.unwrap().to_bytes().to_vec(),
    )
}

fn request(method: &str, path: &str) -> Request<Body> {
    Request::builder()
        .method(method)
        .uri(path)
        .body(Body::empty())
        .unwrap()
}

fn put(path: &str, body: &[u8]) -> Request<Body> {
    let sig = token::sign(TOKEN, "PUT", path, body.len() as u64, body);
    Request::builder()
        .method("PUT")
        .uri(path)
        .header(header::AUTHORIZATION, format!("MGC1 {sig}"))
        .body(Body::from(body.to_vec()))
        .unwrap()
}

/// Chunk eviction removes emptied set directories under the shared root; it
/// must never be able to reach the blob directory.
#[tokio::test]
async fn chunk_eviction_leaves_blobs_in_place() {
    let dir = tempfile::tempdir().unwrap();
    let chunks = ChunkStore::open(dir.path().to_path_buf(), 5).unwrap();
    let blobs = BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap();
    let app = router(Arc::new(chunks), Arc::new(blobs), TOKEN.to_string());
    send(&app, put("/v1/art/w92/a.jpg", b"pic")).await;

    let chunk = |id: &str| {
        let path = format!("/v1/sets/{id}/chunks/0");
        let sig = token::sign(TOKEN, "PUT", &path, 5, b"hello");
        Request::builder()
            .method("PUT")
            .uri(path)
            .header("X-Set-Total", "5")
            .header(header::AUTHORIZATION, format!("MGC1 {sig}"))
            .body(Body::from("hello"))
            .unwrap()
    };
    assert_eq!(send(&app, chunk("blobs")).await.0, StatusCode::NOT_FOUND);
    assert_eq!(send(&app, chunk("one")).await.0, StatusCode::CREATED);
    assert_eq!(send(&app, chunk("two")).await.0, StatusCode::CREATED);

    assert_eq!(
        send(&app, request("GET", "/v1/art/w92/a.jpg")).await.1,
        b"pic"
    );
    let (status, _) = send(&app, put("/v1/art/w92/b.jpg", b"new")).await;
    assert_eq!(status, StatusCode::CREATED);
}
