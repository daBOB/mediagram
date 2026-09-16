//! Where a lesson sits inside its course.
//!
//! A real course is not two levels deep. The one this was built for nests
//! unevenly from one to four folders below the root, and one folder holds
//! twenty videos *beside* a subfolder. Chapter-and-lesson cannot describe
//! that, so a lesson carries the path of the folder it came from and the
//! player rebuilds the tree by splitting it.
//!
//! The separator is `/` regardless of the uploading platform: this is a path
//! within a course, not a path on a disk.

use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::caption_codec::{parse, to_text};
use mlib_spec::ids::ProviderIds;

fn lesson(path: Option<&str>) -> Caption {
    Caption {
        t: Kind::Tut,
        ids: ProviderIds {
            tmdb: None,
            tvdb: None,
            imdb: None,
        },
        cid: Some("geldhochschule".into()),
        show: Some("Geldhochschule".into()),
        chap: Some("1. Trading".into()),
        path: path.map(str::to_string),
        title: Some("Einführung".into()),
        year: None,
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mp4".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["deu".into()],
        slang: vec![],
        dur: Some(120),
        variant: None,
        set: "01SET0000000000000000001".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "a".repeat(64),
        },
        total: 100,
    }
}

#[test]
fn a_lesson_carries_the_folders_it_came_from() {
    let caption = lesson(Some("Ausbildung Trading/1. Grundlagen/1. Trading"));
    let parsed = parse(&to_text(&caption, "").unwrap()).unwrap();

    assert_eq!(
        parsed.path.as_deref(),
        Some("Ausbildung Trading/1. Grundlagen/1. Trading")
    );
}

/// Depth is not fixed: the same course has lessons one folder down and four.
#[test]
fn any_depth_round_trips() {
    for path in [
        "Basislektionen",
        "Basislektionen/1. Start",
        "Ausbildung Trading/1. Grundlagen/1. Trading",
        "a/b/c/d/e",
    ] {
        let parsed = parse(&to_text(&lesson(Some(path)), "").unwrap()).unwrap();
        assert_eq!(parsed.path.as_deref(), Some(path), "path {path}");
    }
}

/// A film has no folders, and neither does a course whose lessons sit at the
/// root. Absent is a legitimate answer, not a defect.
#[test]
fn a_set_with_no_path_is_still_valid() {
    let parsed = parse(&to_text(&lesson(None), "").unwrap()).unwrap();
    assert_eq!(parsed.path, None);
}

/// Captions written before this field existed must keep parsing: the channel
/// is the archive, and it is not rewritten.
#[test]
fn a_v3_caption_still_parses_and_has_no_path() {
    let v3 = r#"#mlib v=3
{"t":"tut","ids":{},"cid":"geldhochschule","show":"Geldhochschule","chap":"1. Trading","title":"Einführung","year":null,"s":1,"e":1,"abs":null,"q":null,"hdr":null,"container":"mp4","vcodec":"h264","acodec":"aac","alang":["deu"],"slang":[],"dur":120,"variant":null,"set":"01SET0000000000000000001","part":{"i":0,"n":1,"off":0,"len":100,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},"total":100}"#;

    let parsed = parse(v3).expect("a v3 caption is still readable");
    assert_eq!(parsed.path, None);
    assert_eq!(parsed.chap.as_deref(), Some("1. Trading"));
}

#[test]
fn the_marker_names_version_four() {
    assert!(
        to_text(&lesson(None), "")
            .unwrap()
            .starts_with("#mlib v=4\n")
    );
}

/// The path comes from a directory walk, so it is the one caption field an
/// attacker could aim at a filesystem. It is a label, never a path to open,
/// but a reader that forgets that should not be handed `../../etc`.
#[test]
fn a_path_that_climbs_out_of_the_course_is_refused() {
    for hostile in ["../secrets", "a/../../b", "/etc/passwd", "a//b", "a/./b"] {
        let caption = lesson(Some(hostile));
        let wire = to_text(&caption, "");
        let refused = match wire {
            Err(_) => true,
            Ok(text) => parse(&text).is_err(),
        };
        assert!(refused, "path {hostile:?} should be refused");
    }
}

#[test]
fn an_absurdly_long_path_is_refused() {
    let deep = (0..100)
        .map(|i| format!("folder{i}"))
        .collect::<Vec<_>>()
        .join("/");
    let caption = lesson(Some(&deep));
    let refused = match to_text(&caption, "") {
        Err(_) => true,
        Ok(text) => parse(&text).is_err(),
    };
    assert!(refused, "a path of {} bytes should be refused", deep.len());
}
