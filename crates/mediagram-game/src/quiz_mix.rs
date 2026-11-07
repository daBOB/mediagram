//! Which questions a quiz night asks: films, general, or both alternating.

use std::collections::HashSet;

use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::film_questions::film_questions;
use crate::protocol::QuizMode;
use crate::quiz_config::Question;
use crate::titles::TitlePool;

/// Unasked general questions first; once too few remain the night starts over.
fn general_questions(general: &[Question], rng: &mut StdRng, count: usize, used: &mut HashSet<String>) -> Vec<Question> {
    if general.iter().filter(|q| !used.contains(&q.text)).count() < count {
        // Only general keys: film history shares this set.
        used.retain(|k| !general.iter().any(|q| q.text == *k));
    }
    let mut fresh: Vec<&Question> = general.iter().filter(|q| !used.contains(&q.text)).collect();
    fresh.shuffle(rng);
    fresh.truncate(count);
    used.extend(fresh.iter().map(|q| q.text.clone()));
    fresh.into_iter().cloned().collect()
}

/// `rounds` questions in asking order (fewer only when the general pack is
/// too small to make up the difference).
pub fn quiz_questions(
    mode: QuizMode,
    pool: &TitlePool,
    kids_only: bool,
    general: &[Question],
    rng: &mut StdRng,
    rounds: usize,
    used: &mut HashSet<String>,
) -> Vec<Question> {
    let film_count = match mode {
        QuizMode::Films => rounds,
        QuizMode::General => 0,
        QuizMode::Mixed => rounds.div_ceil(2),
    };
    let films = film_questions(pool, &pool.eligible(kids_only), rng, film_count, used);
    // General fills whatever the films did not: half of a mixed night, all of
    // a general one, or the shortfall when the library is too small.
    let mut generals = general_questions(general, rng, rounds - films.len(), used).into_iter();
    let mut out = Vec::with_capacity(rounds);
    let film_total = films.len();
    for (n, f) in films.into_iter().enumerate() {
        out.push(f);
        if mode == QuizMode::Mixed && n + 1 < film_total {
            out.extend(generals.next());
        }
    }
    out.extend(generals);
    out
}

#[cfg(test)]
#[path = "quiz_mix_tests.rs"]
mod tests;
