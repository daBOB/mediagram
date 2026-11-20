use super::*;

#[test]
fn a_single_file_that_is_not_an_mp3_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    let film = dir.path().join("film.mkv");
    std::fs::write(&film, b"x").unwrap();

    let err = mp3_files(&film).unwrap_err();

    assert!(err.to_string().contains("not an .mp3"), "{err:#}");
}

#[test]
fn a_folder_yields_its_mp3s_sorted_whatever_the_case() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("sub")).unwrap();
    for name in ["b.mp3", "sub/a.MP3", "cover.jpg"] {
        std::fs::write(dir.path().join(name), b"x").unwrap();
    }

    let found = mp3_files(dir.path()).unwrap();

    assert_eq!(
        found,
        [dir.path().join("b.mp3"), dir.path().join("sub/a.MP3")]
    );
}
