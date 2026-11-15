use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;
use crate::protocol::{GameLength, GameSpeed};

#[test]
fn every_prompt_has_five_to_ten_distinct_answers() {
    assert!(PROMPTS.len() >= 60, "{} prompts", PROMPTS.len());
    let questions: HashSet<&str> = PROMPTS.iter().map(|p| p.q.as_str()).collect();
    assert_eq!(questions.len(), PROMPTS.len(), "a question is listed twice");
    for p in PROMPTS.iter() {
        let answers: HashSet<&str> = p.answers.iter().map(String::as_str).collect();
        assert!((5..=10).contains(&p.answers.len()), "{}: {} answers", p.q, p.answers.len());
        assert_eq!(answers.len(), p.answers.len(), "{}: an answer is listed twice", p.q);
    }
}

#[test]
fn a_game_draws_no_prompt_twice() {
    let mut used = HashSet::new();
    let drawn = letztes_wort_prompts(16, &mut StdRng::seed_from_u64(7), &mut used);
    assert_eq!(drawn.len(), 16);
    let distinct: HashSet<&str> = drawn.iter().map(|p| p.q.as_str()).collect();
    assert_eq!(distinct.len(), 16);
    assert_eq!(used.len(), 16, "each drawn prompt is marked asked tonight");
}

#[test]
fn asked_tonight_comes_back_only_when_all_were_asked() {
    let mut rng = StdRng::seed_from_u64(3);
    let mut used = HashSet::new();
    let first = letztes_wort_prompts(PROMPTS.len() - 2, &mut rng, &mut used);
    let next = letztes_wort_prompts(2, &mut rng, &mut used);
    for p in &next {
        assert!(!first.iter().any(|f| std::ptr::eq(*f, *p)), "{} was asked already", p.q);
    }
    // Everything has been asked now; the next game starts over instead of running dry,
    // and leaves the other games' history alone.
    used.insert("spruch:Wer zuletzt lacht".into());
    assert_eq!(letztes_wort_prompts(5, &mut rng, &mut used).len(), 5);
    assert!(used.contains("spruch:Wer zuletzt lacht"));
}

#[test]
fn tempo_stretches_only_the_hidden_time() {
    let normal = LetztesWortConfig::default();
    let relaxed = LetztesWortConfig::default().at(GameSpeed::Relaxed);
    assert_eq!((relaxed.time_min_ms, relaxed.time_max_ms), (normal.time_min_ms * 2.0, normal.time_max_ms * 2.0));
    assert_eq!((relaxed.intro_ms, relaxed.verdict_gap_ms, relaxed.round_end_ms), (normal.intro_ms, normal.verdict_gap_ms, normal.round_end_ms));
    assert_eq!((normal.time_min_ms, normal.time_max_ms), (15_000.0, 35_000.0));
}

#[test]
fn length_scales_the_laps() {
    let laps = |l| LetztesWortConfig::default().sized(l).laps;
    assert_eq!([laps(GameLength::Short), laps(GameLength::Normal), laps(GameLength::Long)], [1, 2, 3]);
}
