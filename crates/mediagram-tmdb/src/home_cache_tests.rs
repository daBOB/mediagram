use super::*;
use crate::disk_cache::DiskCachedApi;
use crate::tmdb_client::{NoKey, TmdbApi, TmdbClient};
use serde_json::{Value, json};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

const TOKEN: &str = "ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12";

/// One request as the fake server saw it: head (request line and headers) and body.
#[derive(Clone)]
struct Seen {
    head: String,
    body: Vec<u8>,
}

impl Seen {
    fn line(&self) -> &str {
        self.head.lines().next().unwrap()
    }
}

type Reply = Box<dyn Fn(&str) -> (u16, Vec<u8>) + Send + Sync>;

struct Fake {
    url: String,
    seen: Arc<Mutex<Vec<Seen>>>,
}

impl Fake {
    fn lines(&self) -> Vec<String> {
        self.seen
            .lock()
            .unwrap()
            .iter()
            .map(|s| s.line().to_string())
            .collect()
    }
    fn count(&self, method: &str) -> usize {
        self.lines()
            .iter()
            .filter(|l| l.starts_with(method))
            .count()
    }
}

fn install_tls() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

/// Serves on `listener`, answering each request line with `reply` and recording it.
fn serve(listener: tokio::net::TcpListener, reply: Reply) -> Fake {
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
            log.lock().unwrap().push(Seen { head, body });
            let head = format!(
                "HTTP/1.1 {status} X\r\ncontent-length: {}\r\nconnection: close\r\n\r\n",
                answer.len()
            );
            let _ = socket.write_all(head.as_bytes()).await;
            let _ = socket.write_all(&answer).await;
        }
    });
    Fake { url, seen }
}

async fn fake(reply: Reply) -> Fake {
    install_tls();
    serve(
        tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap(),
        reply,
    )
}

fn home(url: &str, token: Option<&str>) -> Arc<HomeCache> {
    Arc::new(HomeCache::new(
        reqwest::Client::new(),
        url,
        token.map(String::from),
    ))
}

/// Stands in for TMDB: counts calls and answers a fixed value.
struct Counting {
    calls: AtomicUsize,
    answer: Value,
}

impl Counting {
    fn new(answer: Value) -> Self {
        Self {
            calls: AtomicUsize::new(0),
            answer,
        }
    }
}

impl TmdbApi for &Counting {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> anyhow::Result<Value> {
        self.calls.fetch_add(1, Ordering::SeqCst);
        Ok(self.answer.clone())
    }
}

fn query() -> Vec<(&'static str, String)> {
    vec![("query", "x".into()), ("language", "de-DE".into())]
}

fn disk_files(dir: &std::path::Path) -> Vec<std::path::PathBuf> {
    std::fs::read_dir(dir.join("tmdb-cache"))
        .map(|d| d.map(|e| e.unwrap().path()).collect())
        .unwrap_or_default()
}

#[tokio::test]
async fn a_server_hit_answers_without_asking_tmdb() {
    let server = fake(Box::new(|_| (200, br#"{"id":7}"#.to_vec()))).await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    let got = api.get_json("/movie/7", &[]).await.unwrap();
    assert_eq!(got, json!({"id": 7}));
    assert_eq!(inner.calls.load(Ordering::SeqCst), 0);
    let files = disk_files(dir.path());
    assert_eq!(files.len(), 1);
    assert_eq!(std::fs::read(&files[0]).unwrap(), br#"{"id":7}"#);
    assert_eq!(server.count("PUT"), 0);
}

#[tokio::test]
async fn a_server_miss_asks_tmdb_and_uploads_signed() {
    let server = fake(Box::new(|_| (404, vec![]))).await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    api.get_json("/movie/1", &[]).await.unwrap();
    assert_eq!(inner.calls.load(Ordering::SeqCst), 1);
    let path = tmdb_path("/movie/1", &[]);
    let puts: Vec<Seen> = server
        .seen
        .lock()
        .unwrap()
        .iter()
        .filter(|s| s.line().starts_with("PUT"))
        .cloned()
        .collect();
    assert_eq!(puts.len(), 1);
    assert_eq!(puts[0].line(), format!("PUT {path} HTTP/1.1"));
    let sig = mlib_spec::lan_signature::sign(
        TOKEN,
        "PUT",
        &path,
        puts[0].body.len() as u64,
        &puts[0].body,
    );
    let want = format!("authorization: MGC1 {sig}");
    assert!(
        puts[0]
            .head
            .to_ascii_lowercase()
            .contains(&want.to_ascii_lowercase()),
        "{}",
        puts[0].head
    );
    assert_eq!(
        serde_json::from_slice::<Value>(&puts[0].body).unwrap(),
        json!({"id": 1})
    );
}

#[tokio::test]
async fn a_disk_hit_uploads_only_what_the_server_lacks() {
    for (has, puts) in [(404u16, 1usize), (200, 0)] {
        let server = fake(Box::new(move |line| {
            if line.starts_with("HEAD") {
                (has, vec![])
            } else {
                (201, vec![])
            }
        }))
        .await;
        let (dir, inner) = (
            tempfile::tempdir().unwrap(),
            Counting::new(json!({"id": 1})),
        );
        // Fill the disk through a cache with no server.
        DiskCachedApi::new(&inner, dir.path())
            .get_json("/movie/1", &[])
            .await
            .unwrap();
        let api = DiskCachedApi::new(&inner, dir.path())
            .with_home_cache(Some(home(&server.url, Some(TOKEN))));
        api.get_json("/movie/1", &[]).await.unwrap();
        assert_eq!(inner.calls.load(Ordering::SeqCst), 1, "the disk answered");
        assert_eq!(server.count("HEAD"), 1);
        assert_eq!(server.count("PUT"), puts, "HEAD {has}");
    }
}

/// The disk as a cache with no server leaves it, holding `/movie/1`.
async fn filled_disk(inner: &Counting) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    DiskCachedApi::new(inner, dir.path())
        .get_json("/movie/1", &[])
        .await
        .unwrap();
    dir
}

#[tokio::test]
async fn a_second_disk_hit_for_the_same_path_sends_no_second_head() {
    let server = fake(Box::new(|_| (200, vec![]))).await;
    let inner = Counting::new(json!({"id": 1}));
    let dir = filled_disk(&inner).await;
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    api.get_json("/movie/1", &[]).await.unwrap();
    api.get_json("/movie/1", &[]).await.unwrap();
    assert_eq!(server.count("HEAD"), 1);
    assert_eq!(server.count("PUT"), 0);
}

#[tokio::test]
async fn a_path_just_uploaded_is_not_asked_about_on_a_disk_hit() {
    let server = fake(Box::new(|line| {
        if line.starts_with("PUT") {
            (201, vec![])
        } else {
            (404, vec![])
        }
    }))
    .await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    api.get_json("/movie/1", &[]).await.unwrap();
    api.get_json("/movie/1", &[]).await.unwrap();
    assert_eq!(server.count("PUT"), 1);
    assert_eq!(server.count("HEAD"), 0);
}

#[tokio::test]
async fn without_a_token_a_disk_hit_asks_the_server_nothing() {
    let server = fake(Box::new(|_| (404, vec![]))).await;
    let inner = Counting::new(json!({"id": 1}));
    let dir = filled_disk(&inner).await;
    let api = DiskCachedApi::new(&inner, dir.path()).with_home_cache(Some(home(&server.url, None)));
    api.get_json("/movie/1", &[]).await.unwrap();
    assert!(server.lines().is_empty(), "{:?}", server.lines());
}

#[tokio::test]
async fn an_empty_search_page_is_not_uploaded() {
    let server = fake(Box::new(|_| (404, vec![]))).await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"results": []})),
    );
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    api.get_json("/search/movie", &query()).await.unwrap();
    assert_eq!(server.count("PUT"), 0);
}

#[test]
fn de_and_de_de_are_one_server_key() {
    let key = |lang: &str| {
        tmdb_path(
            "/search/movie",
            &[("query", "x".into()), ("language", lang.into())],
        )
    };
    assert_eq!(key("de"), key("de-DE"));
    assert_ne!(key("de"), key("en"));
    assert!(key("de").starts_with("/v1/tmdb/") && key("de").len() == "/v1/tmdb/".len() + 64);
}

#[test]
fn art_path_keeps_the_file_name() {
    assert_eq!(art_path("w780", "/abc/def.jpg"), "/v1/art/w780/def.jpg");
}

#[tokio::test]
async fn invalid_json_from_the_server_is_a_miss() {
    let server = fake(Box::new(|_| (200, b"<html>".to_vec()))).await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path()).with_home_cache(Some(home(&server.url, None)));
    assert_eq!(
        api.get_json("/movie/1", &[]).await.unwrap(),
        json!({"id": 1})
    );
    assert_eq!(inner.calls.load(Ordering::SeqCst), 1);
    let files = disk_files(dir.path());
    assert_eq!(files.len(), 1);
    assert_eq!(
        serde_json::from_slice::<Value>(&std::fs::read(&files[0]).unwrap()).unwrap(),
        json!({"id": 1})
    );
}

#[tokio::test]
async fn an_unreachable_server_is_skipped_for_30_s() {
    install_tls();
    let closed = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = closed.local_addr().unwrap();
    drop(closed);
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path())
        .with_home_cache(Some(home(&format!("http://{addr}"), Some(TOKEN))));
    api.get_json("/movie/1", &[]).await.unwrap();
    assert_eq!(inner.calls.load(Ordering::SeqCst), 1);

    // The server comes up on the same address; inside the window nobody asks it.
    let server = serve(
        tokio::net::TcpListener::bind(addr).await.unwrap(),
        Box::new(|_| (404, vec![])),
    );
    api.get_json("/movie/2", &[]).await.unwrap();
    assert_eq!(inner.calls.load(Ordering::SeqCst), 2);
    assert!(server.lines().is_empty(), "{:?}", server.lines());
}

#[tokio::test]
async fn without_a_token_nothing_is_uploaded() {
    let server = fake(Box::new(|_| (404, vec![]))).await;
    let (dir, inner) = (
        tempfile::tempdir().unwrap(),
        Counting::new(json!({"id": 1})),
    );
    let api = DiskCachedApi::new(&inner, dir.path()).with_home_cache(Some(home(&server.url, None)));
    api.get_json("/movie/1", &[]).await.unwrap();
    assert_eq!(server.count("GET"), 1);
    assert_eq!(server.count("PUT"), 0);
}

#[tokio::test]
async fn no_key_errs_on_every_request() {
    let err = NoKey.get_json("/movie/1", &[]).await.unwrap_err();
    assert_eq!(
        err.to_string(),
        "no TMDB key, and the home cache server does not hold this"
    );
}

#[tokio::test]
async fn the_tmdb_key_never_reaches_the_server() {
    let key = "0123456789abcdef0123456789abcdef";
    let tmdb = fake(Box::new(|_| (200, br#"{"id":1}"#.to_vec()))).await;
    let server = fake(Box::new(|_| (404, vec![]))).await;
    let dir = tempfile::tempdir().unwrap();
    let client = TmdbClient::new(reqwest::Client::new(), key).with_base_url(&tmdb.url);
    let api = DiskCachedApi::new(client, dir.path())
        .with_home_cache(Some(home(&server.url, Some(TOKEN))));
    api.get_json("/movie/1", &[("language", "de".into())])
        .await
        .unwrap();
    assert!(tmdb.lines()[0].contains(key), "the key does reach TMDB");
    assert_eq!(server.count("PUT"), 1);
    for seen in server.seen.lock().unwrap().iter() {
        assert!(!seen.head.contains(key) && !String::from_utf8_lossy(&seen.body).contains(key));
    }
}
