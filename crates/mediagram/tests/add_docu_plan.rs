//! What `add-docu` plans, for a standalone file and for a collection folder,
//! without touching the network: the same walk, identity and artwork
//! machinery `add-course` uses, fed a documentary instead of a lesson.

use mediagram::course::identity::{collection_id, course_title};
use mediagram::course::walk::walk_course;
use mediagram::index::{artwork, db};
use mediagram::metadata::resolve::{ResolveInput, docu_file, lesson};
use mlib_spec::Kind;

/// A standalone documentary is titled from its file name, dropping a leading
/// number the way a lesson's would, and carries no course or numbering.
#[test]
fn a_standalone_file_is_titled_from_its_name_with_no_course() {
    let input = ResolveInput {
        file_name: "02 Deep Ocean.mp4".to_string(),
        ..ResolveInput::default()
    };
    let resolved = docu_file(None, &input);
    assert_eq!(resolved.kind, Kind::Docu);
    assert_eq!(resolved.title.as_deref(), Some("Deep Ocean"));
    assert_eq!(resolved.show, None);
    assert_eq!(resolved.season, None);
    assert_eq!(resolved.episode, None);
}

/// `--title` overrides whatever the file name would otherwise give.
#[test]
fn a_title_override_wins_over_the_file_name() {
    let input = ResolveInput {
        file_name: "raw_export_04.mp4".to_string(),
        ..ResolveInput::default()
    };
    let resolved = docu_file(Some("Great Barrier Reef"), &input);
    assert_eq!(resolved.title.as_deref(), Some("Great Barrier Reef"));
}

/// A documentary filed inside a collection folder is grouped and numbered
/// exactly as a course lesson is; only the kind differs.
#[test]
fn a_collection_episode_is_grouped_like_a_lesson_but_kinded_docu() {
    let input = ResolveInput {
        file_name: "01 Great Barrier Reef.mp4".to_string(),
        season: Some(1),
        episode: Some(3),
        ..ResolveInput::default()
    };
    let resolved = lesson(Kind::Docu, "Terra X", &input);
    assert_eq!(resolved.kind, Kind::Docu);
    assert_eq!(resolved.show.as_deref(), Some("Terra X"));
    assert_eq!(resolved.title.as_deref(), Some("Great Barrier Reef"));
    assert_eq!(resolved.season, Some(1));
}

/// The full folder plan: title, collection id and artwork key an
/// `add-docu <dir>` run would produce, plus the walk it uploads from.
#[test]
fn a_collection_folder_plans_a_title_cid_and_two_episodes() {
    let root = tempfile::tempdir().unwrap();
    let dir = root.path().join("Terra X");
    std::fs::create_dir_all(dir.join("Season 1")).unwrap();
    std::fs::write(dir.join("Season 1/01 Great Barrier Reef.mp4"), b"video").unwrap();
    std::fs::write(dir.join("Season 1/02 Amazon.mp4"), b"video").unwrap();
    std::fs::write(dir.join("poster.jpg"), b"poster bytes").unwrap();
    std::fs::write(dir.join("backdrop.png"), b"backdrop bytes").unwrap();

    let title = course_title(None, &dir).unwrap();
    assert_eq!(title, "Terra X");
    let cid = collection_id(&title, None).unwrap();
    assert_eq!(cid, "terra-x");

    let walked = walk_course(&dir).unwrap();
    assert_eq!(walked.lessons.len(), 2);
    assert!(walked.lessons.iter().all(|l| l.chapter == 1));
    let mut numbers: Vec<u32> = walked.lessons.iter().map(|l| l.lesson).collect();
    numbers.sort_unstable();
    assert_eq!(numbers, [1, 2]);

    let art_key = mlib_spec::package::title_art_key(&title).unwrap();
    assert_eq!(art_key, "title-terra-x");

    let index_dir = tempfile::tempdir().unwrap();
    let conn = db::open(index_dir.path()).unwrap();
    let stored = artwork::adopt_folder(&conn, &dir, &art_key).unwrap();
    assert_eq!(stored, 2);
    let (poster_mime, _) = artwork::get(&conn, "title-terra-x").unwrap().unwrap();
    assert_eq!(poster_mime, "image/jpeg");
    let (backdrop_mime, _) = artwork::get(&conn, "title-terra-x-bg").unwrap().unwrap();
    assert_eq!(backdrop_mime, "image/png");
}
