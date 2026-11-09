//! Dreifelder's rules, free of time and I/O: who is out after a round, when
//! the game is over, and the finishing order.
//!
//! A wrong or missing pick puts an alive player out, unless every alive
//! player got it wrong; a player who left without a pick is out regardless.
//! Players who are out keep guessing as ghosts: that
//! never changes who is alive, but separates players who went out together.

use std::cmp::Reverse;
use std::collections::{BTreeMap, BTreeSet};

use crate::protocol::PlayerId;

/// Final score per player beaten, so the scores order like the placings.
const POINTS_PER_PLACE: i32 = 100;

#[derive(Debug, Clone, Default)]
pub(crate) struct Tally {
    alive: BTreeSet<PlayerId>,
    /// The 0-based round each player went out in.
    out_round: BTreeMap<PlayerId, usize>,
    /// Right guesses made after going out.
    ghost_right: BTreeMap<PlayerId, u32>,
    /// Speed points of right answers while alive.
    speed: BTreeMap<PlayerId, i32>,
}

impl Tally {
    pub(crate) fn new(players: impl IntoIterator<Item = PlayerId>) -> Self {
        Self { alive: players.into_iter().collect(), ..Self::default() }
    }

    pub(crate) fn is_alive(&self, player: PlayerId) -> bool {
        self.alive.contains(&player)
    }

    /// Whether `player` started this game (alive or out).
    pub(crate) fn plays(&self, player: PlayerId) -> bool {
        self.is_alive(player) || self.out_round.contains_key(&player)
    }

    /// Everyone who started this game, lowest id first.
    pub(crate) fn players(&self) -> Vec<PlayerId> {
        let mut all: Vec<PlayerId> = self.alive().into_iter().chain(self.out_round.keys().copied()).collect();
        all.sort_unstable();
        all
    }

    pub(crate) fn alive(&self) -> Vec<PlayerId> {
        self.alive.iter().copied().collect()
    }

    /// Who is out and in which 0-based round, in the order they went out.
    pub(crate) fn out(&self) -> Vec<(PlayerId, usize)> {
        let mut out: Vec<_> = self.out_round.iter().map(|(&p, &r)| (p, r)).collect();
        out.sort_by_key(|&(p, r)| (r, p));
        out
    }

    /// Settles 0-based `round`; `picks` holds `(choice, speed points)` of
    /// everyone who picked, `connected` who is still here. Returns who went
    /// out, lowest id first.
    pub(crate) fn settle_round(
        &mut self,
        round: usize,
        correct: u8,
        picks: &BTreeMap<PlayerId, (u8, i32)>,
        connected: &[PlayerId],
    ) -> Vec<PlayerId> {
        let right = |p: &PlayerId| picks.get(p).is_some_and(|&(c, _)| c == correct);
        let left = |p: &PlayerId| !picks.contains_key(p) && !connected.contains(p);
        for (&p, &(choice, points)) in picks {
            if choice != correct {
                continue;
            }
            if self.is_alive(p) {
                *self.speed.entry(p).or_default() += points;
            } else if self.out_round.contains_key(&p) {
                *self.ghost_right.entry(p).or_default() += 1;
            }
        }
        // Nobody right: only those who left go, so an absent player cannot win on others' mistakes.
        let anyone_right = self.alive.iter().any(right);
        let mut out: Vec<PlayerId> =
            self.alive.iter().copied().filter(|p| if anyone_right { !right(p) } else { left(p) }).collect();
        if out.len() == self.alive.len() {
            // Everyone still in left: nobody goes, and the game ends on `is_over`.
            out.clear();
        }
        for &p in &out {
            self.alive.remove(&p);
            self.out_round.insert(p, round);
        }
        out
    }

    /// Over with one player left, after `cap` rounds, or when no alive player is connected.
    pub(crate) fn is_over(&self, played: usize, cap: usize, connected: &[PlayerId]) -> bool {
        self.alive.len() <= 1 || played >= cap || !connected.iter().any(|p| self.is_alive(*p))
    }

    /// The alive by speed, then the out: later out first, then more ghost
    /// guesses, then speed; the lower id breaks what is left.
    pub(crate) fn finishing_order(&self) -> Vec<PlayerId> {
        let speed = |p: PlayerId| self.speed.get(&p).copied().unwrap_or(0);
        let mut order = self.players();
        order.sort_by_key(|&p| {
            let gone = self.out_round.get(&p).map(|&r| Reverse(r));
            let ghost = self.ghost_right.get(&p).copied().unwrap_or(0);
            // `None` (alive) sorts before any `Some` (out).
            (gone, Reverse(ghost), Reverse(speed(p)), p)
        });
        order
    }
}

/// Each player's final score, `POINTS_PER_PLACE` per player placed below them.
pub(crate) fn placement_scores(order: &[PlayerId]) -> Vec<(PlayerId, i32)> {
    let n = order.len() as i32;
    order.iter().enumerate().map(|(i, &p)| (p, (n - 1 - i as i32) * POINTS_PER_PLACE)).collect()
}

#[cfg(test)]
#[path = "dreifelder_rules_tests.rs"]
mod tests;
