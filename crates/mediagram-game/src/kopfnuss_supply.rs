//! Where Kopfnuss gets its questions: the general pack by category, plus the
//! film questions as one category of their own. Asked questions are marked in
//! the night's shared history as they are taken, so a later game tonight
//! (Kopfnuss or Quiz) does not repeat them.

use std::collections::HashSet;
use std::sync::{Arc, Mutex, MutexGuard};

use rand::rngs::StdRng;
use rand::seq::{IndexedRandom, SliceRandom};

use crate::quiz_config::Question;

/// The category the film questions from the library are filed under.
pub(crate) const FILMS: &str = "Eure Filme";

pub(crate) struct QuestionSupply {
    general: Arc<Vec<Question>>,
    /// Drawn by the factory and already in the night's history.
    films: Vec<Question>,
    used: Arc<Mutex<HashSet<String>>>,
}

impl QuestionSupply {
    /// Starts the general pack over when fewer than `needed` of it are left
    /// tonight, like the buzzer quiz does.
    pub(crate) fn new(
        general: Arc<Vec<Question>>,
        films: Vec<Question>,
        used: Arc<Mutex<HashSet<String>>>,
        needed: usize,
    ) -> Self {
        let films = films.into_iter().map(|f| Question { category: FILMS.into(), ..f }).collect();
        let supply = Self { general, films, used };
        {
            let mut used = supply.history();
            if supply.general.iter().filter(|q| !used.contains(&q.text)).count() < needed {
                // Only general keys: film history shares this set.
                used.retain(|k| !supply.general.iter().any(|q| q.text == *k));
            }
        }
        supply
    }

    fn history(&self) -> MutexGuard<'_, HashSet<String>> {
        // A poisoned lock still holds a usable set; losing it only risks a repeat.
        self.used.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Unasked questions of `category`, or of every general category with `None`.
    fn fresh(&self, category: Option<&str>) -> Vec<&Question> {
        if category == Some(FILMS) {
            return self.films.iter().collect();
        }
        let used = self.history();
        self.general
            .iter()
            .filter(|q| category.is_none_or(|c| q.category == c) && !used.contains(&q.text))
            .collect()
    }

    pub(crate) fn left(&self, category: &str) -> usize {
        self.fresh(Some(category)).len()
    }

    /// Up to `count` distinct categories that can still fill a round of `per_round`.
    pub(crate) fn offer(&self, rng: &mut StdRng, count: usize, per_round: usize) -> Vec<String> {
        let mut categories: Vec<&str> = self.general.iter().map(|q| q.category.as_str()).collect();
        categories.sort_unstable();
        categories.dedup();
        let open: Vec<&str> =
            categories.into_iter().chain([FILMS]).filter(|c| self.left(c) >= per_round.max(1)).collect();
        open.choose_multiple(rng, count).map(|c| c.to_string()).collect()
    }

    /// Up to `n` unasked questions of `category`, now marked as asked.
    pub(crate) fn take(&mut self, category: &str, n: usize, rng: &mut StdRng) -> Vec<Question> {
        if category == FILMS {
            let keep = self.films.len().saturating_sub(n);
            return self.films.split_off(keep);
        }
        self.draw(Some(category), n, rng)
    }

    /// Up to `n` unasked general questions of any category, for the finale.
    pub(crate) fn deck(&mut self, n: usize, rng: &mut StdRng) -> Vec<Question> {
        self.draw(None, n, rng)
    }

    fn draw(&mut self, category: Option<&str>, n: usize, rng: &mut StdRng) -> Vec<Question> {
        let mut fresh: Vec<Question> = self.fresh(category).into_iter().cloned().collect();
        fresh.shuffle(rng);
        fresh.truncate(n);
        self.history().extend(fresh.iter().map(|q| q.text.clone()));
        fresh
    }
}

#[cfg(test)]
#[path = "kopfnuss_supply_tests.rs"]
mod tests;
