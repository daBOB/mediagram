use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;

#[test]
fn decks_have_the_right_size_and_no_duplicates() {
    for (lowest, size) in [(Rank::Seven, 32), (Rank::Six, 36), (Rank::Two, 52)] {
        let deck = deck_from(lowest);
        assert_eq!(deck.len(), size);
        assert_eq!(deck.iter().collect::<HashSet<_>>().len(), size);
        assert!(deck.iter().all(|c| c.rank >= lowest));
    }
}

#[test]
fn shuffle_is_a_permutation_and_follows_the_seed() {
    let a = shuffled(deck_from(Rank::Seven), &mut StdRng::seed_from_u64(7));
    let b = shuffled(deck_from(Rank::Seven), &mut StdRng::seed_from_u64(7));
    assert_eq!(a, b);
    assert_ne!(a, deck_from(Rank::Seven));
    assert_eq!(a.iter().collect::<HashSet<_>>().len(), 32);
}
