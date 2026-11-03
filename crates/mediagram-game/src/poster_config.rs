//! Poster game inputs (timing, scoring), the per-game state types and which
//! titles can make a round.

use std::collections::HashSet;

use rand::SeedableRng;
use rand::rngs::StdRng;
use rand::seq::{IndexedRandom, SliceRandom};

use crate::protocol::{PlayerId, ServerTime};
use crate::titles::{GameTitle, TitleKind, TitlePool};

#[derive(Debug, Clone)]
pub struct PosterConfig {
    pub rounds: usize,
    /// Sharpness levels a poster passes through.
    pub steps: u8,
    pub step_ms: f64,
    /// How long the sharpest poster stays up before the round closes.
    pub grace_ms: f64,
    pub reveal_ms: f64,
    pub max_points: i32,
    pub min_points: i32,
    /// How far before its arrival a tap may claim to have happened.
    pub max_claim_lag_ms: f64,
    /// Taps arriving this early are accepted, to absorb clock-sync error.
    pub early_tolerance_ms: f64,
    pub seed: Option<u64>,
}

impl Default for PosterConfig {
    fn default() -> Self {
        Self {
            rounds: 10,
            steps: 8,
            step_ms: 2000.0,
            grace_ms: 3000.0,
            reveal_ms: 5000.0,
            max_points: 1000,
            min_points: 500,
            max_claim_lag_ms: 400.0,
            early_tolerance_ms: 150.0,
            seed: None,
        }
    }
}

pub fn points_at_step(cfg: &PosterConfig, step: u8) -> i32 {
    let span = f64::from(cfg.max_points - cfg.min_points);
    cfg.max_points - (f64::from(step) * span / f64::from(cfg.steps - 1)).round() as i32
}

pub fn step_at(cfg: &PosterConfig, started_at: ServerTime, at: ServerTime) -> u8 {
    let step = ((at - started_at) / cfg.step_ms).floor().clamp(0.0, f64::from(cfg.steps - 1));
    step as u8
}

pub(crate) struct Round {
    /// Index into the title pool.
    pub title: usize,
    pub name: String,
    pub series: bool,
    pub options: Vec<String>,
    pub correct: u8,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    Showing { started_at: ServerTime, closes_at: ServerTime },
    Reveal { ends_at: ServerTime },
    Final,
}

/// A right tap: the accepted time it claims.
pub(crate) type Claims = std::collections::BTreeMap<PlayerId, ServerTime>;

/// Names of the other titles of the same kind that have a poster, distinct
/// from each other and from `t`; the round finds its answer by name.
fn distractors(pool: &TitlePool, eligible: &[usize], t: &GameTitle) -> Vec<String> {
    let mut names: Vec<String> = eligible
        .iter()
        .map(|&j| pool.get(j))
        .filter(|o| o.kind == t.kind && o.poster.is_some() && o.title != t.title)
        .map(|o| o.title.clone())
        .collect();
    names.sort();
    names.dedup();
    names
}

/// Titles that can anchor a round: a poster and three distinct same-kind rivals.
fn playable(pool: &TitlePool, eligible: &[usize]) -> Vec<usize> {
    eligible
        .iter()
        .copied()
        .filter(|&i| pool.get(i).poster.is_some() && distractors(pool, eligible, pool.get(i)).len() >= 3)
        .collect()
}

/// At least four titles of one kind have a poster.
pub fn poster_ready(pool: &TitlePool, eligible: &[usize]) -> bool {
    !playable(pool, eligible).is_empty()
}

fn key(pool: &TitlePool, i: usize) -> String {
    format!("poster:{}", pool.get(i).key)
}

/// Up to `count` rounds over distinct titles. `used` holds `"poster:{key}"` of
/// what was shown tonight; once too few remain only those keys start over.
pub(crate) fn pick_rounds(
    pool: &TitlePool,
    eligible: &[usize],
    count: usize,
    used: &mut HashSet<String>,
    rng: &mut StdRng,
) -> Vec<Round> {
    let mut candidates = playable(pool, eligible);
    if candidates.iter().filter(|&&i| !used.contains(&key(pool, i))).count() < count {
        // Quiz history shares this set; leave its keys alone.
        used.retain(|k| !k.starts_with("poster:"));
    }
    candidates.retain(|&i| !used.contains(&key(pool, i)));
    candidates.shuffle(rng);
    candidates.truncate(count);
    candidates
        .into_iter()
        .map(|i| {
            let t = pool.get(i);
            used.insert(key(pool, i));
            let mut options: Vec<String> = distractors(pool, eligible, t).choose_multiple(rng, 3).cloned().collect();
            options.push(t.title.clone());
            options.shuffle(rng);
            let correct = options.iter().position(|o| *o == t.title).unwrap_or(0) as u8;
            Round { title: i, name: t.title.clone(), series: t.kind == TitleKind::Series, options, correct }
        })
        .collect()
}

pub(crate) fn rng_for(cfg: &PosterConfig) -> StdRng {
    cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64)
}
