//! Ported from `web/test/search-index.test.ts`'s non-order cases: matching
//! and its limits. The order-sensitive cases — where German collation could
//! make this crate disagree with the web — live in
//! `shared_search_fixtures.rs` instead, run against both.

use super::*;

fn set(set_id: &str, title: Option<&str>) -> SearchableSet {
    SearchableSet { set_id: set_id.to_string(), title: title.map(str::to_string), show: None, chap: None, path: None, summary: None }
}

/// Builds the corpus and a collator fresh, the ceremony `Core::search`
/// normally amortises across calls — irrelevant here, where each test's
/// whole point is the ranking, not how often it is repeated.
fn search_all(sets: &[SearchableSet], query: &str) -> Vec<Hit> {
    search(&Corpus::build(sets), query, &collator())
}

fn matched_titles(hits: &[Hit], sets: &[SearchableSet]) -> Vec<Option<String>> {
    hits.iter()
        .map(|hit| sets.iter().find(|s| s.set_id == hit.set_id).unwrap().title.clone())
        .collect()
}

#[test]
fn a_title_however_it_is_typed() {
    let sets = vec![set("1", Some("Überblick"))];
    assert_eq!(search_all(&sets, "uberblick").len(), 1);
    assert_eq!(search_all(&sets, "Überblick").len(), 1);
    assert_eq!(search_all(&sets, "UBERBLICK").len(), 1);
}

#[test]
fn an_umlaut_spelled_out_the_way_a_keyboard_without_one_forces() {
    let sets = vec![set("1", Some("Überblick"))];
    assert_eq!(search_all(&sets, "ueberblick").len(), 1);
    assert_eq!(search_all(&sets, "uberblick").len(), 1);
}

#[test]
fn a_summary_found_by_a_spelled_out_umlaut_still_shows_its_excerpt() {
    let sets = vec![SearchableSet {
        set_id: "1".into(),
        title: Some("Interpretation".into()),
        show: None,
        chap: None,
        path: None,
        summary: Some("Die Volatilität steigt im Chart.".into()),
    }];
    let hits = search_all(&sets, "volatilitaet");
    assert_eq!(hits[0].matched, Field::Summary);
    assert!(hits[0].excerpt.as_deref().unwrap().contains("Volatilität"));
}

#[test]
fn part_of_a_word_not_only_the_start_of_one() {
    let sets = vec![set("1", Some("Kontoeröffnung"))];
    assert_eq!(search_all(&sets, "eroffnung").len(), 1);
}

#[test]
fn every_term_has_to_match_somewhere_not_just_one_of_them() {
    let sets = vec![
        SearchableSet { set_id: "1".into(), title: Some("Kontoeröffnung".into()), show: None, chap: None, path: Some("Basislektionen/5. Broker".into()), summary: None },
        SearchableSet { set_id: "2".into(), title: Some("Broker Vergleich".into()), show: None, chap: None, path: Some("Basislektionen/1. Start".into()), summary: None },
    ];
    let hits = search_all(&sets, "broker kontoeroffnung");
    assert_eq!(matched_titles(&hits, &sets), vec![Some("Kontoeröffnung".to_string())]);
}

#[test]
fn an_empty_query_finds_nothing_rather_than_everything() {
    let sets = vec![set("1", None), set("2", None), set("3", None)];
    assert!(search_all(&sets, "").is_empty());
    assert!(search_all(&sets, "   ").is_empty());
}

#[test]
fn a_term_nothing_carries_finds_nothing() {
    let sets = vec![set("1", Some("Einführung"))];
    assert!(search_all(&sets, "kryptowahrung").is_empty());
}

#[test]
fn a_title_hit_needs_no_excerpt() {
    let sets = vec![set("1", Some("Broker Vergleich"))];
    assert_eq!(search_all(&sets, "broker")[0].excerpt, None);
}

#[test]
fn it_stops_well_short_of_returning_the_library() {
    let sets: Vec<SearchableSet> = (0..200).map(|i| set(&i.to_string(), Some(&format!("Einführung {i}")))).collect();
    assert!(search_all(&sets, "einfuhrung").len() <= MAX_HITS);
}
