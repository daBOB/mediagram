use rand::SeedableRng;

use super::*;
use crate::film_questions::tests::{film, films};
use crate::titles::{GameTitle, TitleKind};

fn general(n: usize) -> Vec<Question> {
    (0..n)
        .map(|i| Question {
            category: "Allgemein".into(),
            text: format!("Frage {i}?"),
            correct: "a".into(),
            wrong: ["b".into(), "c".into(), "d".into()],
            image: None,
        })
        .collect()
}

fn mix(mode: QuizMode, pool: &TitlePool, rounds: usize, used: &mut HashSet<String>) -> Vec<Question> {
    quiz_questions(mode, pool, false, &general(30), &mut StdRng::seed_from_u64(3), rounds, used)
}

#[test]
fn too_few_titles_tops_up_with_general() {
    let qs = mix(QuizMode::Films, &films(2), 8, &mut HashSet::new());
    assert_eq!(qs.len(), 8);
    assert!(qs.iter().filter(|q| q.category == "Allgemein").count() >= 6);
}

#[test]
fn mixed_alternates_film_and_general() {
    let qs = mix(QuizMode::Mixed, &films(12), 8, &mut HashSet::new());
    assert_eq!(qs.len(), 8);
    for (n, q) in qs.iter().enumerate() {
        assert_eq!(q.category == "Allgemein", n % 2 == 1, "position {n}: {}", q.category);
    }
}

#[test]
fn general_mode_asks_only_general() {
    let qs = mix(QuizMode::General, &films(12), 8, &mut HashSet::new());
    assert!(qs.len() == 8 && qs.iter().all(|q| q.category == "Allgemein"));
}

#[test]
fn no_repeats_within_an_evening() {
    let pool = films(20);
    let mut used = HashSet::new();
    let key = |q: &Question| (q.text.clone(), q.correct.clone());
    let first: HashSet<_> = mix(QuizMode::Mixed, &pool, 8, &mut used).iter().map(key).collect();
    let second: HashSet<_> = mix(QuizMode::Mixed, &pool, 8, &mut used).iter().map(key).collect();
    assert_eq!((first.len(), second.len()), (8, 8));
    assert!(first.is_disjoint(&second));
}

#[test]
fn an_exhausted_pack_starts_over_instead_of_running_dry() {
    let pool = films(0);
    let mut used = HashSet::new();
    for _ in 0..5 {
        let qs = quiz_questions(QuizMode::General, &pool, false, &general(10), &mut StdRng::seed_from_u64(1), 8, &mut used);
        assert_eq!(qs.len(), 8);
    }
}

fn general_texts(qs: &[Question]) -> HashSet<String> {
    qs.iter().filter(|q| q.category == "Allgemein").map(|q| q.text.clone()).collect()
}

#[test]
fn a_tiny_library_does_not_reset_general_history() {
    for pool in [films(0), films(2)] {
        let mut used = HashSet::new();
        let first = general_texts(&mix(QuizMode::Mixed, &pool, 8, &mut used));
        let second = general_texts(&mix(QuizMode::Mixed, &pool, 8, &mut used));
        assert!(first.len() >= 6 && second.len() >= 6);
        assert!(first.is_disjoint(&second));
    }
}

#[test]
fn a_general_reset_keeps_film_history() {
    let pool = films(20);
    let mut used = HashSet::new();
    let qs = quiz_questions(QuizMode::Mixed, &pool, false, &general(5), &mut StdRng::seed_from_u64(3), 8, &mut used);
    assert_eq!(qs.len(), 8);
    // 4 general of 5 used; the next night's 4 forces a general reset.
    let film_keys: HashSet<_> = used.iter().filter(|k| k.contains(':')).cloned().collect();
    assert_eq!(film_keys.len(), 4);
    quiz_questions(QuizMode::General, &pool, false, &general(5), &mut StdRng::seed_from_u64(4), 4, &mut used);
    assert!(film_keys.is_subset(&used));
}

fn kids_mix(mode: QuizMode, pool: &TitlePool, rounds: usize, used: &mut HashSet<String>) -> Vec<Question> {
    quiz_questions(mode, pool, true, &general(30), &mut StdRng::seed_from_u64(3), rounds, used)
}

#[test]
fn kids_mixed_and_films_ask_no_general_question() {
    for mode in [QuizMode::Mixed, QuizMode::Films, QuizMode::General] {
        let qs = kids_mix(mode, &films(12), 8, &mut HashSet::new());
        assert_eq!(qs.len(), 8, "{mode:?}");
        assert!(qs.iter().all(|q| q.category != "Allgemein"), "{mode:?}");
    }
}

#[test]
fn a_tiny_library_leaves_a_kids_quiz_short_instead_of_filling_it_with_general() {
    for mode in [QuizMode::Mixed, QuizMode::Films] {
        for n in [0, 2] {
            let qs = kids_mix(mode, &films(n), 8, &mut HashSet::new());
            assert_eq!(qs.len(), n, "{mode:?} with {n} titles");
            assert!(qs.iter().all(|q| q.category != "Allgemein"), "{mode:?} with {n} titles");
        }
    }
}

/// Six titles with every question kind and two with only a year: a later night
/// has unasked questions left about six titles only.
fn uneven_kids_library() -> TitlePool {
    let year_only = |i| GameTitle { still: None, directors: Vec::new(), cast: Vec::new(), ..film(i, true, TitleKind::Film) };
    TitlePool::new((0..6).map(|i| film(i, true, TitleKind::Film)).chain((6..8).map(year_only)).collect(), false)
}

#[test]
fn a_kids_quiz_keeps_its_length_night_after_night() {
    let pool = uneven_kids_library();
    let mut used = HashSet::new();
    for night in 0..6 {
        let qs = kids_mix(QuizMode::Mixed, &pool, 8, &mut used);
        assert_eq!(qs.len(), 8, "night {night}");
        assert!(qs.iter().all(|q| q.category != "Allgemein"), "night {night}");
    }
}

#[test]
fn kids_quiz_capacity_counts_titles_that_make_a_question_up_to_the_longest_quiz() {
    assert_eq!(kids_quiz_capacity(&films(0)), 0);
    assert_eq!(kids_quiz_capacity(&films(5)), 5);
    assert_eq!(kids_quiz_capacity(&uneven_kids_library()), 8);
    assert_eq!(kids_quiz_capacity(&films(40)), 12, "Lang asks twelve");
    let unsafe_only = TitlePool::new((0..20).map(|i| film(i, false, TitleKind::Film)).collect(), false);
    assert_eq!(kids_quiz_capacity(&unsafe_only), 0);
}
