use std::path::{Path, PathBuf};

use super::*;
use crate::test_fakes::session::config_in;

fn args(path: &Path, dry_run: bool, category: Option<&str>) -> AddDocuArgs {
    AddDocuArgs {
        path: path.to_path_buf(),
        title: None,
        cid: None,
        dry_run,
        no_push: false,
        variant: None,
        no_remux: false,
        category: category.map(str::to_string),
    }
}

/// A collection folder of three episodes, two at its root.
fn terra_x(root: &Path) -> PathBuf {
    let folder = root.join("Terra X");
    std::fs::create_dir_all(folder.join("Ozeane")).unwrap();
    for file in ["01 Wale.mp4", "02 Haie.mp4", "Ozeane/01 Robben.mp4"] {
        std::fs::write(folder.join(file), b"not really a video").unwrap();
    }
    folder
}

fn index_exists(data_dir: &Path) -> bool {
    data_dir.join(mlib_spec::schema::INDEX_FILE).exists()
}

/// The dry-run table is `add-course`'s own; reworded, it must not say
/// "lesson" or "course" anywhere a collection's episodes are counted.
#[test]
fn the_course_table_is_reworded_for_a_collection() {
    let dir = tempfile::tempdir().unwrap();
    let folder = terra_x(dir.path());
    let walked = walk_course(&folder).unwrap();

    let lines: Vec<String> = dry_run_table("Terra X", "terra-x", &walked)
        .iter()
        .map(|line| in_docu_words(line))
        .collect();

    assert!(
        lines.contains(&"(collection root)  (2 episode(s))".to_string()),
        "{lines:#?}"
    );
    assert!(
        lines.contains(&"Ozeane  (1 episode(s))".to_string()),
        "{lines:#?}"
    );
    assert_eq!(lines.last().unwrap(), "3 episode(s) across 2 folder(s)");
    assert!(
        lines
            .iter()
            .all(|l| !l.contains("lesson") && !l.contains("course root")),
        "{lines:#?}"
    );
}

/// A bad `--category` fails before anything is walked or opened, the same
/// way with `--dry-run` as without.
#[tokio::test]
async fn a_bad_category_fails_first_with_or_without_a_dry_run() {
    let dir = tempfile::tempdir().unwrap();
    let data_dir = dir.path().join("data");
    let cfg = config_in(&data_dir);
    let folder = dir.path().join("Empty");
    std::fs::create_dir(&folder).unwrap();

    for dry_run in [true, false] {
        let err = run(&cfg, args(&folder, dry_run, Some("Other")))
            .await
            .unwrap_err();
        assert!(err.to_string().contains("under Other"), "{err}");
    }
    assert!(!index_exists(&data_dir));
}

/// Nothing to upload is not an error, and touches neither the index nor
/// the channel.
#[tokio::test]
async fn an_empty_collection_uploads_nothing_and_opens_no_index() {
    let dir = tempfile::tempdir().unwrap();
    let data_dir = dir.path().join("data");
    let cfg = config_in(&data_dir);
    let folder = dir.path().join("Empty");
    std::fs::create_dir(&folder).unwrap();

    run(&cfg, args(&folder, false, None)).await.unwrap();

    assert!(!index_exists(&data_dir));
}

/// A dry run only reports: no index is opened, so no artwork or category
/// is written either.
#[tokio::test]
async fn a_dry_run_writes_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let data_dir = dir.path().join("data");
    let cfg = config_in(&data_dir);
    let folder = terra_x(dir.path());
    std::fs::write(folder.join("poster.jpg"), b"art").unwrap();

    run(&cfg, args(&folder, true, Some("Natur"))).await.unwrap();

    assert!(!index_exists(&data_dir));
}
