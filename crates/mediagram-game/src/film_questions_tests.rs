use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;

use super::*;
use crate::titles::TitleKind;

/// A title whose every field is unique to `i`, so a leak shows by name.
pub(crate) fn film(i: usize, kids_ok: bool, kind: TitleKind) -> GameTitle {
    let tag = if kids_ok { "safe" } else { "unsafe" };
    GameTitle {
        key: format!("{tag}-{i}"),
        kind,
        title: format!("Titel {tag} {i}"),
        year: Some(1990 + i as u16),
        poster: None,
        still: Some(format!("{tag}{i}.png").into()),
        directors: vec![format!("Regie {tag} {i}")],
        cast: (0..3).map(|c| format!("Darsteller {tag} {i}{c}")).collect(),
        kids_ok,
    }
}

pub(crate) fn films(n: usize) -> TitlePool {
    TitlePool::new((0..n).map(|i| film(i, true, TitleKind::Film)).collect(), false)
}

fn ask(pool: &TitlePool, seed: u64, count: usize) -> Vec<Question> {
    film_questions(pool, &pool.eligible(false), &mut StdRng::seed_from_u64(seed), count, &mut HashSet::new())
}

#[test]
fn kids_only_never_uses_unsafe_titles() {
    let titles = (0..6)
        .map(|i| film(i, true, TitleKind::Film))
        .chain((6..12).map(|i| film(i, false, TitleKind::Film)))
        .collect();
    let pool = TitlePool::new(titles, false);
    for seed in 0..50 {
        let qs = film_questions(&pool, &pool.eligible(true), &mut StdRng::seed_from_u64(seed), 6, &mut HashSet::new());
        assert!(!qs.is_empty());
        for q in &qs {
            for option in std::iter::once(&q.correct).chain(&q.wrong).chain(std::iter::once(&q.text)) {
                assert!(!option.contains("unsafe"), "seed {seed}: {option}");
            }
            if let Some(url) = &q.image {
                let i: usize = url.split('/').nth(2).unwrap().parse().unwrap();
                assert!(pool.get(i).kids_ok, "seed {seed}: image of an unsafe title");
            }
        }
    }
}

#[test]
fn options_are_four_distinct_with_the_answer_once() {
    let pool = films(12);
    for seed in 0..20 {
        for q in ask(&pool, seed, 8) {
            let mut options = vec![q.correct.clone()];
            options.extend(q.wrong.clone());
            options.sort();
            options.dedup();
            assert_eq!(options.len(), 4, "{}", q.text);
        }
    }
}

#[test]
fn two_titles_sharing_a_name_still_give_distinct_options() {
    let mut titles: Vec<_> = (0..8).map(|i| film(i, true, TitleKind::Film)).collect();
    let shared = titles[0].title.clone();
    for t in &mut titles[4..] {
        t.title = shared.clone();
    }
    let pool = TitlePool::new(titles, false);
    for seed in 0..20 {
        for q in ask(&pool, seed, 8) {
            let mut options: Vec<_> = q.wrong.to_vec();
            options.push(q.correct.clone());
            options.sort();
            options.dedup();
            assert_eq!(options.len(), 4, "{}", q.text);
        }
    }
}

#[test]
fn year_distractors_are_near_and_distinct() {
    let pool = films(8);
    let mut seen = 0;
    for seed in 0..20 {
        for q in ask(&pool, seed, 8).iter().filter(|q| q.category == "Erscheinungsjahr") {
            seen += 1;
            let year: i32 = q.correct.parse().unwrap();
            let wrong: HashSet<i32> = q.wrong.iter().map(|w| w.parse().unwrap()).collect();
            assert_eq!(wrong.len(), 3);
            assert!(wrong.iter().all(|&y| y != year && (y - year).abs() <= 6), "{year} {wrong:?}");
        }
    }
    assert!(seen > 0);
}

#[test]
fn series_never_get_director_questions() {
    let pool = TitlePool::new((0..10).map(|i| film(i, true, TitleKind::Series)).collect(), false);
    for seed in 0..20 {
        let qs = ask(&pool, seed, 8);
        assert!(!qs.is_empty());
        assert!(qs.iter().all(|q| q.category != "Regie"));
        assert!(qs.iter().filter(|q| q.image.is_some()).all(|q| q.category == "Serienbild"));
    }
}
