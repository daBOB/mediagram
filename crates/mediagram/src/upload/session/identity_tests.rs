use std::path::PathBuf;

use super::*;
use crate::index::db;
use crate::index::set_row::SetRow;
use crate::test_fakes::upload::sample_caption;
use crate::upload::record_document::Document;
use mlib_spec::caption::{Caption, Episode, Kind};

fn record(conn: &Connection, caption: &Caption, status: SetStatus) {
    let mut row = SetRow::from_caption(caption, 1_700_000_000);
    row.status = status;
    sets::insert_set(conn, &row).unwrap();
}

/// Entry `number` of chapter `chapter` in collection `cid`, recorded as `kind`.
fn numbered(set_id: &str, kind: Kind, cid: &str, chapter: u32, number: u32) -> Caption {
    let mut caption = sample_caption(set_id, 10, 1);
    caption.t = kind;
    caption.ids.tmdb = None;
    caption.cid = Some(cid.to_string());
    caption.s = Some(chapter);
    caption.e = Some(Episode::Single(number));
    caption
}

fn episode(set_id: &str, tmdb: u64, season: u32, number: u32) -> Caption {
    let mut caption = sample_caption(set_id, 10, 1);
    caption.t = Kind::Ep;
    caption.ids.tmdb = Some(tmdb);
    caption.s = Some(season);
    caption.e = Some(Episode::Single(number));
    caption
}

fn lesson(cid: &str, chapter: Option<u32>, number: Option<u32>, kind: Kind) -> NewSet {
    NewSet {
        file: PathBuf::from("lesson.mp4"),
        lesson: Some(LessonOf {
            course: "Course".to_string(),
            cid: cid.to_string(),
            chapter,
            chapter_title: None,
            path: None,
            number,
            kind,
        }),
        ..NewSet::default()
    }
}

fn document(cid: &str, chapter: u32, number: u32) -> Set {
    Set::Document(Document {
        file: PathBuf::from("handout.pdf"),
        course: "Course".to_string(),
        cid: cid.to_string(),
        chapter,
        chapter_title: None,
        path: None,
        number,
        title: None,
        variant: None,
    })
}

#[test]
fn a_planned_set_is_found_by_its_id() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    record(&conn, &sample_caption("p1", 10, 1), SetStatus::Complete);

    let found = status(&conn, &Set::Planned("p1".to_string())).unwrap();
    let missing = status(&conn, &Set::Planned("p2".to_string())).unwrap();

    assert_eq!(found, Some(SetStatus::Complete));
    assert_eq!(missing, None);
}

/// A handout carries its lesson's number on purpose; only its kind keeps
/// it from being taken for the lesson beside it.
#[test]
fn a_document_is_never_found_as_the_lesson_it_sits_beside() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let handout = document("rust", 1, 3);
    record(
        &conn,
        &numbered("l1", Kind::Tut, "rust", 1, 3),
        SetStatus::Complete,
    );
    assert_eq!(status(&conn, &handout).unwrap(), None);

    record(
        &conn,
        &numbered("d1", Kind::Doc, "rust", 1, 3),
        SetStatus::Pending,
    );
    assert_eq!(status(&conn, &handout).unwrap(), Some(SetStatus::Pending));
}

/// A documentary collection's episode is looked up under the kind it is
/// recorded as. Looked up as a course lesson, it would never be found, and
/// every re-run would upload it again.
#[test]
fn a_collection_episode_is_found_under_the_kind_it_was_recorded_as() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    record(
        &conn,
        &numbered("x1", Kind::Docu, "terra-x", 1, 2),
        SetStatus::Complete,
    );

    let as_docu = lesson("terra-x", Some(1), Some(2), Kind::Docu);
    let as_tut = lesson("terra-x", Some(1), Some(2), Kind::Tut);

    assert_eq!(
        status(&conn, &Set::File(as_docu)).unwrap(),
        Some(SetStatus::Complete)
    );
    assert_eq!(status(&conn, &Set::File(as_tut)).unwrap(), None);
}

#[test]
fn an_episode_is_found_by_show_and_numbers() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    record(&conn, &episode("e1", 1399, 1, 2), SetStatus::Pending);
    let new = NewSet {
        file: PathBuf::from("anywhere/else.mkv"),
        tmdb: Some(1399),
        season: Some(1),
        episode: Some(2),
        ..NewSet::default()
    };

    assert_eq!(
        status(&conn, &Set::File(new)).unwrap(),
        Some(SetStatus::Pending)
    );
}

/// Without a whole identity there is nothing to look up: a movie, an
/// episode missing a number, a lesson missing one — even a lesson that
/// happens to carry an episode's numbers is not looked up as that episode.
#[test]
fn a_file_without_a_whole_identity_is_always_planned_anew() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    record(&conn, &sample_caption("m1", 10, 1), SetStatus::Complete);
    record(&conn, &episode("e1", 1399, 1, 2), SetStatus::Complete);
    record(
        &conn,
        &numbered("l1", Kind::Tut, "rust", 1, 3),
        SetStatus::Complete,
    );

    let movie = NewSet {
        tmdb: Some(603),
        ..NewSet::default()
    };
    let unnumbered_episode = NewSet {
        tmdb: Some(1399),
        season: Some(1),
        ..NewSet::default()
    };
    let unnumbered_lesson = lesson("rust", Some(1), None, Kind::Tut);
    let lesson_with_episode_numbers = NewSet {
        tmdb: Some(1399),
        season: Some(1),
        episode: Some(2),
        ..lesson("rust", None, Some(3), Kind::Tut)
    };

    for new in [
        movie,
        unnumbered_episode,
        unnumbered_lesson,
        lesson_with_episode_numbers,
    ] {
        assert_eq!(status(&conn, &Set::File(new)).unwrap(), None);
    }
}
