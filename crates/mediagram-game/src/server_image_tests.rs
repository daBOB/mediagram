type Asked = std::sync::Arc<std::sync::Mutex<Vec<(String, crate::ImageKind)>>>;

/// An embedder that answers every ask with `copy` and remembers what it was asked.
fn copies(copy: Option<std::path::PathBuf>) -> (crate::ImageLookup, Asked) {
    let asked = Asked::default();
    let seen = asked.clone();
    let lookup: crate::ImageLookup = std::sync::Arc::new(move |key, kind| {
        seen.lock().unwrap().push((key, kind));
        let copy = copy.clone();
        Box::pin(async move { copy })
    });
    (lookup, asked)
}

/// `/img/0/{which}` from the box, for a pool of `t` alone: status, content type and body.
async fn serve(t: crate::GameTitle, images: Option<crate::ImageLookup>, which: &str) -> (u16, String, Vec<u8>) {
    use std::sync::atomic::AtomicU64;

    use axum::extract::{ConnectInfo, Path, State};
    use axum::response::IntoResponse;

    use crate::server::{AppState, image};
    use crate::titles::TitlePool;

    let (events, _rx) = tokio::sync::mpsc::unbounded_channel();
    let state = AppState {
        events,
        next_conn: std::sync::Arc::new(AtomicU64::new(1)),
        join_url: "http://x/".into(),
        pool: std::sync::Arc::new(TitlePool::new(vec![t], false)),
        textless: None,
        images,
        articles: std::sync::Arc::default(),
        log: None,
    };
    let peer = "127.0.0.1:5000".parse().unwrap();
    let res = image(State(state), ConnectInfo(peer), Path((0, which.into()))).await.into_response();
    let kind = res.headers().get("content-type").map(|v| v.to_str().unwrap().to_string()).unwrap_or_default();
    (res.status().as_u16(), kind, axum::body::to_bytes(res.into_body(), 1024).await.unwrap().to_vec())
}

/// A film whose images sit where the host cannot read them, as another app's files do.
fn elsewhere() -> crate::GameTitle {
    let mut t = crate::titles::tests::title("film", true);
    t.poster = Some("/nowhere/posters/film.jpg".into());
    t.still = Some("/nowhere/stills/film.jpg".into());
    t
}

const PNG: &[u8] = b"\x89PNG\r\n\x1a\nrest";

#[tokio::test]
async fn an_unreadable_poster_is_served_from_the_embedders_copy() {
    let dir = tempfile::tempdir().unwrap();
    let copy = dir.path().join("poster-film");
    std::fs::write(&copy, PNG).unwrap();
    let (lookup, asked) = copies(Some(copy));

    let (status, kind, body) = serve(elsewhere(), Some(lookup), "poster").await;

    assert_eq!((status, kind.as_str(), body.as_slice()), (200, "image/png", PNG), "typed by its bytes, having no extension");
    assert_eq!(*asked.lock().unwrap(), [("film".to_string(), crate::ImageKind::Poster)]);
}

#[tokio::test]
async fn a_still_is_asked_for_as_a_still() {
    let dir = tempfile::tempdir().unwrap();
    let copy = dir.path().join("still-film");
    std::fs::write(&copy, [0xFF, 0xD8, 0xFF, 0xE0]).unwrap();
    let (lookup, asked) = copies(Some(copy));

    let (status, kind, _) = serve(elsewhere(), Some(lookup), "still").await;

    assert_eq!((status, kind.as_str()), (200, "image/jpeg"));
    assert_eq!(*asked.lock().unwrap(), [("film".to_string(), crate::ImageKind::Still)]);
}

#[tokio::test]
async fn a_readable_poster_never_asks_the_embedder() {
    let dir = tempfile::tempdir().unwrap();
    let own = dir.path().join("own.png");
    std::fs::write(&own, b"own").unwrap();
    let mut t = elsewhere();
    t.poster = Some(own);
    let (lookup, asked) = copies(None);

    let (status, _, body) = serve(t, Some(lookup), "poster").await;

    assert_eq!((status, body.as_slice()), (200, b"own".as_slice()));
    assert!(asked.lock().unwrap().is_empty());
}

#[tokio::test]
async fn no_copy_and_no_hook_are_both_not_found() {
    let (lookup, _) = copies(None);
    assert_eq!(serve(elsewhere(), Some(lookup), "poster").await.0, 404);
    assert_eq!(serve(elsewhere(), None, "poster").await.0, 404, "the media app's own night, unchanged");
}

#[tokio::test]
async fn a_title_without_the_image_has_nothing_to_ask_for() {
    let mut t = elsewhere();
    t.still = None;
    let (lookup, asked) = copies(None);

    assert_eq!(serve(t, Some(lookup), "still").await.0, 404);
    assert!(asked.lock().unwrap().is_empty());
}

#[tokio::test]
async fn a_copy_that_is_no_image_is_not_served() {
    let dir = tempfile::tempdir().unwrap();
    let copy = dir.path().join("poster-film");
    std::fs::write(&copy, b"<html>not an image</html>").unwrap();
    let (lookup, _) = copies(Some(copy));

    assert_eq!(serve(elsewhere(), Some(lookup), "poster").await.0, 404);
}

#[tokio::test]
async fn a_copy_is_webp_only_with_its_riff_header() {
    let dir = tempfile::tempdir().unwrap();
    let webp = dir.path().join("webp");
    std::fs::write(&webp, b"RIFF\x10\0\0\0WEBPVP8 ").unwrap();
    let (lookup, _) = copies(Some(webp));
    assert_eq!(serve(elsewhere(), Some(lookup), "poster").await.1, "image/webp");

    let other = dir.path().join("other");
    std::fs::write(&other, b"XXXX\x10\0\0\0WEBPVP8 ").unwrap();
    let (lookup, _) = copies(Some(other));
    assert_eq!(serve(elsewhere(), Some(lookup), "poster").await.0, 404);
}

/// `/img/article/{asin}` from `peer`, tonight's list holding `B0GF25D3LF` alone.
async fn serve_article(images: Option<crate::ImageLookup>, asin: &str, peer: &str) -> (u16, String, Vec<u8>) {
    use std::sync::atomic::AtomicU64;

    use axum::extract::{ConnectInfo, Path, State};
    use axum::response::IntoResponse;

    use crate::article_photos::article_photo;
    use crate::server::AppState;

    let list = r#"{"fetched": "2026-11-12", "articles": [{"asin": "B0GF25D3LF", "name": "Halter", "title": "Halter", "cents": 899, "reviews": 21}]}"#;
    let (events, _rx) = tokio::sync::mpsc::unbounded_channel();
    let state = AppState {
        events,
        next_conn: std::sync::Arc::new(AtomicU64::new(1)),
        join_url: "http://x/".into(),
        pool: std::sync::Arc::default(),
        textless: None,
        images,
        articles: std::sync::Arc::new(crate::ArticleList::from_json(list).unwrap()),
        log: None,
    };
    let res = article_photo(State(state), ConnectInfo(peer.parse().unwrap()), Path(asin.into())).await.into_response();
    let kind = res.headers().get("content-type").map(|v| v.to_str().unwrap().to_string()).unwrap_or_default();
    (res.status().as_u16(), kind, axum::body::to_bytes(res.into_body(), 1024).await.unwrap().to_vec())
}

const JPEG: &[u8] = b"\xFF\xD8\xFFrest";

#[tokio::test]
async fn an_articles_photo_is_served_to_the_box_and_to_phones() {
    let dir = tempfile::tempdir().unwrap();
    let copy = dir.path().join("photos-B0GF25D3LF.jpg");
    std::fs::write(&copy, JPEG).unwrap();
    let (lookup, asked) = copies(Some(copy));

    for peer in ["127.0.0.1:5000", "192.168.0.23:5000"] {
        let (status, kind, body) = serve_article(Some(lookup.clone()), "B0GF25D3LF", peer).await;
        assert_eq!((status, kind.as_str(), body.as_slice()), (200, "image/jpeg", JPEG), "{peer}");
    }
    assert_eq!(asked.lock().unwrap()[0], ("B0GF25D3LF".to_string(), crate::ImageKind::Article));
}

#[tokio::test]
async fn an_article_not_on_tonights_list_is_not_asked_for() {
    let (lookup, asked) = copies(Some("/anything.jpg".into()));
    for asin in ["B0OTHER000", "..", "photos-B0GF25D3LF"] {
        assert_eq!(serve_article(Some(lookup.clone()), asin, "127.0.0.1:5000").await.0, 404, "{asin}");
    }
    assert!(asked.lock().unwrap().is_empty());
}

#[tokio::test]
async fn no_photo_from_the_embedder_is_not_found() {
    let (lookup, _) = copies(None);
    assert_eq!(serve_article(Some(lookup), "B0GF25D3LF", "127.0.0.1:5000").await.0, 404);
    assert_eq!(serve_article(None, "B0GF25D3LF", "127.0.0.1:5000").await.0, 404);
}
