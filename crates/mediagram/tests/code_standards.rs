//! `docs/code-standards.md` says every source file stays under 200 lines.
//! This keeps that sentence true: a rule the docs call binding and the code
//! ignores is worse than no rule. Unit tests kept beside a module
//! (`<module>_tests.rs`, or `tests.rs` beside a `mod.rs`) are test files and
//! exempt, like everything under `tests/`.

use std::path::{Path, PathBuf};

const LIMIT: usize = 200;

fn sources(dir: &Path, out: &mut Vec<PathBuf>) {
    for entry in std::fs::read_dir(dir).expect("a readable source directory") {
        let path = entry.expect("a directory entry").path();
        if path.is_dir() {
            sources(&path, out);
        } else if path.extension().is_some_and(|ext| ext == "rs") {
            let name = path.file_name().unwrap().to_string_lossy();
            if !name.ends_with("_tests.rs") && name != "tests.rs" {
                out.push(path);
            }
        }
    }
}

#[test]
fn every_source_file_stays_under_the_line_limit() {
    let crates = Path::new(env!("CARGO_MANIFEST_DIR")).parent().expect("the crates directory");
    let mut files = Vec::new();
    for member in std::fs::read_dir(crates).expect("the crates directory") {
        let src = member.expect("a crate").path().join("src");
        if src.is_dir() {
            sources(&src, &mut files);
        }
    }
    assert!(!files.is_empty(), "found no source files under {}", crates.display());

    let mut over: Vec<String> = files
        .iter()
        .filter_map(|file| {
            let lines = std::fs::read_to_string(file).expect("a readable file").lines().count();
            (lines > LIMIT).then(|| format!("{} ({lines})", file.strip_prefix(crates).unwrap().display()))
        })
        .collect();
    over.sort();
    assert!(
        over.is_empty(),
        "over {LIMIT} lines — split out a focused submodule: {over:#?}"
    );
}
