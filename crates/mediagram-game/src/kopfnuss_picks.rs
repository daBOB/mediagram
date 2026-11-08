//! A Kopfnuss pick phase: each phone votes a category, picks a trick and a
//! target, and the host settles both once the clock runs out.

use std::collections::BTreeMap;

use rand::Rng;
use rand::rngs::StdRng;
use rand::seq::IndexedRandom;

use crate::protocol::{PlayerId, ServerTime, Trick, TrickHit};

/// Where a phone is in its three taps.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Step {
    Vote,
    Trick,
    Target,
    Done,
}

/// One player's picks this round.
#[derive(Debug, Clone)]
pub(crate) struct Sheet {
    pub offered: Vec<Trick>,
    /// Who this player may target, frozen when the round starts.
    pub targets: Vec<PlayerId>,
    pub vote: Option<u8>,
    pub trick: Option<Trick>,
    pub target: Option<PlayerId>,
    /// When the current step's buttons opened; the phone keys its screen by it.
    pub step_at: ServerTime,
}

impl Sheet {
    pub(crate) fn step(&self) -> Step {
        match (self.vote, self.trick, self.target) {
            (None, ..) => Step::Vote,
            // Nobody to send a trick to: voting is all there is.
            _ if self.targets.is_empty() => Step::Done,
            (_, None, _) => Step::Trick,
            (_, _, None) => Step::Target,
            _ => Step::Done,
        }
    }

    /// Records tap `index` for the current step and opens the next one `gap`
    /// later; `false` for a tap that is no button, or comes more than `early`
    /// before this step opened (the second tap of a double tap).
    pub(crate) fn tap(&mut self, index: u8, offered_categories: usize, now: ServerTime, gap: ServerTime, early: ServerTime) -> bool {
        if now < self.step_at - early {
            return false;
        }
        let i = usize::from(index);
        match self.step() {
            Step::Vote if i < offered_categories => self.vote = Some(index),
            Step::Trick if i < self.offered.len() => self.trick = Some(self.offered[i]),
            Step::Target if i < self.targets.len() => self.target = Some(self.targets[i]),
            _ => return false,
        }
        self.step_at = now + gap;
        true
    }
}

/// A trick a player finished sending, at the time of their last tap.
#[derive(Debug, Clone, Copy)]
pub(crate) struct Sent {
    pub at: ServerTime,
    pub from: PlayerId,
    pub trick: Trick,
    pub to: PlayerId,
}

/// The majority's category. A tie goes to the last-placed player's vote when
/// they voted one of the tied options, otherwise a draw among the tied.
pub(crate) fn resolve_category(votes: &[(PlayerId, u8)], last_place: &[PlayerId], offered: usize, rng: &mut StdRng) -> u8 {
    let mut counts = vec![0usize; offered];
    for &(_, v) in votes {
        counts[usize::from(v)] += 1;
    }
    let max = counts.iter().copied().max().unwrap_or(0);
    if max == 0 {
        return rng.random_range(0..offered.max(1)) as u8;
    }
    let tied: Vec<u8> = (0..offered as u8).filter(|&c| counts[usize::from(c)] == max).collect();
    let mut chosen_by_last: Vec<u8> =
        votes.iter().filter(|(p, v)| last_place.contains(p) && tied.contains(v)).map(|&(_, v)| v).collect();
    chosen_by_last.sort_unstable();
    chosen_by_last.dedup();
    match (tied.as_slice(), chosen_by_last.as_slice()) {
        ([only], _) => *only,
        (_, [pick]) => *pick,
        _ => *tied.choose(rng).expect("max > 0 means one is tied"),
    }
}

/// Lands tricks in pick order; one past `max` on a phone, or one it already
/// carries, bounces off.
pub(crate) fn resolve_tricks(mut sent: Vec<Sent>, max: usize) -> (BTreeMap<PlayerId, Vec<Trick>>, Vec<TrickHit>) {
    sent.sort_by(|a, b| a.at.total_cmp(&b.at).then(a.from.cmp(&b.from)));
    let mut loads: BTreeMap<PlayerId, Vec<Trick>> = BTreeMap::new();
    let hits = sent
        .into_iter()
        .map(|s| {
            let load = loads.entry(s.to).or_default();
            let bounced = load.len() >= max || load.contains(&s.trick);
            if !bounced {
                load.push(s.trick);
            }
            TrickHit { from: s.from, to: s.to, trick: s.trick, bounced }
        })
        .collect();
    (loads, hits)
}

#[cfg(test)]
#[path = "kopfnuss_picks_tests.rs"]
mod tests;
