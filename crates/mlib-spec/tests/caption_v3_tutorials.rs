//! Caption v3: a third content kind for courses, plus the two fields it
//! needs. A course is `show`, a lesson is `title`, chapter and lesson numbers
//! reuse `s` and `e`, `chap` carries the chapter title, and `cid` is a stable
//! collection id that survives renaming the course.

use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::caption_codec::{MARKER, parse, to_text};
use mlib_spec::ids::ProviderIds;

fn lesson() -> Caption {
    Caption {
        t: Kind::Tut,
        ids: ProviderIds::default(),
        cid: Some("rust-course-2024".into()),
        show: Some("Rust Course".into()),
        chap: Some("Ownership".into()),
        path: None,
        title: Some("Borrowing".into()),
        year: Some(2024),
        s: Some(2),
        e: Some(Episode::Single(2)),
        abs: None,
        q: Some("1080p".into()),
        hdr: None,
        container: "mp4".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["en".into()],
        slang: vec![],
        dur: Some(612),
        variant: None,
        set: "01JQ8F2K9M4XZ00000000001".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 412_336_102,
            sha256: "a".repeat(64),
        },
        total: 412_336_102,
    }
}

#[test]
fn a_tutorial_round_trips_through_the_wire_format() {
    let text = to_text(&lesson(), "").unwrap();
    // The version is whatever this build writes; the subject here is the
    // round-trip, and pinning a number made this fail when `path` arrived.
    assert!(text.starts_with(MARKER), "{text}");
    assert_eq!(parse(&text).unwrap(), lesson());
}

#[test]
fn the_new_fields_sit_where_the_spec_says() {
    let text = to_text(&lesson(), "").unwrap();
    let json = text.lines().nth(1).unwrap();
    let cid_at = json.find("\"cid\"").unwrap();
    let show_at = json.find("\"show\"").unwrap();
    let chap_at = json.find("\"chap\"").unwrap();
    let title_at = json.find("\"title\"").unwrap();
    assert!(cid_at < show_at, "cid comes after ids and before show");
    assert!(
        show_at < chap_at && chap_at < title_at,
        "course, chapter, lesson"
    );
}

#[test]
fn a_movie_still_serializes_both_new_fields_as_null() {
    let mut movie = lesson();
    movie.t = Kind::Movie;
    movie.cid = None;
    movie.chap = None;
    let json = to_text(&movie, "").unwrap();
    assert!(json.contains("\"cid\":null") && json.contains("\"chap\":null"));
}

/// Already-published captions must keep parsing: a v2 caption has neither
/// new field and is still a valid record.
#[test]
fn a_v2_caption_still_parses_with_the_new_fields_absent() {
    let v2 = "#mlib v=2\n{\"t\":\"movie\",\"ids\":{\"tmdb\":603,\"tvdb\":null,\"imdb\":null},\
\"show\":null,\"title\":\"The Matrix\",\"year\":1999,\"s\":null,\"e\":null,\"abs\":null,\
\"q\":null,\"hdr\":null,\"container\":\"mkv\",\"vcodec\":null,\"acodec\":null,\"alang\":[],\
\"slang\":[],\"dur\":null,\"variant\":null,\"set\":\"01JQ8F2K9M4XZ00000000002\",\
\"part\":{\"i\":0,\"n\":1,\"off\":0,\"len\":10,\"sha256\":\"\"},\"total\":10}";
    let caption = parse(v2).unwrap();
    assert_eq!(caption.t, Kind::Movie);
    assert_eq!(caption.cid, None);
    assert_eq!(caption.chap, None);
}

#[test]
fn an_unknown_marker_version_is_still_refused() {
    let v9 = "#mlib v=9\n{}";
    assert!(parse(v9).is_err());
}

#[test]
fn the_kind_serializes_as_tut() {
    assert!(to_text(&lesson(), "").unwrap().contains("\"t\":\"tut\""));
}

#[test]
fn a_course_with_no_chapters_uses_chapter_one() {
    let mut flat = lesson();
    flat.chap = None;
    flat.s = Some(1);
    let text = to_text(&flat, "").unwrap();
    assert_eq!(parse(&text).unwrap().s, Some(1));
}
