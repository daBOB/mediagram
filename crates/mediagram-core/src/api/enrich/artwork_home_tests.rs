//! A fetch run with the household's cache server: a device with no TMDB key
//! reads what the server holds, and a keyed one adds what TMDB answered.
//! The server is a local stand-in that records each request it is sent.

use std::path::Path;
use std::sync::{Arc, Mutex};

use serde_json::{Value, json};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

use super::*;
use crate::api::test_support::index_at;

const TOKEN: &str = "ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12ab12";
const POSTER: &[u8] = b"poster-from-the-home-cache";

type Seen = Arc<Mutex<Vec<(String, Vec<u8>)>>>;

/// The request lines and bodies the stand-in was sent, in order.
struct Fake {
    url: String,
    seen: Seen,
}

impl Fake {
    fn seen(&self) -> Vec<(String, Vec<u8>)> {
        self.seen.lock().unwrap().clone()
    }
}

/// A plain-http server answering each request line with `reply`.
async fn fake(reply: impl Fn(&str) -> (u16, Vec<u8>) + Send + 'static) -> Fake {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    let seen: Seen = Arc::default();
    let log = seen.clone();
    tokio::spawn(async move {
        while let Ok((mut socket, _)) = listener.accept().await {
            let mut raw = Vec::new();
            let mut chunk = [0u8; 4096];
            let (head, body) = loop {
                let n = socket.read(&mut chunk).await.unwrap_or(0);
                raw.extend_from_slice(&chunk[..n]);
                let Some(at) = raw.windows(4).position(|w| w == b"\r\n\r\n") else {
                    if n == 0 {
                        break (String::new(), Vec::new());
                    }
                    continue;
                };
                let head = String::from_utf8_lossy(&raw[..at]).to_string();
                let want = head
                    .lines()
                    .find_map(|l| l.to_ascii_lowercase().strip_prefix("content-length:").map(|v| v.trim().parse().unwrap()))
                    .unwrap_or(0usize);
                if raw.len() >= at + 4 + want || n == 0 {
                    break (head, raw[at + 4..].to_vec());
                }
            };
            let line = head.lines().next().unwrap_or_default().to_string();
            let (status, answer) = reply(&line);
            log.lock().unwrap().push((line.clone(), body));
            let answer = if line.starts_with("HEAD") { Vec::new() } else { answer };
            // As the real server labels them: an image not labelled one is a miss.
            let kind = if line.contains("/v1/art/") { "image/jpeg" } else { "application/json" };
            let head = format!("HTTP/1.1 {status} X\r\ncontent-type: {kind}\r\ncontent-length: {}\r\nconnection: close\r\n\r\n", answer.len());
            let _ = socket.write_all(head.as_bytes()).await;
            let _ = socket.write_all(&answer).await;
        }
    });
    Fake { url, seen }
}

/// What TMDB says about Fight Club, as far as a fetch run reads it.
fn details() -> Value {
    json!({ "id": 550, "title": "Fight Club", "overview": "An insomniac office worker.", "poster_path": "/p.jpg" })
}

/// An installed catalog of one film the provider numbers 550 and one course
/// it numbers nothing of.
fn library(dir: &Path) -> Arc<Core> {
    let conn = index_at(dir);
    for (set_id, kind, tmdb) in [("01SETFIXTURE000000000000001", "movie", Some(550)), ("01SETFIXTURE000000000000002", "tut", None)] {
        conn.execute(
            "INSERT INTO sets(set_id, kind, tmdb, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, ?2, ?3, 'mkv', 0, 0, 'complete', 0, 1)",
            rusqlite::params![set_id, kind, tmdb],
        )
        .unwrap();
    }
    Core::at(dir)
}

/// The household's second device: no key, but the keyed one already filled
/// the server. The run never verifies a key — an empty one sent to TMDB is
/// refused, offline or not, and would fail the run — so everything below
/// came from the server.
#[tokio::test]
async fn keyless_fetch_reads_artwork_from_the_home_cache() {
    let dir = tempfile::tempdir().unwrap();
    let core = library(dir.path());
    let server = fake(|line| {
        if line.starts_with("GET /v1/tmdb/") {
            (200, serde_json::to_vec(&details()).unwrap())
        } else if line.starts_with("GET /v1/art/") {
            (200, POSTER.to_vec())
        } else {
            (200, Vec::new())
        }
    })
    .await;
    core.set_home_cache(Some(server.url.clone()), None);

    let report = fetch_missing(&core, String::new(), "en-US".into(), 780)
        .await
        .expect("a keyless run with a home cache server succeeds");

    assert_eq!(report.posters_fetched, 1);
    assert_eq!(report.details_recorded, 1);
    assert_eq!(report.failed, 0);
    let poster = store::artwork_dir(&core).join("tmdb-movie-550.jpg");
    assert_eq!(std::fs::read(poster).unwrap(), POSTER);
    let asked = server.seen();
    assert!(asked.iter().any(|(line, _)| line.starts_with("GET /v1/art/w342/p.jpg")), "{asked:?}");
    assert!(!asked.iter().any(|(line, _)| line.starts_with("PUT")), "a device with no token uploads nothing");
}

/// Away from home with no key: nothing can be asked, so nothing is — the
/// report a library gets when there is nothing to ask the provider about.
#[tokio::test]
async fn keyless_without_a_home_cache_fetches_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let core = library(dir.path());

    let report = fetch_missing(&core, String::new(), "en-US".into(), 780)
        .await
        .expect("a keyless run with no server asks nobody");

    assert_eq!(report, FetchReport { no_provider_id: 1, ..FetchReport::default() });
    assert!(!store::artwork_dir(&core).join("tmdb-movie-550.jpg").exists());
}

/// The server has not been filled yet, or has gone to sleep: a title it
/// cannot answer waits for the keyed device, so a keyless run reports what a
/// device with no key reported before there was a server — nothing, and no
/// failure. Nothing goes to the TMDB API: with no key the run has nothing to
/// ask it with, only the server.
#[tokio::test]
async fn keyless_titles_the_server_lacks_are_not_failures() {
    let dir = tempfile::tempdir().unwrap();
    let core = library(dir.path());
    let server = fake(|_| (404, Vec::new())).await;
    core.set_home_cache(Some(server.url.clone()), None);

    let report = fetch_missing(&core, String::new(), "en-US".into(), 780)
        .await
        .expect("a keyless run the server cannot answer still ends cleanly");

    assert_eq!(report, FetchReport { no_provider_id: 1, ..FetchReport::default() });
    let asked = server.seen();
    assert!(asked.iter().any(|(line, _)| line.starts_with("GET /v1/tmdb/")), "the server was asked: {asked:?}");
}

/// Answers the key check and every title the way TMDB answers Fight Club.
struct Tmdb;

impl TmdbApi for Tmdb {
    async fn get_json(&self, path: &str, _: &[(&str, String)]) -> anyhow::Result<Value> {
        Ok(if path == "/authentication" { json!({ "success": true }) } else { details() })
    }
}

/// The keyed device fills the server: what TMDB answered is uploaded, so the
/// next device reads it without a key.
#[tokio::test]
async fn a_keyed_fetch_uploads_what_tmdb_answered() {
    let dir = tempfile::tempdir().unwrap();
    let core = library(dir.path());
    let server = fake(|line| if line.starts_with("PUT") { (201, Vec::new()) } else { (404, Vec::new()) }).await;
    core.set_home_cache(Some(server.url.clone()), Some(TOKEN.into()));
    let plan = plan_fetch(&core, "en-US").unwrap();
    let offline = reqwest::Client::builder().resolve("image.tmdb.org", "127.0.0.1:1".parse().unwrap()).build().unwrap();

    verify_then_fetch(&core, Tmdb, &offline, &plan, 780).await.expect("a keyed run succeeds");

    let asked = server.seen();
    let (_, body) = asked
        .iter()
        .find(|(line, _)| line.starts_with("PUT /v1/tmdb/"))
        .unwrap_or_else(|| panic!("nothing TMDB answered was uploaded: {asked:?}"));
    assert_eq!(serde_json::from_slice::<Value>(body).unwrap(), details());
}
