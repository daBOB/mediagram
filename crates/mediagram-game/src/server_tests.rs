use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

use crate::general::general_pack;
use crate::{GameFactory, Host, HostConfig, Quiz, QuizConfig};

fn factory() -> GameFactory {
    Box::new(|ctx, _| Box::new(Quiz::new(QuizConfig::default(), general_pack(), ctx.now)))
}

async fn start(preferred_port: u16) -> Host {
    let cfg = HostConfig { preferred_port, ..HostConfig::default() };
    Host::start(cfg, factory()).await.unwrap()
}

#[tokio::test]
async fn stop_frees_the_port() {
    let host = start(0).await;
    let addr = ("127.0.0.1", host.port());
    assert!(TcpStream::connect(addr).await.is_ok());
    host.stop().await;
    assert!(TcpStream::connect(addr).await.is_err());
}

#[tokio::test]
async fn falls_back_when_preferred_port_is_taken() {
    let taken = TcpListener::bind("0.0.0.0:0").await.unwrap();
    let p = taken.local_addr().unwrap().port();
    let host = start(p).await;
    assert_ne!(host.port(), p);
    host.stop().await;
}

/// A raw GET on the host's port: the response head and body.
async fn get(port: u16, path: &str) -> (String, String) {
    let mut s = TcpStream::connect(("127.0.0.1", port)).await.unwrap();
    s.write_all(format!("GET {path} HTTP/1.1\r\nhost: x\r\nconnection: close\r\n\r\n").as_bytes()).await.unwrap();
    let mut raw = String::new();
    s.read_to_string(&mut raw).await.unwrap();
    let (head, body) = raw.split_once("\r\n\r\n").unwrap();
    (head.into(), body.into())
}

#[tokio::test]
async fn qr_svg_is_served() {
    let host = start(0).await;
    let (head, body) = get(host.port(), "/qr.svg").await;
    assert!(head.starts_with("HTTP/1.1 200"), "{head}");
    assert!(head.to_lowercase().contains("content-type: image/svg+xml"), "{head}");
    assert!(body.contains("<svg"));
    host.stop().await;
}

#[tokio::test]
async fn the_games_log_is_served_as_text_once_it_exists() {
    let dir = tempfile::tempdir().unwrap();
    let log = dir.path().join("grand-allee-log.jsonl");
    let cfg = HostConfig { preferred_port: 0, log: Some(log.clone()), ..HostConfig::default() };
    let host = Host::start(cfg, factory()).await.unwrap();
    let (head, _) = get(host.port(), "/grand-allee-log").await;
    assert!(head.starts_with("HTTP/1.1 404"), "no file yet: {head}");
    std::fs::write(&log, "{\"ev\":\"start\"}\n").unwrap();
    let (head, body) = get(host.port(), "/grand-allee-log").await;
    assert!(head.starts_with("HTTP/1.1 200"), "{head}");
    assert!(head.to_lowercase().contains("content-type: text/plain; charset=utf-8"), "{head}");
    assert_eq!(body, "{\"ev\":\"start\"}\n");
    host.stop().await;
}

#[tokio::test]
async fn images_are_only_served_to_the_box() {
    use std::net::SocketAddr;
    use std::sync::Arc;
    use std::sync::atomic::AtomicU64;

    use axum::extract::{ConnectInfo, Path, State};
    use axum::http::StatusCode;
    use axum::response::IntoResponse;

    use crate::server::{AppState, image};
    use crate::titles::TitlePool;
    use crate::titles::tests::title;

    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join("p.png");
    std::fs::write(&file, b"png-bytes").unwrap();
    let mut t = title("a", true);
    t.poster = Some(file);
    let (events, _rx) = tokio::sync::mpsc::unbounded_channel();
    let state = AppState {
        events,
        next_conn: Arc::new(AtomicU64::new(1)),
        join_url: "http://x/".into(),
        pool: Arc::new(TitlePool::new(vec![t], false)),
        textless: None,
        images: None,
        articles: std::sync::Arc::default(),
        log: None,
    };
    let get = |peer: &str, i: usize, which: &str| {
        let peer: SocketAddr = peer.parse().unwrap();
        image(State(state.clone()), ConnectInfo(peer), Path((i, which.into())))
    };

    assert_eq!(get("192.168.0.5:5000", 0, "poster").await.status(), StatusCode::FORBIDDEN);
    let ok = get("127.0.0.1:5000", 0, "poster").await.into_response();
    assert_eq!(ok.status(), StatusCode::OK);
    assert_eq!(ok.headers()["content-type"], "image/png");
    let body = axum::body::to_bytes(ok.into_body(), 1024).await.unwrap();
    assert_eq!(&body[..], b"png-bytes");
    assert_eq!(get("127.0.0.1:5000", 99, "poster").await.status(), StatusCode::NOT_FOUND);
    assert_eq!(get("127.0.0.1:5000", 0, "still").await.status(), StatusCode::NOT_FOUND);
    assert_eq!(get("127.0.0.1:5000", 0, "other").await.status(), StatusCode::NOT_FOUND);
}

async fn poster_with(lookup: crate::PosterLookup) -> (axum::http::StatusCode, Vec<u8>) {
    use std::sync::atomic::AtomicU64;

    use axum::extract::{ConnectInfo, Path, State};
    use axum::response::IntoResponse;

    use crate::server::{AppState, image};
    use crate::titles::TitlePool;
    use crate::titles::tests::title;

    let dir = tempfile::tempdir().unwrap();
    let normal = dir.path().join("normal.png");
    std::fs::write(&normal, b"normal").unwrap();
    let mut t = title("film", true);
    t.poster = Some(normal);
    let (events, _rx) = tokio::sync::mpsc::unbounded_channel();
    let state = AppState {
        events,
        next_conn: std::sync::Arc::new(AtomicU64::new(1)),
        join_url: "http://x/".into(),
        pool: std::sync::Arc::new(TitlePool::new(vec![t], false)),
        textless: Some(lookup),
        images: None,
        articles: std::sync::Arc::default(),
        log: None,
    };
    let peer = "127.0.0.1:5000".parse().unwrap();
    let res = image(State(state), ConnectInfo(peer), Path((0, "poster".into()))).await.into_response();
    let status = res.status();
    (status, axum::body::to_bytes(res.into_body(), 1024).await.unwrap().to_vec())
}

fn lookup_of(file: Option<std::path::PathBuf>, delay: std::time::Duration) -> crate::PosterLookup {
    std::sync::Arc::new(move |key| {
        let file = file.clone();
        Box::pin(async move {
            assert_eq!(key, "film");
            tokio::time::sleep(delay).await;
            file
        })
    })
}

#[tokio::test]
async fn lookup_poster_is_preferred() {
    let dir = tempfile::tempdir().unwrap();
    let textless = dir.path().join("textless.jpg");
    std::fs::write(&textless, b"textless").unwrap();
    let (status, body) = poster_with(lookup_of(Some(textless), std::time::Duration::ZERO)).await;
    assert_eq!(status, axum::http::StatusCode::OK);
    assert_eq!(body, b"textless");
}

#[tokio::test]
async fn no_textless_poster_falls_back_to_the_normal_one() {
    let (_, body) = poster_with(lookup_of(None, std::time::Duration::ZERO)).await;
    assert_eq!(body, b"normal");
}

#[tokio::test(start_paused = true)]
async fn slow_lookup_falls_back_after_300_ms() {
    let dir = tempfile::tempdir().unwrap();
    let textless = dir.path().join("textless.jpg");
    std::fs::write(&textless, b"textless").unwrap();
    let slow = std::time::Duration::from_secs(10);
    let began = tokio::time::Instant::now();
    let (_, body) = poster_with(lookup_of(Some(textless), slow)).await;
    assert_eq!(body, b"normal");
    assert_eq!(began.elapsed(), std::time::Duration::from_millis(300));
}
