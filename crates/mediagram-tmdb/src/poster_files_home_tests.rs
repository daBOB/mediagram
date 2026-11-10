//! The walk with a home cache server: what it asks the server, and what it
//! sends it, as recorded by local stand-ins for the server and the CDN.

use super::*;
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

const TOKEN: &str = "ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12";

type Reply = Box<dyn Fn(&str) -> (u16, Vec<u8>) + Send + Sync>;

/// A plain-http stand-in that records each request line and body.
type Seen = Arc<Mutex<Vec<(String, Vec<u8>)>>>;

struct Fake {
    url: String,
    seen: Seen,
}

impl Fake {
    fn lines(&self) -> Vec<String> {
        self.seen
            .lock()
            .unwrap()
            .iter()
            .map(|(l, _)| l.clone())
            .collect()
    }
    fn put_body(&self) -> Option<Vec<u8>> {
        let seen = self.seen.lock().unwrap();
        seen.iter()
            .find(|(l, _)| l.starts_with("PUT"))
            .map(|(_, b)| b.clone())
    }
}

async fn fake(reply: Reply) -> Fake {
    let _ = rustls::crypto::ring::default_provider().install_default();
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    let seen = Arc::new(Mutex::new(Vec::new()));
    let log = seen.clone();
    tokio::spawn(async move {
        loop {
            let Ok((mut socket, _)) = listener.accept().await else {
                return;
            };
            let mut raw = Vec::new();
            let mut chunk = [0u8; 4096];
            let (head, body) = loop {
                let n = socket.read(&mut chunk).await.unwrap_or(0);
                raw.extend_from_slice(&chunk[..n]);
                if let Some(at) = raw.windows(4).position(|w| w == b"\r\n\r\n") {
                    let head = String::from_utf8_lossy(&raw[..at]).to_string();
                    let want = head
                        .lines()
                        .find_map(|l| {
                            l.to_ascii_lowercase()
                                .strip_prefix("content-length:")
                                .map(|v| v.trim().parse::<usize>().unwrap())
                        })
                        .unwrap_or(0);
                    if raw.len() >= at + 4 + want || n == 0 {
                        break (head, raw[at + 4..].to_vec());
                    }
                } else if n == 0 {
                    break (String::new(), Vec::new());
                }
            };
            let line = head.lines().next().unwrap_or_default().to_string();
            let (status, answer) = reply(&line);
            log.lock().unwrap().push((line, body));
            let head = format!(
                "HTTP/1.1 {status} X\r\ncontent-length: {}\r\nconnection: close\r\n\r\n",
                answer.len()
            );
            let _ = socket.write_all(head.as_bytes()).await;
            if !head.starts_with("HTTP/1.1 204") {
                let _ = socket.write_all(&answer).await;
            }
        }
    });
    Fake { url, seen }
}

fn home(server: &Fake) -> HomeCache {
    HomeCache::new(reqwest::Client::new(), &server.url, Some(TOKEN.into()))
}

fn poster() -> PosterRef {
    PosterRef {
        key: "tmdb-movie-1".into(),
        path: "/p.jpg".into(),
        width: None,
    }
}

fn backdrop(width: u32) -> PosterRef {
    PosterRef {
        key: "tmdb-movie-1-bg".into(),
        path: "/b.jpg".into(),
        width: Some(width),
    }
}

async fn walk(cdn: &Fake, home: &HomeCache, refs: &[PosterRef], dir: &Path) -> Vec<String> {
    let base = cdn.url.clone();
    fetch_each(
        &reqwest::Client::new(),
        refs,
        dir,
        Some(home),
        |_| {},
        |p| format!("{base}/{}/{}", p.size(), p.path.trim_start_matches('/')),
    )
    .await
    .unwrap()
}

/// A server that holds exactly the images named in `held` (by path suffix).
fn server_holding(held: &'static [&'static str]) -> Reply {
    Box::new(move |line| {
        let has = held.iter().any(|h| line.contains(h));
        match (line.split(' ').next().unwrap(), has) {
            ("GET", true) => (200, b"from-server".to_vec()),
            ("HEAD", true) => (200, Vec::new()),
            ("PUT", _) => (201, Vec::new()),
            _ => (404, Vec::new()),
        }
    })
}

fn cdn_reply() -> Reply {
    Box::new(|_| (200, b"from-cdn".to_vec()))
}

#[tokio::test]
async fn a_held_image_on_the_server_is_written_without_the_cdn() {
    let (server, cdn) = (
        fake(server_holding(&["/v1/art/w342/p.jpg"])).await,
        fake(cdn_reply()).await,
    );
    let tmp = tempfile::tempdir().unwrap();

    walk(&cdn, &home(&server), &[poster()], tmp.path()).await;

    assert_eq!(
        std::fs::read(tmp.path().join("tmdb-movie-1.jpg")).unwrap(),
        b"from-server"
    );
    assert!(cdn.lines().is_empty());
    assert_eq!(server.lines().len(), 1);
    assert!(server.lines()[0].starts_with("GET /v1/art/w342/p.jpg"));
}

#[tokio::test]
async fn a_cdn_image_is_uploaded_to_the_server() {
    let (server, cdn) = (fake(server_holding(&[])).await, fake(cdn_reply()).await);
    let tmp = tempfile::tempdir().unwrap();

    walk(&cdn, &home(&server), &[poster()], tmp.path()).await;

    assert_eq!(
        std::fs::read(tmp.path().join("tmdb-movie-1.jpg")).unwrap(),
        b"from-cdn"
    );
    assert_eq!(cdn.lines().len(), 1);
    assert!(
        server
            .lines()
            .iter()
            .any(|l| l.starts_with("PUT /v1/art/w342/p.jpg"))
    );
    assert_eq!(server.put_body().unwrap(), b"from-cdn");
}

#[tokio::test]
async fn an_image_already_on_disk_is_uploaded_only_if_the_server_lacks_it() {
    let tmp = tempfile::tempdir().unwrap();
    std::fs::write(tmp.path().join("tmdb-movie-1.jpg"), b"on-disk").unwrap();
    let cdn = fake(cdn_reply()).await;

    let lacking = fake(server_holding(&[])).await;
    walk(&cdn, &home(&lacking), &[poster()], tmp.path()).await;
    assert!(lacking.lines()[0].starts_with("HEAD /v1/art/w342/p.jpg"));
    assert_eq!(lacking.put_body().unwrap(), b"on-disk");

    let holding = fake(server_holding(&["/v1/art/w342/p.jpg"])).await;
    walk(&cdn, &home(&holding), &[poster()], tmp.path()).await;
    assert_eq!(holding.lines().len(), 1);
    assert!(cdn.lines().is_empty());
}

#[tokio::test]
async fn a_backdrop_is_keyed_by_its_width() {
    let (server, cdn) = (fake(server_holding(&[])).await, fake(cdn_reply()).await);
    let tmp = tempfile::tempdir().unwrap();

    walk(&cdn, &home(&server), &[backdrop(780)], tmp.path()).await;

    assert!(
        server
            .lines()
            .iter()
            .any(|l| l.starts_with("GET /v1/art/w780/b.jpg"))
    );
    assert!(
        server
            .lines()
            .iter()
            .any(|l| l.starts_with("PUT /v1/art/w780/b.jpg"))
    );
}

#[tokio::test]
async fn a_missing_width_on_the_server_is_fetched_from_the_cdn_at_this_width() {
    let (server, cdn) = (
        fake(server_holding(&["/v1/art/w1280/b.jpg"])).await,
        fake(cdn_reply()).await,
    );
    let tmp = tempfile::tempdir().unwrap();

    walk(&cdn, &home(&server), &[backdrop(780)], tmp.path()).await;

    assert_eq!(cdn.lines().len(), 1);
    assert!(cdn.lines()[0].starts_with("GET /w780/b.jpg"));
    assert!(
        server
            .lines()
            .iter()
            .any(|l| l.starts_with("PUT /v1/art/w780/b.jpg"))
    );
    assert!(!server.lines().iter().any(|l| l.contains("w1280")));
}

#[tokio::test]
async fn an_empty_server_body_is_a_miss_and_the_cdn_is_asked() {
    let server = fake(Box::new(|line| match line.split(' ').next().unwrap() {
        "GET" => (200, Vec::new()),
        "PUT" => (201, Vec::new()),
        _ => (404, Vec::new()),
    }))
    .await;
    let cdn = fake(cdn_reply()).await;
    let tmp = tempfile::tempdir().unwrap();

    walk(&cdn, &home(&server), &[poster()], tmp.path()).await;

    assert_eq!(cdn.lines().len(), 1);
    assert_eq!(
        std::fs::read(tmp.path().join("tmdb-movie-1.jpg")).unwrap(),
        b"from-cdn"
    );
    assert_eq!(server.put_body().unwrap(), b"from-cdn");
}

#[tokio::test]
async fn a_held_backdrop_is_uploaded_under_the_width_it_has() {
    let tmp = tempfile::tempdir().unwrap();
    std::fs::write(tmp.path().join("tmdb-movie-1-bg.jpg"), b"wide").unwrap();
    std::fs::write(tmp.path().join("tmdb-movie-1-bg.width"), "1280").unwrap();
    let (server, cdn) = (fake(server_holding(&[])).await, fake(cdn_reply()).await);

    walk(&cdn, &home(&server), &[backdrop(780)], tmp.path()).await;

    let lines = server.lines();
    assert!(
        lines
            .iter()
            .any(|l| l.starts_with("HEAD /v1/art/w1280/b.jpg"))
    );
    assert!(
        lines
            .iter()
            .any(|l| l.starts_with("PUT /v1/art/w1280/b.jpg"))
    );
    assert!(!lines.iter().any(|l| l.contains("w780")));
    assert_eq!(server.put_body().unwrap(), b"wide");
}
