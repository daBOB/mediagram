//! A course document on the wire.
//!
//! A document reuses every field a lesson uses — `cid`, `show`, `chap`,
//! `path`, `s`, `e`, `title` — and leaves empty the ones that only describe
//! moving pictures. What tells the two apart is `t`, and nothing else: a
//! reader that goes by container or by a missing duration would have to be
//! taught every format anyone ever drops into a course folder.

use mlib_spec::caption::{Caption, Episode, Kind, Part, document_code};
use mlib_spec::caption_codec::{MARKER, parse, to_text};
use mlib_spec::ids::ProviderIds;
use mlib_spec::part_name::base_name;

fn handout() -> Caption {
    Caption {
        t: Kind::Doc,
        ids: ProviderIds::default(),
        cid: Some("rust-course-2024".into()),
        show: Some("Rust Course".into()),
        chap: Some("Ownership".into()),
        path: Some("Rust Course/2. Ownership".into()),
        title: Some("Borrowing".into()),
        year: None,
        s: Some(2),
        e: Some(Episode::Single(3)),
        abs: None,
        q: None,
        hdr: None,
        container: "pdf".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01JQ8F2K9M4XZ00000000003".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 2_412_336,
            sha256: "b".repeat(64),
        },
        total: 2_412_336,
    }
}

#[test]
fn a_document_round_trips_through_the_wire_format() {
    let text = to_text(&handout(), "").unwrap();
    assert!(text.starts_with(MARKER), "{text}");
    assert_eq!(parse(&text).unwrap(), handout());
}

#[test]
fn the_kind_serializes_as_doc() {
    assert!(to_text(&handout(), "").unwrap().contains("\"t\":\"doc\""));
}

/// The one field that says "do not try to play this". Everything else a
/// document carries is shared with a lesson, so a reader that guessed from
/// the container would have to know every format instead of one flag.
#[test]
fn nothing_but_the_kind_marks_it_unplayable() {
    let doc = handout();
    assert_eq!(doc.dur, None);
    assert_eq!(doc.vcodec, None);
    assert_eq!(doc.acodec, None);
    assert_eq!(doc.q, None);
    assert_eq!(doc.hdr, None);
    assert!(doc.alang.is_empty() && doc.slang.is_empty());
}

/// `D` where a lesson has `L`, so a Telegram document list reads as what it
/// holds without anyone opening a file.
#[test]
fn the_code_says_document_where_a_lesson_says_lesson() {
    assert_eq!(document_code(2, Episode::Single(3)), "C02D03");
    assert_eq!(handout().display_name(), "Rust Course C02D03");
    assert_eq!(base_name(&handout()), "Rust Course - c02d03 - Borrowing");
}

/// A document is numbered inside its chapter the way a lesson is, which is
/// what puts a handout on the row beside the lesson it belongs to without
/// anything having to pair the two.
#[test]
fn a_handout_carries_its_lessons_number() {
    let mut beside = handout();
    beside.e = Some(Episode::Single(3));
    let text = to_text(&beside, "").unwrap();
    assert_eq!(parse(&text).unwrap().e, Some(Episode::Single(3)));
}

/// A document sitting in a folder that holds no video at all: it has a path
/// and a chapter number like anything else, and no lesson to sit beside.
#[test]
fn a_workbook_in_a_videoless_folder_is_an_ordinary_document() {
    let mut workbook = handout();
    workbook.chap = Some("Ressourcen".into());
    workbook.path = Some("Rust Course/Ressourcen".into());
    workbook.title = Some("Arbeitsbuch".into());
    workbook.s = Some(9);
    workbook.e = Some(Episode::Single(1));
    let text = to_text(&workbook, "").unwrap();
    assert_eq!(parse(&text).unwrap(), workbook);
}
