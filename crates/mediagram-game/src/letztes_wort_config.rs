//! Letztes Wort's timing and length, its question pack (one question per round with
//! five to ten right answers, bundled in `questions/letztes-wort.json`) and its catalogue entry.

use std::collections::HashSet;
use std::sync::LazyLock;

use rand::rngs::StdRng;
use rand::seq::SliceRandom;
use rand::{Rng, SeedableRng};
use serde::Deserialize;

use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::letztes_wort::LetztesWortGame;
use crate::protocol::{GameLength, GameSpeed, LobbySettings, ServerTime};

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // A host who judges and two who can take the stick from each other.
    name: "Letztes Wort", seats: Seats::Range(3, ROOM_CAP), timed: true, sized: true,
    // Its own pack, written for every age.
    kids: Kids::Plays, board: false, needs: Need::Nothing, start,
};

fn start(night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    let cfg = LetztesWortConfig::default().at(settings.speed).sized(settings.length);
    // Two spares for rounds restarted when a host walks away; drawn prompts count as asked.
    let wanted = cfg.laps * ctx.players.len() + 2;
    let prompts = letztes_wort_prompts(wanted, &mut StdRng::from_os_rng(), &mut night.history());
    Box::new(LetztesWortGame::new(cfg, prompts, ctx.players, ctx.now))
}

#[derive(Debug, Clone)]
pub struct LetztesWortConfig {
    /// Times round the table: every player hosts once per lap.
    pub laps: usize,
    /// A round's hidden time runs for a time between these two, from when the buzzers first open.
    pub time_min_ms: ServerTime,
    pub time_max_ms: ServerTime,
    /// The question shows this long before the buzzers open.
    pub intro_ms: ServerTime,
    /// After a verdict the buzzers reopen this much later, so a double tap answers nothing unseen.
    pub verdict_gap_ms: ServerTime,
    /// How long a round's result stays up before the next question.
    pub round_end_ms: ServerTime,
    /// Buzzes arriving this early are accepted, to absorb clock-sync error.
    pub early_tolerance_ms: ServerTime,
    pub seed: Option<u64>,
}

impl Default for LetztesWortConfig {
    fn default() -> Self {
        Self {
            laps: 2,
            time_min_ms: 15_000.0,
            time_max_ms: 35_000.0,
            intro_ms: 3000.0,
            verdict_gap_ms: 400.0,
            round_end_ms: 5000.0,
            early_tolerance_ms: 150.0,
            seed: None,
        }
    }
}

impl LetztesWortConfig {
    /// At the lobby's tempo: the hidden time runs longer or shorter.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.time_min_ms = speed.stretch(self.time_min_ms);
        self.time_max_ms = speed.stretch(self.time_max_ms);
        self
    }

    /// At the lobby's length: the laps round the table.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.laps = length.scale(self.laps);
        self
    }
}

/// One question as written in `questions/letztes-wort.json`.
#[derive(Debug, Deserialize)]
pub struct Prompt {
    pub q: String,
    /// Every right answer, as the host's phone lists them.
    pub answers: Vec<String>,
}

pub(crate) static PROMPTS: LazyLock<Vec<Prompt>> =
    LazyLock::new(|| serde_json::from_str(include_str!("../questions/letztes-wort.json")).expect("bundled prompts parse"));

/// A game's `rounds` prompts in random order. Prompts asked tonight (`used`) come back only
/// once all have been asked; then the pack starts over, this game's own picks excepted.
pub fn letztes_wort_prompts(rounds: usize, rng: &mut impl Rng, used: &mut HashSet<String>) -> Vec<&'static Prompt> {
    // A game never asks one prompt twice, so it asks at most the whole pack.
    let rounds = rounds.min(PROMPTS.len());
    let mut picked: Vec<&'static Prompt> = Vec::with_capacity(rounds);
    while picked.len() < rounds {
        let mut fresh: Vec<&'static Prompt> =
            PROMPTS.iter().filter(|p| !used.contains(&key(p)) && !picked.iter().any(|q| std::ptr::eq(*q, *p))).collect();
        if fresh.is_empty() {
            // Only this pack starts over; the other games share `used` and keep their history.
            used.retain(|k| !k.starts_with(KEY_PREFIX) || picked.iter().any(|p| key(p) == *k));
            continue;
        }
        fresh.shuffle(rng);
        for p in fresh.into_iter().take(rounds - picked.len()) {
            used.insert(key(p));
            picked.push(p);
        }
    }
    picked
}

const KEY_PREFIX: &str = "lw:";

fn key(p: &Prompt) -> String {
    format!("{KEY_PREFIX}{}", p.q)
}

#[cfg(test)]
#[path = "letztes_wort_config_tests.rs"]
mod tests;
