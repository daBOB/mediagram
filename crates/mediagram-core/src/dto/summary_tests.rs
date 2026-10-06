//! `tests/dto_mapping.rs` holds the episode numbers and poster keys through
//! the public surface; this holds the rest of the row's own facts, and what
//! is deliberately left for the listing to attach.

use super::*;

fn set() -> PlayableSet {
    PlayableSet {
        set_id: "01SET".into(),
        kind: "tut".into(),
        title: Some("Signal".into()),
        show: Some("Terra X".into()),
        chap: Some("Basics".into()),
        path: Some("basics/signal".into()),
        season: Some(1),
        episode: Some("[3,4]".into()),
        tmdb: None,
        year: Some(2024),
        container: "mp4".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        quality: Some("720p".into()),
        hdr: None,
        duration: Some(600),
        total: 123_456,
        part_count: 2,
        created_at: 1_781_568_000,
        alang: vec!["de".into()],
        slang: vec!["en".into(), "de".into()],
    }
}

/// Named as the web player names them, so two surfaces over one library
/// need no translation table: `added_at` is the row's `created_at`.
#[test]
fn a_summary_carries_the_rows_own_facts() {
    let summary = summary_from(&set());
    assert_eq!(summary.set_id, "01SET");
    assert_eq!(
        (summary.kind.as_str(), summary.show.as_deref()),
        ("tut", Some("Terra X"))
    );
    assert_eq!(summary.title.as_deref(), Some("Signal"));
    assert_eq!(
        (summary.chap.as_deref(), summary.path.as_deref()),
        (Some("Basics"), Some("basics/signal"))
    );
    assert_eq!(
        (summary.season, summary.episode_first, summary.episode_last),
        (Some(1), Some(3), Some(4))
    );
    assert_eq!((summary.year, summary.duration), (Some(2024), Some(600)));
    assert_eq!((summary.total, summary.part_count), (123_456, 2));
    assert_eq!(summary.added_at, 1_781_568_000);
    assert_eq!(summary.alang, ["de"]);
    assert_eq!(summary.slang, ["en", "de"]);
    assert_eq!(summary.poster_key.as_deref(), Some("title-terra-x"));
}

/// Resolving a key to a file, and attaching what the index keeps per title,
/// is the listing's work: done here, every flattened set would cost a
/// filesystem check even for a caller that never enriches it.
#[test]
fn what_the_listing_attaches_starts_empty_even_with_a_poster_key() {
    let summary = summary_from(&set());
    assert!(summary.poster_key.is_some());
    assert_eq!(summary.poster_path, None);
    assert_eq!(
        (summary.backdrop_path, summary.season_poster_path),
        (None, None)
    );
    assert_eq!(
        (summary.fsk, summary.tagline, summary.category),
        (None, None, None)
    );
    assert_eq!((summary.rating, summary.popularity), (None, None));
    assert!(summary.genres.is_empty() && summary.subtitles.is_empty());
    assert!(!summary.has_summary && !summary.anime);
}
