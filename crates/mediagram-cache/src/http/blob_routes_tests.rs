use std::sync::Arc;

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use tempfile::TempDir;
use tower::ServiceExt;

use crate::blobs::BlobStore;
use crate::http::router;
use crate::store::ChunkStore;
use crate::token;

const TOKEN: &str = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";
const KEY: &str = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

fn app(blob_budget: u64) -> (TempDir, Router) {
    let dir = tempfile::tempdir().unwrap();
    let chunks = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).unwrap();
    let blobs = BlobStore::open(dir.path().to_path_buf(), blob_budget).unwrap();
    (
        dir,
        router(Arc::new(chunks), Arc::new(blobs), TOKEN.to_string()),
    )
}

fn put(path: &str, body: &[u8], signed: bool) -> Request<Body> {
    let mut req = Request::builder().method("PUT").uri(path);
    if signed {
        let sig = token::sign(TOKEN, "PUT", path, body.len() as u64, body);
        req = req.header(header::AUTHORIZATION, format!("MGC1 {sig}"));
    }
    req.body(Body::from(body.to_vec())).unwrap()
}

fn request(method: &str, path: &str) -> Request<Body> {
    Request::builder()
        .method(method)
        .uri(path)
        .body(Body::empty())
        .unwrap()
}

async fn send(app: &Router, req: Request<Body>) -> (StatusCode, Vec<u8>, axum::http::HeaderMap) {
    let res = app.clone().oneshot(req).await.unwrap();
    let (status, headers) = (res.status(), res.headers().clone());
    (
        status,
        res.into_body().collect().await.unwrap().to_bytes().to_vec(),
        headers,
    )
}

#[tokio::test]
async fn unsigned_put_is_refused() {
    let (_dir, app) = app(1 << 20);
    let (status, ..) = send(&app, put(&format!("/v1/tmdb/{KEY}"), b"{}", false)).await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);
    let (status, ..) = send(&app, request("GET", &format!("/v1/tmdb/{KEY}"))).await;
    assert_eq!(status, StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn signed_tmdb_put_then_get_returns_it() {
    let (_dir, app) = app(1 << 20);
    let path = format!("/v1/tmdb/{KEY}");
    let (status, ..) = send(&app, put(&path, br#"{"a":1}"#, true)).await;
    assert_eq!(status, StatusCode::CREATED);
    let (status, body, headers) = send(&app, request("GET", &path)).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body, br#"{"a":1}"#);
    assert_eq!(headers[header::CONTENT_TYPE], "application/json");
}

#[tokio::test]
async fn a_second_tmdb_put_replaces_the_first() {
    let (_dir, app) = app(1 << 20);
    let path = format!("/v1/tmdb/{KEY}");
    send(&app, put(&path, b"[1]", true)).await;
    let (status, ..) = send(&app, put(&path, b"[2,2]", true)).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(send(&app, request("GET", &path)).await.1, b"[2,2]");
}

#[tokio::test]
async fn a_second_art_put_keeps_the_first() {
    let (_dir, app) = app(1 << 20);
    let path = "/v1/art/w342/abc_1.jpg";
    let (status, ..) = send(&app, put(path, b"first", true)).await;
    assert_eq!(status, StatusCode::CREATED);
    let (status, ..) = send(&app, put(path, b"second", true)).await;
    assert_eq!(status, StatusCode::OK);
    let (_, body, headers) = send(&app, request("GET", path)).await;
    assert_eq!(body, b"first");
    assert_eq!(headers[header::CONTENT_TYPE], "image/jpeg");
}

#[tokio::test]
async fn bad_names_are_not_found() {
    let (_dir, app) = app(1 << 20);
    for path in [
        "/v1/tmdb/XYZ",
        "/v1/art/w342/../x.jpg",
        "/v1/art/big/a.jpg",
        "/v1/art/w342/a.gif",
    ] {
        for method in ["GET", "HEAD"] {
            assert_eq!(
                send(&app, request(method, path)).await.0,
                StatusCode::NOT_FOUND,
                "{method} {path}"
            );
        }
        assert_eq!(
            send(&app, put(path, b"x", true)).await.0,
            StatusCode::NOT_FOUND,
            "PUT {path}"
        );
    }
}

#[tokio::test]
async fn head_says_whether_it_is_held() {
    let (_dir, app) = app(1 << 20);
    let path = "/v1/art/original/p.png";
    assert_eq!(
        send(&app, request("HEAD", path)).await.0,
        StatusCode::NOT_FOUND
    );
    send(&app, put(path, b"12345", true)).await;
    let (status, _, headers) = send(&app, request("HEAD", path)).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(headers[header::CONTENT_LENGTH], "5");
}

#[tokio::test]
async fn a_blob_larger_than_the_budget_is_refused() {
    let (_dir, app) = app(10);
    send(&app, put("/v1/art/w92/keep.jpg", b"0123456789", true)).await;
    let (status, ..) = send(&app, put("/v1/art/w92/big.jpg", b"01234567890", true)).await;
    assert_eq!(status, StatusCode::PAYLOAD_TOO_LARGE);
    assert_eq!(
        send(&app, request("HEAD", "/v1/art/w92/keep.jpg")).await.0,
        StatusCode::OK
    );
}

#[tokio::test]
async fn bodies_over_the_kind_limits_are_refused() {
    let (_dir, app) = app(1 << 30);
    let json = vec![b' '; 2 * 1024 * 1024 + 1];
    assert_eq!(
        send(&app, put(&format!("/v1/tmdb/{KEY}"), &json, true))
            .await
            .0,
        StatusCode::PAYLOAD_TOO_LARGE
    );
    let image = vec![0u8; 16 * 1024 * 1024 + 1];
    assert_eq!(
        send(&app, put("/v1/art/w92/big.jpg", &image, true)).await.0,
        StatusCode::PAYLOAD_TOO_LARGE
    );
    let image = vec![0u8; 3 * 1024 * 1024];
    assert_eq!(
        send(&app, put("/v1/art/w92/ok.jpg", &image, true)).await.0,
        StatusCode::CREATED
    );
}

#[tokio::test]
async fn status_counts_blob_bytes() {
    let (_dir, app) = app(777);
    send(&app, put("/v1/art/w92/a.jpg", b"abcd", true)).await;
    let (_, body, _) = send(&app, request("GET", "/v1/status")).await;
    let json: serde_json::Value = serde_json::from_slice(&body).unwrap();
    assert_eq!(json["blob_bytes"], 4);
    assert_eq!(json["blob_budget_bytes"], 777);
}

/// Sends `head` over a real socket and withholds the body it announces, so
/// an answer that arrives at all was given without reading that body.
async fn answer_before_the_body(app: Router, head: String) -> Option<String> {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move { axum::serve(listener, app).await });
    let mut socket = tokio::net::TcpStream::connect(addr).await.unwrap();
    socket.write_all(head.as_bytes()).await.unwrap();
    let mut answer = [0u8; 64];
    let read = tokio::time::timeout(std::time::Duration::from_secs(2), socket.read(&mut answer));
    let n = read.await.ok()?.ok()?;
    Some(String::from_utf8_lossy(&answer[..n]).into_owned())
}

#[tokio::test]
async fn an_unsigned_put_is_refused_before_its_body_is_read() {
    let (_dir, app) = app(1 << 20);
    let head = format!("PUT /v1/tmdb/{KEY} HTTP/1.1\r\nhost: x\r\ncontent-length: 100\r\n\r\n");
    let answer = answer_before_the_body(app, head).await;
    assert!(
        answer
            .as_deref()
            .is_some_and(|a| a.starts_with("HTTP/1.1 401")),
        "{answer:?}"
    );
}

#[tokio::test]
async fn an_oversize_put_is_refused_before_its_body_is_read() {
    let (_dir, app) = app(1 << 30);
    for (path, len) in [
        (format!("/v1/tmdb/{KEY}"), 2 * 1024 * 1024 + 1),
        ("/v1/art/w92/big.jpg".to_string(), 16 * 1024 * 1024 + 1),
    ] {
        let head = format!(
            "PUT {path} HTTP/1.1\r\nhost: x\r\nauthorization: MGC1 00\r\ncontent-length: {len}\r\n\r\n"
        );
        let answer = answer_before_the_body(app.clone(), head).await;
        assert!(
            answer
                .as_deref()
                .is_some_and(|a| a.starts_with("HTTP/1.1 413")),
            "{path}: {answer:?}"
        );
    }
}
