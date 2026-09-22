//! Where `prepare` writes. Both answers guard a rename over a file, which
//! cannot be taken back, so each case that decides where bytes land is
//! pinned here rather than left to the command.

use std::path::Path;

use mediagram::media::prepare::paths::{PREPARE_WORKING_SUFFIX, mirrored, working_path};

#[test]
fn a_file_under_the_root_keeps_its_place_in_the_tree() {
    let dest = mirrored(
        Path::new("/shows/Dark/S01/e1.mkv"),
        Path::new("/shows"),
        Path::new("/out"),
        false,
    )
    .unwrap();
    assert_eq!(dest, Path::new("/out/Dark/S01/e1.mkv"));
}

#[test]
fn a_file_named_directly_lands_at_the_top_of_the_output() {
    let file = Path::new("/shows/Dark/S01/e1.mkv");
    let dest = mirrored(file, file, Path::new("/out"), false).unwrap();
    assert_eq!(dest, Path::new("/out/e1.mkv"));
}

#[test]
fn converting_to_mp4_changes_the_extension() {
    let dest = mirrored(
        Path::new("/shows/e1.mkv"),
        Path::new("/shows"),
        Path::new("/out"),
        true,
    )
    .unwrap();
    assert_eq!(dest, Path::new("/out/e1.mp4"));
}

/// `--out` pointed at the folder being prepared would write each result over
/// its own source; that is refused rather than done.
#[test]
fn an_output_that_is_its_own_source_is_refused() {
    let refused = mirrored(
        Path::new("/shows/e1.mkv"),
        Path::new("/shows"),
        Path::new("/shows"),
        false,
    )
    .unwrap_err();
    assert!(refused.to_string().contains("over its own source"));
}

#[test]
fn the_working_file_sits_beside_its_destination() {
    let working = working_path(Path::new("/out/Dark/e1.mp4"));
    assert_eq!(working.parent(), Some(Path::new("/out/Dark")));
    assert_eq!(
        working.file_name().unwrap().to_string_lossy(),
        format!("e1{PREPARE_WORKING_SUFFIX}")
    );
}
