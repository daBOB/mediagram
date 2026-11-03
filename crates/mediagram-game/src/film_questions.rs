//! Quiz questions generated from the title list: still, year, director, cast.

use std::collections::HashSet;

use rand::prelude::IndexedRandom;
use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::quiz_config::Question;
use crate::titles::{GameTitle, ImageKind, TitleKind, TitlePool, image_url};

#[derive(Clone, Copy)]
enum Kind {
    Still,
    Year,
    Director,
    Cast,
}

const KINDS: [(Kind, &str); 4] =
    [(Kind::Still, "still"), (Kind::Year, "year"), (Kind::Director, "director"), (Kind::Cast, "cast")];

fn needs(kind: Kind, t: &GameTitle) -> bool {
    match kind {
        Kind::Still => t.still.is_some(),
        Kind::Year => t.year.is_some(),
        Kind::Director => t.kind == TitleKind::Film && !t.directors.is_empty(),
        Kind::Cast => !t.cast.is_empty(),
    }
}

/// Three distractors, distinct from each other and from `exclude`; `None`
/// when the candidates cannot supply three. Distinct by text, because the
/// quiz finds the correct option by its text.
fn three(rng: &mut StdRng, mut candidates: Vec<String>, exclude: &[String]) -> Option<[String; 3]> {
    candidates.retain(|c| !exclude.contains(c));
    candidates.sort();
    candidates.dedup();
    let picked: Vec<String> = candidates.choose_multiple(rng, 3).cloned().collect();
    picked.try_into().ok()
}

fn build(kind: Kind, pool: &TitlePool, eligible: &[usize], i: usize, rng: &mut StdRng) -> Option<Question> {
    let t = pool.get(i);
    let others = || eligible.iter().filter(|&&j| j != i).map(|&j| pool.get(j));
    let (category, text, correct, wrong, image) = match kind {
        Kind::Still => {
            let same_kind = others().filter(|o| o.kind == t.kind).map(|o| o.title.clone()).collect();
            let (category, text) = match t.kind {
                TitleKind::Series => ("Serienbild", "Aus welcher Serie ist dieses Bild?"),
                TitleKind::Film => ("Filmszene", "Aus welchem Film ist diese Szene?"),
            };
            let wrong = three(rng, same_kind, std::slice::from_ref(&t.title))?;
            (category, text.to_string(), t.title.clone(), wrong, Some(image_url(i, ImageKind::Still)))
        }
        Kind::Year => {
            let year = i32::from(t.year?);
            let near = (year - 6..=year + 6).filter(|&y| y != year && y > 0).map(|y| y.to_string()).collect();
            let wrong = three(rng, near, &[])?;
            ("Erscheinungsjahr", format!("In welchem Jahr erschien „{}“?", t.title), year.to_string(), wrong, None)
        }
        Kind::Director => {
            let names = others().filter(|o| o.kind == TitleKind::Film).flat_map(|o| o.directors.clone()).collect();
            let wrong = three(rng, names, &t.directors)?;
            ("Regie", format!("Wer führte bei „{}“ Regie?", t.title), t.directors[0].clone(), wrong, None)
        }
        Kind::Cast => {
            let names = others().flat_map(|o| o.cast.iter().take(3).cloned().collect::<Vec<_>>()).collect();
            let wrong = three(rng, names, &t.cast)?;
            ("Besetzung", format!("Wer spielt in „{}“ mit?", t.title), t.cast[0].clone(), wrong, None)
        }
    };
    Some(Question { category: category.into(), text, correct, wrong, image })
}

/// Up to `count` questions about distinct titles drawn from `eligible`.
/// `used` holds `"{kind}:{title.key}"` of what was already asked tonight.
pub fn film_questions(
    pool: &TitlePool,
    eligible: &[usize],
    rng: &mut StdRng,
    count: usize,
    used: &mut HashSet<String>,
) -> Vec<Question> {
    let mut candidates: Vec<(usize, Kind, &str)> = eligible
        .iter()
        .flat_map(|&i| KINDS.iter().filter(move |(k, _)| needs(*k, pool.get(i))).map(move |&(k, n)| (i, k, n)))
        .collect();
    let key = |&(i, _, n): &(usize, Kind, &str)| format!("{n}:{}", pool.get(i).key);
    if candidates.iter().filter(|c| !used.contains(&key(c))).count() < count {
        used.clear();
    }
    candidates.shuffle(rng);
    let mut out = Vec::new();
    let mut taken = HashSet::new();
    for c in candidates {
        if out.len() == count {
            break;
        }
        // One question per title keeps a night from quizzing the same film twice in a row.
        if used.contains(&key(&c)) || taken.contains(&c.0) {
            continue;
        }
        if let Some(q) = build(c.1, pool, eligible, c.0, rng) {
            used.insert(key(&c));
            taken.insert(c.0);
            out.push(q);
        }
    }
    out
}

#[cfg(test)]
#[path = "film_questions_tests.rs"]
pub(crate) mod tests;
