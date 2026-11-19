use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;

fn country(name: &str) -> &'static Country {
    COUNTRIES.iter().find(|c| c.name == name).expect("bundled country")
}

#[test]
fn every_bundled_outline_has_a_unique_name_a_tier_and_a_path() {
    let names: HashSet<&str> = COUNTRIES.iter().map(|c| c.name.as_str()).collect();
    assert_eq!(names.len(), COUNTRIES.len());
    for c in COUNTRIES.iter() {
        assert!((1..=3).contains(&c.tier), "{} has tier {}", c.name, c.tier);
        assert!(c.d.starts_with('M') && c.w.max(c.h) == 1000, "{} outline", c.name);
        assert!(!c.d.contains(['<', '>', '#', '\'', '"']), "{} path would break its data URL", c.name);
    }
    for tier in 1..=3 {
        let in_tier = COUNTRIES.iter().filter(|c| c.tier == tier).count();
        let rounds = tiers(ROUNDS).iter().filter(|&&t| t == tier).count();
        assert!(in_tier >= 3 * rounds, "tier {tier} has {in_tier} countries for {rounds} rounds");
    }
}

#[test]
fn a_game_asks_ten_countries_easiest_first_each_with_its_outline() {
    let questions = grenzfall_questions(ROUNDS, &mut StdRng::seed_from_u64(7), &mut HashSet::new());
    assert_eq!(questions.len(), grenzfall_config().rounds);
    let tiers: Vec<u8> = questions.iter().map(|q| country(&q.correct).tier).collect();
    assert_eq!(tiers, super::tiers(ROUNDS));
    let answers: HashSet<&str> = questions.iter().map(|q| q.correct.as_str()).collect();
    assert_eq!(answers.len(), questions.len(), "no country is asked twice");
    for q in &questions {
        assert_eq!(q.image.as_deref(), Some(outline_url(country(&q.correct)).as_str()));
        let mut options: HashSet<&str> = q.wrong.iter().map(String::as_str).collect();
        options.insert(&q.correct);
        assert_eq!(options.len(), 4, "four different names for {}", q.correct);
    }
}

#[test]
fn a_hard_round_offers_the_neighbours_and_a_middle_one_its_continent() {
    let mut rng = StdRng::seed_from_u64(3);
    for name in ["Ruanda", "Usbekistan", "Guatemala"] {
        let c = country(name);
        for w in look_alikes(c, &mut rng) {
            assert_eq!(w.subregion, c.subregion, "{name} offered {}", w.name);
        }
    }
    let kenia = country("Kenia");
    for w in look_alikes(kenia, &mut rng) {
        assert_eq!(w.continent, kenia.continent, "Kenia offered {}", w.name);
    }
}

#[test]
fn an_easy_round_offers_only_other_famous_shapes() {
    let mut rng = StdRng::seed_from_u64(5);
    for w in look_alikes(country("Italien"), &mut rng) {
        assert_eq!(w.tier, 1, "Italien offered {}", w.name);
    }
}

#[test]
fn a_second_game_tonight_shows_other_countries_until_a_tier_runs_out() {
    let mut used = HashSet::new();
    let mut rng = StdRng::seed_from_u64(11);
    let first: HashSet<String> = grenzfall_questions(ROUNDS, &mut rng, &mut used).into_iter().map(|q| q.correct).collect();
    let second: HashSet<String> = grenzfall_questions(ROUNDS, &mut rng, &mut used).into_iter().map(|q| q.correct).collect();
    assert!(first.is_disjoint(&second));
}

#[test]
fn a_used_up_tier_starts_over_with_the_unseen_ones_first_and_without_repeats() {
    let mut used = HashSet::new();
    let mut rng = StdRng::seed_from_u64(13);
    let hard = |qs: &[Question]| -> HashSet<String> {
        qs.iter().filter(|q| country(&q.correct).tier == 3).map(|q| q.correct.clone()).collect()
    };
    // 68 hard countries, three a game: after 22 games two were never shown.
    let mut shown = HashSet::new();
    let mut last = Vec::new();
    for _ in 0..22 {
        last = grenzfall_questions(ROUNDS, &mut rng, &mut used);
        shown.extend(hard(&last));
    }
    let unseen: HashSet<String> =
        COUNTRIES.iter().filter(|c| c.tier == 3 && !shown.contains(&c.name)).map(|c| c.name.clone()).collect();
    assert_eq!(unseen.len(), 2);
    let next = grenzfall_questions(ROUNDS, &mut rng, &mut used);
    assert!(unseen.is_subset(&hard(&next)), "the unseen ones come first");
    // Thirty easy countries made ten games; after the restart they do not repeat back to back.
    let easy = |qs: &[Question]| -> HashSet<String> {
        qs.iter().filter(|q| country(&q.correct).tier == 1).map(|q| q.correct.clone()).collect()
    };
    let after = grenzfall_questions(ROUNDS, &mut rng, &mut used);
    assert!(easy(&next).is_disjoint(&easy(&after)));
    assert!(easy(&last).is_disjoint(&easy(&next)));
}
