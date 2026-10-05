use super::*;
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use tokio::io::{AsyncReadExt, AsyncWriteExt};

/// A local stand-in for the image CDN: answers every request with a fixed
/// body, or with a 500 while `up` is false, and counts what was asked.
struct Cdn {
    url: String,
    up: Arc<AtomicBool>,
    hits: Arc<AtomicUsize>,
}

async fn cdn() -> Cdn {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let url = format!("http://{}", listener.local_addr().unwrap());
    let up = Arc::new(AtomicBool::new(true));
    let hits = Arc::new(AtomicUsize::new(0));
    let (up_task, hits_task) = (up.clone(), hits.clone());
    tokio::spawn(async move {
        loop {
            let Ok((mut socket, _)) = listener.accept().await else { return };
            let mut request = [0u8; 2048];
            let _ = socket.read(&mut request).await;
            hits_task.fetch_add(1, Ordering::SeqCst);
            let reply: &[u8] = if up_task.load(Ordering::SeqCst) {
                b"HTTP/1.1 200 OK\r\ncontent-length: 3\r\nconnection: close\r\n\r\nnew"
            } else {
                b"HTTP/1.1 500 Oops\r\ncontent-length: 0\r\nconnection: close\r\n\r\n"
            };
            let _ = socket.write_all(reply).await;
        }
    });
    Cdn { url, up, hits }
}

fn backdrop(width: u32) -> PosterRef {
    PosterRef { key: "tmdb-movie-1-bg".into(), path: "/b.jpg".into(), backdrop_width: Some(width) }
}

fn poster() -> PosterRef {
    PosterRef { key: "tmdb-movie-1".into(), path: "/p.jpg".into(), backdrop_width: None }
}

/// Runs the walk against the stand-in CDN.
async fn run(cdn: &Cdn, refs: &[PosterRef], dir: &Path) -> Vec<String> {
    let _ = rustls::crypto::ring::default_provider().install_default();
    let base = cdn.url.clone();
    fetch_each(&reqwest::Client::new(), refs, dir, |_| {}, |p| format!("{base}{}", p.path))
        .await
        .unwrap()
}

fn hold(dir: &Path, key: &str, record: Option<&str>) {
    std::fs::create_dir_all(dir).unwrap();
    std::fs::write(dir.join(format!("{key}.jpg")), b"old").unwrap();
    if let Some(width) = record {
        std::fs::write(dir.join(format!("{key}.width")), width).unwrap();
    }
}

fn recorded(dir: &Path) -> String {
    std::fs::read_to_string(dir.join("tmdb-movie-1-bg.width")).unwrap()
}

fn bytes(dir: &Path, key: &str) -> Vec<u8> {
    std::fs::read(dir.join(format!("{key}.jpg"))).unwrap()
}

#[tokio::test]
async fn a_backdrop_held_narrower_than_wanted_is_fetched_again_and_the_record_follows() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    hold(tmp.path(), "tmdb-movie-1-bg", Some("780"));
    let refs = [backdrop(1280)];
    assert_eq!(already_held(&refs, tmp.path()), 0);

    let written = run(&cdn, &refs, tmp.path()).await;

    assert_eq!(written, vec!["tmdb-movie-1-bg".to_string()]);
    assert_eq!(bytes(tmp.path(), "tmdb-movie-1-bg"), b"new");
    assert_eq!(recorded(tmp.path()), "1280");
    assert_eq!(already_held(&refs, tmp.path()), 1);
}

#[tokio::test]
async fn a_backdrop_held_wider_than_wanted_is_kept() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    hold(tmp.path(), "tmdb-movie-1-bg", Some("1280"));
    let refs = [backdrop(780)];
    assert_eq!(already_held(&refs, tmp.path()), 1);

    run(&cdn, &refs, tmp.path()).await;

    assert_eq!(cdn.hits.load(Ordering::SeqCst), 0);
    assert_eq!(bytes(tmp.path(), "tmdb-movie-1-bg"), b"old");
}

#[tokio::test]
async fn a_backdrop_with_no_record_is_fetched_once_then_held() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    hold(tmp.path(), "tmdb-movie-1-bg", None);
    let refs = [backdrop(780)];

    run(&cdn, &refs, tmp.path()).await;
    run(&cdn, &refs, tmp.path()).await;

    assert_eq!(cdn.hits.load(Ordering::SeqCst), 1);
    assert_eq!(recorded(tmp.path()), "780");
}

#[tokio::test]
async fn a_failed_refetch_keeps_the_old_file_counts_it_written_and_tries_again_next_run() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    hold(tmp.path(), "tmdb-movie-1-bg", Some("780"));
    let refs = [backdrop(1280)];
    cdn.up.store(false, Ordering::SeqCst);

    let written = run(&cdn, &refs, tmp.path()).await;

    assert_eq!(written, vec!["tmdb-movie-1-bg".to_string()]);
    assert_eq!(bytes(tmp.path(), "tmdb-movie-1-bg"), b"old");
    assert_eq!(recorded(tmp.path()), "780");

    cdn.up.store(true, Ordering::SeqCst);
    run(&cdn, &refs, tmp.path()).await;
    assert_eq!(bytes(tmp.path(), "tmdb-movie-1-bg"), b"new");
    assert_eq!(recorded(tmp.path()), "1280");
}

#[tokio::test]
async fn a_failed_first_fetch_is_still_a_failure() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    cdn.up.store(false, Ordering::SeqCst);

    let written = run(&cdn, &[backdrop(1280)], tmp.path()).await;

    assert!(written.is_empty());
    assert!(!tmp.path().join("tmdb-movie-1-bg.width").exists());
}

#[tokio::test]
async fn a_poster_on_disk_needs_no_record_and_leaves_none() {
    let (cdn, tmp) = (cdn().await, tempfile::tempdir().unwrap());
    hold(tmp.path(), "tmdb-movie-1", None);
    let refs = [poster()];
    assert_eq!(already_held(&refs, tmp.path()), 1);

    run(&cdn, &refs, tmp.path()).await;
    assert_eq!(cdn.hits.load(Ordering::SeqCst), 0);

    let fresh = tempfile::tempdir().unwrap();
    run(&cdn, &refs, fresh.path()).await;
    assert!(!fresh.path().join("tmdb-movie-1.width").exists());
}
