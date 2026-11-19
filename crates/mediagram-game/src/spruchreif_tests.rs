use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;
use crate::protocol::GameLength;

fn proverb(q: &Question) -> &'static Proverb {
    PROVERBS.iter().find(|p| q.text == format!("{} …", p.start)).expect("bundled proverb")
}

/// The longest ending a phone key and a TV answer bar hold on two lines.
const LONGEST_ENDING: usize = 40;

#[test]
fn every_proverb_has_a_unique_start_four_different_short_endings_and_a_tier() {
    let starts: HashSet<&str> = PROVERBS.iter().map(|p| p.start.as_str()).collect();
    assert_eq!(starts.len(), PROVERBS.len(), "a start is the history key");
    for p in PROVERBS.iter() {
        assert!((1..=3).contains(&p.tier), "{} has tier {}", p.start, p.tier);
        let endings: HashSet<&str> = p.wrong.iter().map(String::as_str).chain([p.end.as_str()]).collect();
        assert_eq!(endings.len(), 4, "four different endings for {}", p.start);
        for e in &endings {
            assert!(!e.is_empty() && e.chars().count() <= LONGEST_ENDING, "{} offers {e:?}", p.start);
            assert_eq!(e.trim(), *e, "{} offers {e:?}", p.start);
        }
    }
}

#[test]
fn every_language_and_tier_holds_three_long_games_worth() {
    let rounds = GameLength::Long.scale(ROUNDS);
    let tiers = tiers(rounds);
    for lang in [Lang::De, Lang::En] {
        for tier in 1..=3 {
            let held = PROVERBS.iter().filter(|p| p.lang == lang && p.tier == tier).count();
            let asked = tiers.iter().filter(|&&t| t == tier).count().div_ceil(2);
            assert!(held >= 3 * asked && held >= 25, "{lang:?} tier {tier} holds {held}");
        }
    }
}

#[test]
fn a_game_asks_ten_proverbs_easiest_first_by_turns_in_german_and_english() {
    for seed in 0..8 {
        let questions = spruchreif_questions(ROUNDS, &mut StdRng::seed_from_u64(seed), &mut HashSet::new());
        assert_eq!(questions.len(), spruchreif_config().rounds);
        let asked: Vec<&Proverb> = questions.iter().map(proverb).collect();
        assert_eq!(asked.iter().map(|p| p.tier).collect::<Vec<_>>(), tiers(ROUNDS));
        for pair in asked.windows(2) {
            assert_ne!(pair[0].lang, pair[1].lang, "the language changes every round");
        }
        for (q, p) in questions.iter().zip(&asked) {
            assert_eq!(q.correct, p.end);
            assert!(q.image.is_none());
            let flag = if p.lang == Lang::De { "🇩🇪" } else { "🇬🇧" };
            assert!(q.category.starts_with(flag), "{} is marked {}", p.start, q.category);
        }
    }
}

#[test]
fn either_language_may_open_the_game() {
    let openers: HashSet<bool> = (0..16)
        .map(|seed| {
            let q = &spruchreif_questions(ROUNDS, &mut StdRng::seed_from_u64(seed), &mut HashSet::new())[0];
            proverb(q).lang == Lang::De
        })
        .collect();
    assert_eq!(openers.len(), 2);
}

#[test]
fn games_tonight_repeat_no_proverb_until_a_group_runs_out_and_never_within_a_game() {
    let mut used = HashSet::new();
    let mut rng = StdRng::seed_from_u64(21);
    let first: HashSet<String> = spruchreif_questions(ROUNDS, &mut rng, &mut used).into_iter().map(|q| q.text).collect();
    let second: HashSet<String> = spruchreif_questions(ROUNDS, &mut rng, &mut used).into_iter().map(|q| q.text).collect();
    assert!(first.is_disjoint(&second));
    // Far more games than any group holds: groups start over, a game still asks each proverb once.
    for _ in 0..60 {
        let game = spruchreif_questions(GameLength::Long.scale(ROUNDS), &mut rng, &mut used);
        let texts: HashSet<&str> = game.iter().map(|q| q.text.as_str()).collect();
        assert_eq!(texts.len(), game.len());
    }
}

#[test]
fn a_used_up_group_offers_its_unseen_proverbs_first() {
    let hard_de = || PROVERBS.iter().filter(|p| p.lang == Lang::De && p.tier == 3);
    // Every hard German proverb but one was asked tonight.
    let left = hard_de().next_back().expect("hard German proverbs");
    let mut used: HashSet<String> = hard_de().filter(|p| p.start != left.start).map(shown_key).collect();
    let game = spruchreif_questions(ROUNDS, &mut StdRng::seed_from_u64(5), &mut used);
    let hard: Vec<&Proverb> = game.iter().map(proverb).filter(|p| p.lang == Lang::De && p.tier == 3).collect();
    assert_eq!(hard[0].start, left.start, "the one not yet asked comes first");
    if let [_, second] = hard[..] {
        assert_ne!(second.start, left.start, "the group starts over without it");
    }
}
