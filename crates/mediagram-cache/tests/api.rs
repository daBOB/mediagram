//! The v1 API's happy paths, one test per row, driven straight at the
//! [`mediagram_cache::http::router`] with `tower::ServiceExt::oneshot` — no
//! socket, so a wrong status is a wrong status and not a flaky port bind.
//! Rejections (400/401/404/409/413) are in `api_rejections.rs`.

mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::{app, body_bytes, signed_put};
use mediagram_cache::rules;
use tower::ServiceExt;

#[tokio::test]
async fn put_then_get_returns_the_bytes_with_201_then_200() {
    let (_dir, app) = app(1 << 30);
    let body = b"hello".to_vec();

    let put_res = app
        .clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, &body))
        .await
        .unwrap();
    assert_eq!(put_res.status(), StatusCode::CREATED);

    let get_res = app
        .oneshot(
            Request::get("/v1/sets/abc123/chunks/0")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(get_res.status(), StatusCode::OK);
    assert_eq!(get_res.headers()[header::CONTENT_LENGTH], "5");
    assert_eq!(body_bytes(get_res).await, body);
}

#[tokio::test]
async fn head_reports_length_with_no_body() {
    let (_dir, app) = app(1 << 30);
    let body = b"hello".to_vec();
    app.clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, &body))
        .await
        .unwrap();

    let res = app
        .oneshot(
            Request::head("/v1/sets/abc123/chunks/0")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    assert_eq!(res.headers()[header::CONTENT_LENGTH], "5");
    assert!(body_bytes(res).await.is_empty());
}

#[tokio::test]
async fn a_repeated_put_of_the_same_chunk_is_200_already_held() {
    let (_dir, app) = app(1 << 30);
    let body = b"hello".to_vec();
    app.clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, &body))
        .await
        .unwrap();

    let res = app
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, &body))
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::OK);
}

#[tokio::test]
async fn a_put_of_exactly_one_chunk_succeeds() {
    let (_dir, app) = app(1 << 30);
    let body = vec![7u8; rules::CHUNK as usize];
    let total = rules::CHUNK * 2;
    let res = app
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", total, &body))
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::CREATED);
}

#[tokio::test]
async fn status_reports_version_held_budget_and_chunks() {
    let (_dir, app) = app(1 << 30);
    app.clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, b"hello"))
        .await
        .unwrap();

    let res = app
        .oneshot(Request::get("/v1/status").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let bytes = body_bytes(res).await;
    let json: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
    assert_eq!(json["held_bytes"], 5);
    assert_eq!(json["budget_bytes"], 1 << 30);
    assert_eq!(json["chunks"], 1);
    assert!(json["version"].is_string());
}

#[tokio::test]
async fn set_status_of_an_unknown_id_is_200_with_zeros_and_a_null_total() {
    let (_dir, app) = app(1 << 30);
    let res = app
        .oneshot(Request::get("/v1/sets/abc123").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let json: serde_json::Value = serde_json::from_slice(&body_bytes(res).await).unwrap();
    assert_eq!(json["total"], serde_json::Value::Null);
    assert_eq!(json["chunks_held"], 0);
    assert_eq!(json["bytes_held"], 0);
}

#[tokio::test]
async fn set_status_reports_the_total_and_bytes_held_for_a_partly_written_set() {
    let (_dir, app) = app(1 << 30);
    let total = rules::CHUNK * 3;
    let body = vec![7u8; rules::CHUNK as usize];
    app.clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", total, &body))
        .await
        .unwrap();

    let res = app
        .oneshot(Request::get("/v1/sets/abc123").body(Body::empty()).unwrap())
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::OK);
    let json: serde_json::Value = serde_json::from_slice(&body_bytes(res).await).unwrap();
    assert_eq!(json["total"], total);
    assert_eq!(json["chunks_held"], 1);
    assert_eq!(json["bytes_held"], rules::CHUNK);
}
