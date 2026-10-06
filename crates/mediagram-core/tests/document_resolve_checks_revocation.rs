//! A Telegram 401 means this device's login ended elsewhere: the core forgets
//! the key and answers `NotAuthorized`, so the app asks for a fresh login.
//! Resolving a message's document is a Telegram call like any other, but its
//! error maps all too easily straight to `Network`, which keeps the dead key
//! and has the app retry a call that can never succeed. No fake reaches
//! `part_document`'s live client, so this holds every call in the API to
//! routing its failure through `revoked::failed`.

use std::path::{Path, PathBuf};

fn rust_files(dir: &Path, out: &mut Vec<PathBuf>) {
    for entry in std::fs::read_dir(dir).expect("a readable source directory") {
        let path = entry.expect("a directory entry").path();
        if path.is_dir() {
            rust_files(&path, out);
        } else if path.extension().is_some_and(|ext| ext == "rs") {
            out.push(path);
        }
    }
}

#[test]
fn every_document_resolved_in_the_api_checks_for_a_revoked_login() {
    let api = Path::new(env!("CARGO_MANIFEST_DIR")).join("src/api");
    let mut files = Vec::new();
    rust_files(&api, &mut files);
    let (mut calls, mut unchecked) = (0, Vec::new());
    for file in &files {
        let text = std::fs::read_to_string(file).expect("a readable source file");
        for (at, _) in text.match_indices("part_document(") {
            calls += 1;
            // The statement the call begins, up to its first `;`.
            let statement = text[at..].split(';').next().unwrap_or_default();
            if !statement.contains("revoked::failed(") {
                let line = text[..at].matches('\n').count() + 1;
                unchecked.push(format!("{}:{line}", file.display()));
            }
        }
    }
    assert!(
        calls > 0,
        "found no part_document call under {}",
        api.display()
    );
    assert!(
        unchecked.is_empty(),
        "a document resolved without the revocation check: {unchecked:#?}"
    );
}
