//! Which questions a quiz night asks: films, general, or both alternating.
//! Kids mode asks films only: the general pack is not checked for children.

use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::film_questions::film_questions;
use crate::protocol::{GameLength, QuizMode};
use crate::quiz_config::{Question, QuizConfig};
use crate::titles::TitlePool;

/// How many questions a kids quiz can ask from the library, counted up to the
/// longest quiz's: one per kid-safe title that makes a question.
pub fn kids_quiz_capacity(pool: &TitlePool) -> usize {
    let longest = QuizConfig::default().sized(GameLength::Long).rounds;
    // Which titles make a question does not depend on the draw, so any seed counts the same.
    film_questions(pool, &pool.eligible(true), &mut StdRng::seed_from_u64(0), longest, &mut HashSet::new()).len()
}

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
/// too small to make up the difference, or in kids mode the library).
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
        _ if kids_only => rounds,
        QuizMode::Films => rounds,
        QuizMode::General => 0,
        QuizMode::Mixed => rounds.div_ceil(2),
    };
    let eligible = pool.eligible(kids_only);
    let mut films = film_questions(pool, &eligible, rng, film_count, used);
    if kids_only && films.len() < film_count {
        // Nothing may fill a kids quiz, so tonight's film questions start over rather than run short.
        let mut fresh = HashSet::new();
        films = film_questions(pool, &eligible, rng, film_count, &mut fresh);
        used.extend(fresh);
    }
    // General fills whatever the films did not: half of a mixed night, all of
    // a general one, or the shortfall when the library is too small.
    let fill = if kids_only { 0 } else { rounds - films.len() };
    let mut generals = general_questions(general, rng, fill, used).into_iter();
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
