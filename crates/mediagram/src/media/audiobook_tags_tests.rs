use super::*;
use crate::media::inspect::inspect;
use crate::test_fakes::media::{ffmpeg_required, make_tagged_mp3};

const TITLE: &str = "Bibi & Tina, Folge 121: Stürmische Weihnachten";

#[tokio::test]
async fn an_attached_cover_is_not_the_video() {
    if !ffmpeg_required("an_attached_cover_is_not_the_video") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let file = make_tagged_mp3(
        dir.path(),
        "book.mp3",
        Some(TITLE),
        Some("Kinderbücher"),
        true,
    );

    let info = inspect(&file).await.unwrap();

    assert_eq!(info.vcodec, None);
    assert_eq!(info.quality, None);
    assert_eq!(info.width, None);
    assert_eq!(info.acodec.as_deref(), Some("mp3"));
    assert_eq!(info.container, "mp3");
}

#[tokio::test]
async fn tags_give_title_genre_and_cover() {
    if !ffmpeg_required("tags_give_title_genre_and_cover") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let file = make_tagged_mp3(
        dir.path(),
        "book.mp3",
        Some(TITLE),
        Some("Kinderbücher"),
        true,
    );

    let tags = read(&file).await.unwrap();
    assert_eq!(tags.title, TITLE);
    assert_eq!(tags.genre.as_deref(), Some("Kinderbücher"));
    let cover = tags.cover.expect("the file carries a cover");
    assert_eq!(cover.mime, "image/jpeg");
    let bytes = cover_bytes(&file, &cover).await.unwrap();
    assert_eq!(&bytes[..2], &[0xFF, 0xD8]);
}

#[tokio::test]
async fn a_file_without_tags_is_titled_from_its_name() {
    assert_eq!(
        title_from_file_name("99 Mind Hacks Mentale Tricks [16969919]"),
        "99 Mind Hacks Mentale Tricks"
    );
    assert_eq!(title_from_file_name("Plain Name"), "Plain Name");
    if !ffmpeg_required("a_file_without_tags_is_titled_from_its_name") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let file = make_tagged_mp3(
        dir.path(),
        "99 Mind Hacks Mentale Tricks [16969919].mp3",
        None,
        None,
        false,
    );

    let tags = read(&file).await.unwrap();
    assert_eq!(tags.title, "99 Mind Hacks Mentale Tricks");
    assert_eq!(tags.genre, None);
    assert!(tags.cover.is_none());
}

#[tokio::test]
async fn a_blank_genre_is_none() {
    if !ffmpeg_required("a_blank_genre_is_none") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let file = make_tagged_mp3(dir.path(), "book.mp3", Some("T"), Some("  "), false);

    assert_eq!(read(&file).await.unwrap().genre, None);
}

/// The longest real title must still fit the caption budget, or the set
/// could never be sent.
#[test]
fn the_longest_real_title_fits_a_caption() {
    let mut caption =
        crate::test_fakes::upload::sample_caption("01ARZ3NDEKTSV4RRFFQ69G5FAV", 9_000_000, 1);
    caption.t = mlib_spec::Kind::Book;
    caption.ids.tmdb = None;
    caption.q = None;
    caption.hdr = None;
    caption.vcodec = None;
    caption.title = Some(
        "99 Mind Hacks: Mentale Tricks für Klarheit im Kopf und Stärke im Alltag | Vom Bestsellerautor und Mentalisten Timon Krause | Psychologie-Hacks für mehr Erfolg im Leben"
            .to_string(),
    );
    mlib_spec::check_budget(&caption).unwrap();
}
