//! Every non-happy-path row of the v1 API: the 400/401/404/409/413 an id
//! shape, a signature, a length or a body size can fail on. Happy paths are
//! in `api.rs`.

mod common;

use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use common::{TOKEN, app, signed_put};
use mediagram_cache::{rules, token};
use tower::ServiceExt;

#[tokio::test]
async fn get_of_an_absent_chunk_is_404() {
    let (_dir, app) = app(1 << 30);
    let res = app
        .oneshot(
            Request::get("/v1/sets/abc123/chunks/0")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn head_of_an_absent_chunk_is_404() {
    let (_dir, app) = app(1 << 30);
    let res = app
        .oneshot(
            Request::head("/v1/sets/abc123/chunks/0")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn an_id_that_fails_the_shape_check_is_404_before_touching_the_store() {
    let (_dir, app) = app(1 << 30);
    let res = app
        .oneshot(
            Request::get("/v1/sets/../etc/chunks/0")
                .body(Body::empty())
                .unwrap(),
        )
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::NOT_FOUND);
}

#[tokio::test]
async fn a_put_with_a_bad_length_is_400() {
    let (_dir, app) = app(1 << 30);
    // Neither a full CHUNK nor exactly the final remainder of `total`.
    let res = app
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, b"too short"))
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);
}

#[tokio::test]
async fn a_put_with_no_x_set_total_header_is_400() {
    let (_dir, app) = app(1 << 30);
    let sig = token::sign(TOKEN, "PUT", "/v1/sets/abc123/chunks/0", 5, b"hello");
    let req = Request::builder()
        .method("PUT")
        .uri("/v1/sets/abc123/chunks/0")
        .header(header::AUTHORIZATION, format!("MGC1 {sig}"))
        .body(Body::from(b"hello".to_vec()))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::BAD_REQUEST);
}

#[tokio::test]
async fn a_put_with_a_mismatched_total_is_409() {
    let (_dir, app) = app(1 << 30);
    app.clone()
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 5, b"hello"))
        .await
        .unwrap();

    // Same id, a different total: the total-pairing check runs before the
    // chunk-existence check, so this is a 409 even though chunk 0 itself
    // is also already held.
    let res = app
        .oneshot(signed_put("/v1/sets/abc123/chunks/0", 6, b"world!"))
        .await
        .unwrap();
    assert_eq!(res.status(), StatusCode::CONFLICT);
}

#[tokio::test]
async fn a_put_with_no_authorization_header_is_401() {
    let (_dir, app) = app(1 << 30);
    let req = Request::builder()
        .method("PUT")
        .uri("/v1/sets/abc123/chunks/0")
        .header("X-Set-Total", "5")
        .body(Body::from(b"hello".to_vec()))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn a_put_with_a_wrong_signature_is_401() {
    let (_dir, app) = app(1 << 30);
    let req = Request::builder()
        .method("PUT")
        .uri("/v1/sets/abc123/chunks/0")
        .header("X-Set-Total", "5")
        .header(header::AUTHORIZATION, "MGC1 0000")
        .body(Body::from(b"hello".to_vec()))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::UNAUTHORIZED);
}

#[tokio::test]
async fn a_put_one_byte_over_the_limit_is_413() {
    let (_dir, app) = app(1 << 30);
    let body = vec![7u8; rules::CHUNK as usize + 1];
    let sig = token::sign(
        TOKEN,
        "PUT",
        "/v1/sets/abc123/chunks/0",
        body.len() as u64,
        &body,
    );
    let req = Request::builder()
        .method("PUT")
        .uri("/v1/sets/abc123/chunks/0")
        .header("X-Set-Total", body.len().to_string())
        .header(header::AUTHORIZATION, format!("MGC1 {sig}"))
        .body(Body::from(body))
        .unwrap();
    let res = app.oneshot(req).await.unwrap();
    assert_eq!(res.status(), StatusCode::PAYLOAD_TOO_LARGE);
}
