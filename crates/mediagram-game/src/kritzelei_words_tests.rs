use super::*;
use rand::{rngs::StdRng, SeedableRng};
use std::collections::HashSet;

#[test]
fn the_list_has_300_words_and_100_for_kids() {
    assert!(WORDS.len() >= 300, "{}", WORDS.len());
    assert!(WORDS.iter().filter(|(_, kid)| *kid).count() >= 100);
}

#[test]
fn no_word_appears_twice() {
    let set: HashSet<String> = WORDS.iter().map(|(w, _)| w.to_lowercase()).collect();
    assert_eq!(set.len(), WORDS.len());
}

#[test]
fn words_are_tidy_nouns() {
    for (w, _) in WORDS {
        assert_eq!(w.trim(), *w, "{w:?}");
        assert!(w.chars().next().is_some_and(char::is_uppercase), "{w:?}");
    }
}

#[test]
fn a_kids_deck_holds_only_kid_words() {
    let deck = deck(true, &mut StdRng::seed_from_u64(1));
    assert!(deck.len() >= 100);
    for w in deck {
        assert!(WORDS.iter().any(|(x, kid)| *x == w && *kid), "{w}");
    }
}

#[test]
fn a_deck_never_repeats() {
    let deck = deck(false, &mut StdRng::seed_from_u64(2));
    assert_eq!(deck.len(), WORDS.len());
    assert_eq!(deck.iter().collect::<HashSet<_>>().len(), deck.len());
}
