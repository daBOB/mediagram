//! Runs the web's own search fixtures against this crate's ranking.
//!
//! `search/rank.rs` is a port of `web/src/search/index.ts`; the cases under
//! `web/test/fixtures/search/` are what pins the two together, the same
//! accommodation `shared_watch_state_fixtures.rs` makes for the sync
//! format. A case that only passes after a change here does not belong in
//! the fixture — the web is authoritative, and this file exists to agree
//! with it, not to redefine it.
//!
//! This is also where German collation drift would show up: `rank::search`
//! ties within a field on `icu_collator`'s German comparison, and the pairs
//! here are real titles a folded-string tie-break — this crate's first
//! attempt at the tie-break — got wrong, before `icu_collator` replaced it.

use std::path::{Path, PathBuf};

use mediagram_core::catalog::SearchableSet;
use mediagram_core::search::rank::{Corpus, collator, search};
use serde::Deserialize;

const FIXTURES: &str = "../../web/test/fixtures/search";

fn fixture_path(file: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join(FIXTURES).join(file)
}

#[derive(Deserialize)]
struct SetInput {
    #[serde(rename = "setId")]
    set_id: String,
    #[serde(default)]
    title: Option<String>,
    #[serde(default)]
    show: Option<String>,
    #[serde(default)]
    chap: Option<String>,
    #[serde(default)]
    path: Option<String>,
    #[serde(default)]
    summary: Option<String>,
}

impl From<SetInput> for SearchableSet {
    fn from(input: SetInput) -> Self {
        SearchableSet {
            set_id: input.set_id,
            title: input.title,
            show: input.show,
            chap: input.chap,
            path: input.path,
            summary: input.summary,
        }
    }
}

#[derive(Deserialize)]
struct ExpectedHit {
    #[serde(rename = "setId")]
    set_id: String,
    matched: String,
    excerpt: Option<String>,
}

#[derive(Deserialize)]
struct Case {
    name: String,
    sets: Vec<SetInput>,
    query: String,
    expect: Vec<ExpectedHit>,
}

#[derive(Deserialize)]
struct Fixture {
    cases: Vec<Case>,
}

/// Loads `cases.json`, or `None` if the whole web checkout is not present —
/// the same accommodation `shared_playable_sql.rs` makes: the web is a
/// separate deliverable, and a sparse checkout that omits it entirely is
/// not a failure of this crate. Distinct from the fixture file itself being
/// missing while `web/` is right there, which panics below — that would be
/// the pairing broken, not the deliverable absent, and a skip would hide
/// exactly the drift this file exists to catch.
fn load() -> Option<Vec<Case>> {
    let web_root = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../web");
    if !web_root.exists() {
        eprintln!("skipping: {} is not present", web_root.display());
        return None;
    }
    let path = fixture_path("cases.json");
    let text = std::fs::read_to_string(&path)
        .unwrap_or_else(|err| panic!("{} is missing even though {} is present: {err}", path.display(), web_root.display()));
    let fixture: Fixture = serde_json::from_str(&text).unwrap_or_else(|err| panic!("{}: {err}", path.display()));
    Some(fixture.cases)
}

#[test]
fn search_matches_the_shared_fixture() {
    let Some(cases) = load() else { return };
    assert!(!cases.is_empty(), "cases.json holds no cases");
    let collator = collator();

    for case in cases {
        let sets: Vec<SearchableSet> = case.sets.into_iter().map(SearchableSet::from).collect();
        let corpus = Corpus::build(&sets);
        let hits = search(&corpus, &case.query, &collator);
        assert_eq!(hits.len(), case.expect.len(), "case: {}", case.name);
        for (hit, expected) in hits.iter().zip(&case.expect) {
            assert_eq!(hit.set_id, expected.set_id, "case: {}", case.name);
            assert_eq!(hit.matched.as_str(), expected.matched, "case: {}", case.name);
            assert_eq!(hit.excerpt, expected.excerpt, "case: {}", case.name);
        }
    }
}
