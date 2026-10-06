use super::*;
use crate::{Episode, Part, ProviderIds};

/// A caption of `kind` with every optional label absent, so each test sets
/// only the fields its label is built from.
fn caption(kind: Kind) -> Caption {
    Caption {
        t: kind,
        ids: ProviderIds {
            tmdb: None,
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: None,
        chap: None,
        path: None,
        title: None,
        year: None,
        s: None,
        e: None,
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01JQ8F2K9M4XZ".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 1,
            sha256: "00".into(),
        },
        total: 1,
    }
}

fn movie(title: Option<&str>, year: Option<u16>) -> Caption {
    Caption {
        title: title.map(Into::into),
        year,
        ..caption(Kind::Movie)
    }
}

fn numbered(kind: Kind, show: Option<&str>, s: Option<u32>, e: Option<Episode>) -> Caption {
    Caption {
        show: show.map(Into::into),
        s,
        e,
        ..caption(kind)
    }
}

#[test]
fn a_movie_reads_as_its_title_and_year() {
    assert_eq!(
        movie(Some("Dune: Part Two"), Some(2024)).display_name(),
        "Dune: Part Two (2024)"
    );
    assert_eq!(movie(Some("Dune"), None).display_name(), "Dune");
}

/// The set id is the one label every caption is guaranteed to carry, so a
/// movie with no title still gets a name, and a bare year is not one.
#[test]
fn a_movie_without_a_title_falls_back_to_its_set_id() {
    assert_eq!(movie(None, Some(2024)).display_name(), "01JQ8F2K9M4XZ");
    assert_eq!(movie(None, None).display_name(), "01JQ8F2K9M4XZ");
}

#[test]
fn an_episode_reads_as_its_show_and_episode_code() {
    let single = numbered(
        Kind::Ep,
        Some("Severance"),
        Some(2),
        Some(Episode::Single(1)),
    );
    assert_eq!(single.display_name(), "Severance S02E01");
    let double = numbered(
        Kind::Ep,
        Some("Severance"),
        Some(1),
        Some(Episode::Range([1, 2])),
    );
    assert_eq!(double.display_name(), "Severance S01E01-E02");
}

/// Anime is often numbered only absolutely, with or without a season.
#[test]
fn an_episode_numbered_only_absolutely_reads_as_its_absolute_number() {
    let ep = Caption {
        abs: Some(7),
        ..numbered(Kind::Ep, Some("One Piece"), None, None)
    };
    assert_eq!(ep.display_name(), "One Piece #007");
    let half_numbered = Caption {
        abs: Some(1071),
        ..numbered(Kind::Ep, Some("One Piece"), Some(21), None)
    };
    assert_eq!(half_numbered.display_name(), "One Piece #1071");
}

#[test]
fn an_episodes_season_and_number_win_over_its_absolute_number() {
    let ep = Caption {
        abs: Some(1071),
        ..numbered(
            Kind::Ep,
            Some("One Piece"),
            Some(21),
            Some(Episode::Single(80)),
        )
    };
    assert_eq!(ep.display_name(), "One Piece S21E80");
}

#[test]
fn an_episode_with_no_usable_number_reads_as_its_show() {
    let ep = numbered(Kind::Ep, Some("Severance"), None, Some(Episode::Single(3)));
    assert_eq!(ep.display_name(), "Severance");
}

#[test]
fn an_episode_without_a_show_says_so() {
    let ep = numbered(Kind::Ep, None, Some(2), Some(Episode::Single(1)));
    assert_eq!(ep.display_name(), "? S02E01");
}

#[test]
fn a_lesson_reads_as_its_course_and_chapter_lesson_code() {
    let lesson = numbered(
        Kind::Tut,
        Some("Rust Course"),
        Some(2),
        Some(Episode::Single(3)),
    );
    assert_eq!(lesson.display_name(), "Rust Course C02L03");
    let spanning = numbered(
        Kind::Tut,
        Some("Rust Course"),
        Some(2),
        Some(Episode::Range([3, 4])),
    );
    assert_eq!(
        spanning.display_name(),
        "Rust Course C02L03",
        "the first lesson leads"
    );
}

#[test]
fn a_lesson_missing_its_chapter_or_number_reads_as_its_course() {
    let no_chapter = numbered(
        Kind::Tut,
        Some("Rust Course"),
        None,
        Some(Episode::Single(3)),
    );
    assert_eq!(no_chapter.display_name(), "Rust Course");
    let no_lesson = numbered(Kind::Tut, Some("Rust Course"), Some(2), None);
    assert_eq!(no_lesson.display_name(), "Rust Course");
    assert_eq!(numbered(Kind::Tut, None, None, None).display_name(), "?");
}

/// A document is numbered the way a lesson is; only the `D` tells the two
/// apart on the row they share.
#[test]
fn a_document_reads_as_its_course_and_chapter_document_code() {
    let doc = numbered(
        Kind::Doc,
        Some("Rust Course"),
        Some(2),
        Some(Episode::Single(3)),
    );
    assert_eq!(doc.display_name(), "Rust Course C02D03");
    let unnumbered = numbered(Kind::Doc, Some("Rust Course"), Some(2), None);
    assert_eq!(unnumbered.display_name(), "Rust Course");
    assert_eq!(numbered(Kind::Doc, None, None, None).display_name(), "?");
}

#[test]
fn a_documentary_in_a_collection_reads_as_the_collection_and_its_code() {
    let episode = numbered(
        Kind::Docu,
        Some("Terra X"),
        Some(1),
        Some(Episode::Single(5)),
    );
    assert_eq!(episode.display_name(), "Terra X C01E05");
    let unnumbered = numbered(Kind::Docu, Some("Terra X"), None, None);
    assert_eq!(unnumbered.display_name(), "Terra X");
}

/// A standalone documentary has no collection, so it is labelled exactly
/// as a movie is — title and year, or the set id when there is no title.
#[test]
fn a_standalone_documentary_reads_like_a_movie() {
    let titled = Caption {
        title: Some("Planet Erde".into()),
        year: Some(2006),
        ..caption(Kind::Docu)
    };
    assert_eq!(titled.display_name(), "Planet Erde (2006)");
    let undated = Caption {
        title: Some("Planet Erde".into()),
        ..caption(Kind::Docu)
    };
    assert_eq!(undated.display_name(), "Planet Erde");
    assert_eq!(caption(Kind::Docu).display_name(), "01JQ8F2K9M4XZ");
}

/// Once a documentary belongs to a collection it is labelled as one of its
/// episodes; its own title and year no longer enter into it.
#[test]
fn a_documentarys_collection_outranks_its_own_title() {
    let docu = Caption {
        title: Some("Die Pyramiden".into()),
        year: Some(2020),
        ..numbered(
            Kind::Docu,
            Some("Terra X"),
            Some(1),
            Some(Episode::Single(5)),
        )
    };
    assert_eq!(docu.display_name(), "Terra X C01E05");
}
