//! Ported from the excerpt-shaped cases in `web/test/search-index.test.ts`
//! (`describe("showing why a summary matched")`), which exercise `excerpt`
//! directly there via `SearchIndex`; here they call it directly.

use super::*;
use crate::search::normalize::terms;

fn wanted(query: &str) -> Vec<String> {
    terms(Some(query))
}

#[test]
fn no_summary_has_no_excerpt() {
    assert_eq!(excerpt(None, &wanted("anything")), None);
}

#[test]
fn a_summary_hit_carries_the_words_around_the_match() {
    let long = format!("{}{}{}", "Vorwort. ".repeat(40), "Der Broker verlangt eine Verifizierung. ", "Ende. ".repeat(40));
    let hit = excerpt(Some(&long), &wanted("broker")).unwrap();
    assert!(hit.contains("Broker"));
    assert!(hit.len() < 260);
}

#[test]
fn markdown_in_a_summary_reads_as_prose_in_the_excerpt() {
    let summary = "Ein Satz. **Optionen:** geben dem *Käufer* das ## Recht dazu.";
    let hit = excerpt(Some(summary), &wanted("optionen")).unwrap();
    assert!(hit.contains("Optionen:"));
    assert!(!hit.contains("**"));
    assert!(!hit.contains("##"));
}

#[test]
fn the_window_lands_on_the_match_however_far_into_the_summary_it_sits() {
    let before = "**Für größere Märkte** prüfen wir zunächst die Händler.\n\n## Weiter\n\n".repeat(30);
    let summary = format!("{before}Die Volatilität entscheidet. {}", "Schluss. ".repeat(20));
    let hit = excerpt(Some(&summary), &wanted("volatilitaet")).unwrap();
    assert!(hit.contains("Volatilität"));
}

#[test]
fn a_term_nothing_carries_has_no_anchor() {
    assert_eq!(excerpt(Some("Wie man ein Signal im Chart liest."), &wanted("kryptowahrung")), None);
}
