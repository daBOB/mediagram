//! Runs the web's own anime-classification fixtures against this crate's
//! port of the rule.
//!
//! `shows::is_anime` is a port of `web/src/catalog/anime.ts`'s `isAnime`; the
//! cases under `web/test/fixtures/anime/` are what pins the two together,
//! the same accommodation `shared_search_fixtures.rs` makes for search
//! ranking. A case that only passes after a change here does not belong in
//! the fixture — the web is authoritative, and this file exists to agree
//! with it, not to redefine it.

use std::path::{Path, PathBuf};

use mediagram_core::shows::is_anime;
use serde::Deserialize;

const FIXTURES: &str = "../../web/test/fixtures/anime";

fn fixture_path(file: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join(FIXTURES).join(file)
}

#[derive(Deserialize)]
struct Case {
    name: String,
    kind: String,
    genres: Vec<String>,
    #[serde(rename = "originalLanguage")]
    original_language: Option<String>,
    #[serde(rename = "override")]
    forced: Option<bool>,
    anime: bool,
}

/// Loads `cases.json`, or `None` if the whole web checkout is not present —
/// the same accommodation `shared_search_fixtures.rs::load` makes.
fn load() -> Option<Vec<Case>> {
    let web_root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../web");
    if !web_root.exists() {
        eprintln!("skipping: {} is not present", web_root.display());
        return None;
    }
    let path = fixture_path("cases.json");
    let text = std::fs::read_to_string(&path)
        .unwrap_or_else(|err| panic!("{} is missing even though {} is present: {err}", path.display(), web_root.display()));
    let cases: Vec<Case> = serde_json::from_str(&text).unwrap_or_else(|err| panic!("{}: {err}", path.display()));
    Some(cases)
}

#[test]
fn is_anime_matches_the_shared_fixture() {
    let Some(cases) = load() else { return };
    assert!(!cases.is_empty(), "cases.json holds no cases");

    for case in cases {
        let actual = is_anime(&case.kind, &case.genres, case.original_language.as_deref(), case.forced);
        assert_eq!(actual, case.anime, "case: {}", case.name);
    }
}
