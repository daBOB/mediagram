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
    let crates = Path::new(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .expect("the crates directory");
    let mut files = Vec::new();
    for member in std::fs::read_dir(crates).expect("the crates directory") {
        let src = member.expect("a crate").path().join("src");
        if src.is_dir() {
            sources(&src, &mut files);
        }
    }
    assert!(
        !files.is_empty(),
        "found no source files under {}",
        crates.display()
    );

    let mut over: Vec<String> = files
        .iter()
        .filter_map(|file| {
            let lines = std::fs::read_to_string(file)
                .expect("a readable file")
                .lines()
                .count();
            (lines > LIMIT)
                .then(|| format!("{} ({lines})", file.strip_prefix(crates).unwrap().display()))
        })
        .collect();
    over.sort();
    assert!(
        over.is_empty(),
        "over {LIMIT} lines — split out a focused submodule: {over:#?}"
    );
}

/// A bare `rusqlite` open initializes SQLite before libsql can configure it,
/// and a Telegram session opened afterwards aborts the process — on whichever
/// thread gets there second, so tests only catch it by timing. Every open in
/// this crate therefore goes through `index::sqlite_init`, and this keeps it so.
#[test]
fn every_sqlite_open_in_the_uploader_configures_sqlite_first() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR"));
    let mut files = Vec::new();
    for dir in ["src", "tests"] {
        let mut all = Vec::new();
        collect_rs(&root.join(dir), &mut all);
        files.extend(all);
    }
    let bare: Vec<String> = files
        .iter()
        .filter(|file| {
            !file.ends_with("index/sqlite_init.rs") && !file.ends_with("code_standards.rs")
        })
        .filter(|file| {
            let text = std::fs::read_to_string(file).expect("a readable file");
            text.contains("Connection::open(") || text.contains("Connection::open_in_memory(")
        })
        .map(|file| file.display().to_string())
        .collect();
    assert!(
        bare.is_empty(),
        "open SQLite through index::sqlite_init::open instead:\n{}",
        bare.join("\n")
    );
}

fn collect_rs(dir: &Path, out: &mut Vec<PathBuf>) {
    for entry in std::fs::read_dir(dir).expect("a readable directory") {
        let path = entry.expect("a directory entry").path();
        if path.is_dir() {
            collect_rs(&path, out);
        } else if path.extension().is_some_and(|ext| ext == "rs") {
            out.push(path);
        }
    }
}

/// Let chains (`if let … && let …`) are stable only from Rust 1.88. The
/// workspace's `rust-version` is what a reader is promised builds it, and
/// what resolver 3 picks dependency versions against, so it must not name a
/// toolchain that cannot compile the code.
#[test]
fn the_declared_rust_version_compiles_let_chains() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let manifest: toml::Table = std::fs::read_to_string(root.join("Cargo.toml"))
        .expect("the workspace manifest")
        .parse()
        .expect("a valid workspace manifest");
    let declared = manifest["workspace"]["package"]["rust-version"]
        .as_str()
        .expect("a workspace rust-version");
    let version: Vec<u32> = declared
        .split('.')
        .map(|n| n.parse().expect("a numeric rust-version"))
        .collect();

    let mut files = Vec::new();
    for member in std::fs::read_dir(root.join("crates")).expect("the crates directory") {
        let src = member.expect("a crate").path().join("src");
        if src.is_dir() {
            collect_rs(&src, &mut files);
        }
    }
    let chained = files.iter().any(|file| {
        let text = std::fs::read_to_string(file).expect("a readable file");
        text.contains("&& let ")
    });
    assert!(
        !chained || version[..2] >= [1, 88][..],
        "the code uses let chains, which need Rust 1.88, but rust-version is {declared}"
    );
}
