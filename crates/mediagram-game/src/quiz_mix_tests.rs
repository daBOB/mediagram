use rand::SeedableRng;

use super::*;
use crate::film_questions::tests::films;

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
