//! Shared harness for the v1 API tests: a router over a fresh temp-dir
//! store, and the signing helper every PUT test needs.

use std::sync::Arc;

use axum::body::Body;
use axum::http::{Request, header};
use http_body_util::BodyExt;
use mediagram_cache::http::router;
use mediagram_cache::store::ChunkStore;
use mediagram_cache::token;

pub const TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

pub fn app(budget: u64) -> (tempfile::TempDir, axum::Router) {
    let dir = tempfile::tempdir().expect("temp dir");
    let store = ChunkStore::open(dir.path().to_path_buf(), budget).expect("open store");
    let router = router(Arc::new(store), TOKEN.to_string());
    (dir, router)
}

pub fn signed_put(path: &str, total: u64, body: &[u8]) -> Request<Body> {
    let sig = token::sign(TOKEN, "PUT", path, total, body);
    Request::builder()
        .method("PUT")
        .uri(path)
        .header("X-Set-Total", total.to_string())
        .header(header::AUTHORIZATION, format!("MGC1 {sig}"))
        .body(Body::from(body.to_vec()))
        .unwrap()
}

// This module is compiled once per test binary that includes it; not every
// binary calls every helper.
#[allow(dead_code)]
pub async fn body_bytes(response: axum::response::Response) -> Vec<u8> {
    response
        .into_body()
        .collect()
        .await
        .unwrap()
        .to_bytes()
        .to_vec()
}
