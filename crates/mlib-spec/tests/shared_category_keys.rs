//! Runs the web's own category-key fixtures against this crate's
//! `category_key`, so a later JS port and this one derive the same
//! `(department, item_key)` for the same input — the same accommodation
//! `mediagram-core/tests/shared_anime_fixtures.rs` makes for the anime rule.

use std::path::{Path, PathBuf};

use mlib_spec::category_key::category_key;
use serde::Deserialize;

const FIXTURES: &str = "../../web/test/fixtures/categories";

fn fixture_path(file: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join(FIXTURES).join(file)
}

#[derive(Deserialize)]
struct Case {
    name: String,
    kind: String,
    show: Option<String>,
    title: Option<String>,
    key: Option<(String, String)>,
}

/// Loads `keys.json`, or `None` if the whole web checkout is not present —
/// the same accommodation `shared_anime_fixtures.rs::load` makes.
fn load() -> Option<Vec<Case>> {
    let web_root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../web");
    if !web_root.exists() {
        eprintln!("skipping: {} is not present", web_root.display());
        return None;
    }
    let path = fixture_path("keys.json");
    let text = std::fs::read_to_string(&path)
        .unwrap_or_else(|err| panic!("{} is missing even though {} is present: {err}", path.display(), web_root.display()));
    let cases: Vec<Case> = serde_json::from_str(&text).unwrap_or_else(|err| panic!("{}: {err}", path.display()));
    Some(cases)
}

#[test]
fn category_key_matches_the_shared_fixture() {
    let Some(cases) = load() else { return };
    assert!(!cases.is_empty(), "keys.json holds no cases");

    for case in cases {
        let actual = category_key(&case.kind, case.show.as_deref(), case.title.as_deref())
            .map(|(department, item_key)| (department.to_string(), item_key));
        assert_eq!(actual, case.key, "case: {}", case.name);
    }
}
