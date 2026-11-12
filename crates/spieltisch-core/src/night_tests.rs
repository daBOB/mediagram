use std::net::TcpListener;
use std::sync::Mutex;

use tokio::io::{AsyncReadExt, AsyncWriteExt};

use super::*;
use crate::media_images::MediaImageKind;

fn port_of(urls: &NightUrls) -> u16 {
    urls.tv_url.rsplit(':').next().unwrap().trim_end_matches("/tv").parse().unwrap()
}

/// In a parallel test run a just-closed port can read as taken for a few
/// microseconds (never in a serial run); a port the night really leaked
/// stays taken past the wait.
fn is_free(port: u16) -> bool {
    let deadline = std::time::Instant::now() + std::time::Duration::from_millis(500);
    loop {
        if TcpListener::bind(("0.0.0.0", port)).is_ok() {
            return true;
        }
        if std::time::Instant::now() >= deadline {
            return false;
        }
        std::thread::sleep(std::time::Duration::from_millis(1));
    }
}

/// The media app's images as Kotlin would hand them over: a copy per ask,
/// its bytes naming what was asked for, and a textless poster only when it has one.
#[derive(Default)]
struct Copies {
    dir: Option<tempfile::TempDir>,
    textless: bool,
    /// How long a textless answer takes, as a media app that must ask TMDB does.
    textless_after: std::time::Duration,
    asked: Mutex<Vec<(String, MediaImageKind)>>,
}

impl MediaImages for Copies {
    fn fetch(&self, key: String, kind: MediaImageKind) -> Option<String> {
        self.asked.lock().unwrap().push((key.clone(), kind));
        if kind == MediaImageKind::Textless {
            std::thread::sleep(self.textless_after);
            if !self.textless {
                return None;
            }
        }
        let file = self.dir.as_ref()?.path().join(format!("{kind:?}-{key}"));
        std::fs::write(&file, [b"\xFF\xD8\xFF ".as_slice(), format!("{kind:?} {key}").as_bytes()].concat()).ok()?;
        Some(file.to_string_lossy().into_owned())
    }
}

fn copies(textless: bool) -> Arc<Copies> {
    Arc::new(Copies { dir: Some(tempfile::tempdir().unwrap()), textless, ..Copies::default() })
}

fn none() -> Arc<dyn MediaImages> {
    Arc::new(Copies::default())
}

/// The fixture both apps are held to: the media app writes it, this night reads it.
const FIXTURE: &str = include_str!("../../mediagram-game/tests/fixtures/night-titles.json");

/// A raw GET from the box itself: the status code and the body after the image's JPEG marker.
async fn get(port: u16, path: &str) -> (u16, String) {
    let mut s = tokio::net::TcpStream::connect(("127.0.0.1", port)).await.unwrap();
    s.write_all(format!("GET {path} HTTP/1.1\r\nhost: x\r\nconnection: close\r\n\r\n").as_bytes()).await.unwrap();
    let mut raw = Vec::new();
    s.read_to_end(&mut raw).await.unwrap();
    let raw = String::from_utf8_lossy(&raw).into_owned();
    let (head, body) = raw.split_once("\r\n\r\n").unwrap();
    (head[9..12].parse().unwrap(), body.trim_start_matches('\u{FFFD}').trim().to_string())
}

#[tokio::test]
async fn start_twice_replaces_the_first() {
    let slot = AsyncMutex::new(None);
    let first = port_of(&replace(&slot, Vec::new(), ArticleList::default(), true, 0, none(), None).await.unwrap());
    let second = port_of(&replace(&slot, Vec::new(), ArticleList::default(), true, 0, none(), None).await.unwrap());
    assert!(first == second || is_free(first), "the first night's port must be released");

    stop(&slot).await;
    assert!(is_free(second), "stopping must free the port");
    assert!(!back(&slot).await, "no night runs after stop");
    stop(&slot).await;
}

#[tokio::test]
async fn a_night_without_a_game_has_nothing_to_step_back_from() {
    let slot = AsyncMutex::new(None);
    let urls = replace(&slot, Vec::new(), ArticleList::default(), true, 0, none(), None).await.unwrap();
    assert!(urls.tv_url.starts_with("http://127.0.0.1:"));

    assert!(!back(&slot).await, "the lobby is already where Back leads");
    stop(&slot).await;
}

#[tokio::test]
async fn the_exported_night_starts_and_stops() {
    let data_dir = std::env::temp_dir().to_string_lossy().into_owned();
    let config = SpieltischConfig { kids_locked: true, data_dir, titles: FIXTURE.into(), articles: ARTICLES.into() };
    let urls = start_night(config, none()).await.unwrap();
    assert!(!urls.join_url.is_empty());
    stop_night().await;
    assert!(!night_back().await);
}

#[test]
fn the_media_apps_titles_are_read_in_its_shape() {
    let keys: Vec<String> = titles_of(FIXTURE).into_iter().map(|t| t.key).collect();
    assert_eq!(keys, ["A", "B", "show:Dark"]);
}

#[test]
fn titles_that_cannot_be_read_are_none() {
    for bad in ["", "not json", "{}", r#"[{"key": "A"}]"#] {
        assert!(titles_of(bad).is_empty(), "{bad:?}");
    }
}

#[tokio::test]
async fn images_the_box_cannot_open_are_fetched_from_the_media_app() {
    let slot = AsyncMutex::new(None);
    let images = copies(false);
    // The fixture's paths are the media app's, which this app cannot read.
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), false, 0, images.clone(), None).await.unwrap());

    assert_eq!(get(port, "/img/0/poster").await, (200, "Poster A".into()), "no textless poster: the normal one");
    assert_eq!(get(port, "/img/0/still").await, (200, "Still A".into()));
    let asked = images.asked.lock().unwrap().clone();
    let a = |kind| ("A".to_string(), kind);
    assert_eq!(asked, [a(MediaImageKind::Textless), a(MediaImageKind::Poster), a(MediaImageKind::Still)]);
    stop(&slot).await;
}

#[tokio::test]
async fn a_textless_poster_comes_through_the_same_fetch() {
    let slot = AsyncMutex::new(None);
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), false, 0, copies(true), None).await.unwrap());

    assert_eq!(get(port, "/img/0/poster").await, (200, "Textless A".into()));
    stop(&slot).await;
}

#[tokio::test]
async fn a_locked_night_holds_only_the_kid_safe_titles() {
    let slot = AsyncMutex::new(None);
    let images = copies(false);
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), true, 0, images.clone(), None).await.unwrap());

    // Alien is not kid-safe, so the night's first title is Bambi, which has no images, and its second is Dark.
    assert_eq!(get(port, "/img/0/poster").await.0, 404);
    assert_eq!(get(port, "/img/1/poster").await, (200, "Poster show:Dark".into()));
    assert_eq!(get(port, "/img/2/poster").await.0, 404, "only two titles are kid-safe");
    assert!(images.asked.lock().unwrap().iter().all(|(key, _)| key != "A"), "Alien is never asked for");
    stop(&slot).await;
}

#[tokio::test]
async fn a_title_without_a_textless_poster_is_asked_about_once_a_night() {
    let slot = AsyncMutex::new(None);
    let images = copies(false);
    let textless = |images: &Copies| images.asked.lock().unwrap().iter().filter(|(_, kind)| *kind == MediaImageKind::Textless).count();
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), false, 0, images.clone(), None).await.unwrap());

    for _ in 0..3 {
        assert_eq!(get(port, "/img/0/poster").await, (200, "Poster A".into()));
    }
    assert_eq!(textless(&images), 1);

    // A new night asks again: the media app may have fetched one since.
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), false, 0, images.clone(), None).await.unwrap());
    get(port, "/img/0/poster").await;
    assert_eq!(textless(&images), 2);
    stop(&slot).await;
}

#[tokio::test]
async fn a_textless_miss_slower_than_the_wait_is_still_remembered() {
    let slot = AsyncMutex::new(None);
    let images = Arc::new(Copies {
        dir: Some(tempfile::tempdir().unwrap()),
        textless_after: std::time::Duration::from_millis(400),
        ..Copies::default()
    });
    let textless = |images: &Copies| images.asked.lock().unwrap().iter().filter(|(_, kind)| *kind == MediaImageKind::Textless).count();
    let port = port_of(&replace(&slot, titles_of(FIXTURE), ArticleList::default(), false, 0, images.clone(), None).await.unwrap());

    // The route gives the textless poster 300 ms and serves the normal one; the miss lands later.
    assert_eq!(get(port, "/img/0/poster").await, (200, "Poster A".into()));
    tokio::time::sleep(std::time::Duration::from_millis(300)).await;
    assert_eq!(get(port, "/img/0/poster").await, (200, "Poster A".into()));
    assert_eq!(textless(&images), 1, "the slow miss was remembered");
    stop(&slot).await;
}

/// Preisfrage's list as the cache server hands it over.
const ARTICLES: &str = r#"{"version": 1, "fetched": "2026-11-12T19:14:54.484Z", "articles": [
  {"asin": "B0GF25D3LF", "name": "Halter", "title": "Halter", "cents": 899, "reviews": 21}]}"#;

#[test]
fn the_cache_servers_article_list_is_read_and_anything_else_is_none() {
    assert_eq!(articles_of(ARTICLES).articles.len(), 1);
    for none in ["", "[]", "{", "<html>Not Found</html>"] {
        assert!(articles_of(none).articles.is_empty(), "{none:?}");
    }
}

#[tokio::test]
async fn an_articles_photo_comes_through_the_same_fetch() {
    let slot = AsyncMutex::new(None);
    let images = copies(false);
    let port = port_of(&replace(&slot, Vec::new(), articles_of(ARTICLES), false, 0, images.clone(), None).await.unwrap());

    assert_eq!(get(port, "/img/article/B0GF25D3LF").await, (200, "Article B0GF25D3LF".into()));
    assert_eq!(get(port, "/img/article/B0OTHER000").await.0, 404);
    assert_eq!(images.asked.lock().unwrap().clone(), [("B0GF25D3LF".to_string(), MediaImageKind::Article)]);
    stop(&slot).await;
}
