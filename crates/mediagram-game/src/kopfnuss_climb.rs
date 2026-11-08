//! Gipfelsturm, the Kopfnuss finale: every phone runs the same question
//! stream at its own pace. A right answer climbs a step, a wrong one shuts the
//! next question for a moment. First to the top wins; at the bell, the highest.

use std::collections::BTreeMap;

use rand::rngs::StdRng;

use crate::game::ActionError;
use crate::kopfnuss_config::KopfnussConfig;
use crate::protocol::{AnswerColor, ChoiceOption, Climber, PhoneScreen, PlayerId, ServerTime};
use crate::quiz_config::{Question, Round};
use crate::quiz_views::rank_of;
use crate::room_seats::Player;

#[derive(Debug, Clone, Copy)]
pub(crate) struct Seat {
    pub step: u8,
    /// Index of this player's current question in the shared deck.
    pub idx: usize,
    pub opens_at: ServerTime,
    /// When the current step was reached; the earlier wins a tie.
    pub reached_at: ServerTime,
}

pub(crate) struct Climb {
    pub(crate) deck: Vec<Round>,
    steps: u8,
    lock_ms: ServerTime,
    gap_ms: ServerTime,
    early_ms: ServerTime,
    step_points: i32,
    pub(crate) ends_at: ServerTime,
    pub(crate) seats: BTreeMap<PlayerId, Seat>,
    pub(crate) winner: Option<PlayerId>,
}

impl Climb {
    pub(crate) fn new(
        cfg: &KopfnussConfig,
        questions: Vec<Question>,
        rng: &mut StdRng,
        players: &[Player],
        now: ServerTime,
    ) -> Self {
        let seats = players
            .iter()
            .map(|p| {
                let (rank, _) = rank_of(players, p.id);
                let start = cfg.head_start.get(usize::from(rank) - 1).copied().unwrap_or(0).min(cfg.steps);
                (p.id, Seat { step: start, idx: 0, opens_at: now, reached_at: now })
            })
            .collect();
        Self {
            deck: questions.into_iter().map(|q| Round::shuffled(q, rng)).collect(),
            steps: cfg.steps,
            lock_ms: cfg.lock_ms,
            gap_ms: cfg.next_gap_ms,
            early_ms: cfg.quiz.early_tolerance_ms,
            step_points: cfg.step_points,
            ends_at: now + cfg.climb_ms,
            seats,
            winner: None,
        }
    }

    pub(crate) fn on_choose(&mut self, player: PlayerId, index: u8, now: ServerTime) -> Result<(), ActionError> {
        if self.winner.is_some() || now >= self.ends_at {
            return Err(ActionError::Ignored);
        }
        let Some(seat) = self.seats.get_mut(&player) else { return Err(ActionError::Ignored) };
        let Some(round) = self.deck.get(seat.idx) else { return Err(ActionError::Ignored) };
        // A phone whose clock runs a little ahead may tap just before the host opens.
        if now < seat.opens_at - self.early_ms {
            return Err(ActionError::Ignored);
        }
        if usize::from(index) >= round.options.len() {
            return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht."));
        }
        seat.idx += 1;
        if index == round.correct {
            seat.step += 1;
            seat.reached_at = now;
            // A beat later, so the second tap of a double tap is dropped, not an answer.
            seat.opens_at = now + self.gap_ms;
            if seat.step >= self.steps {
                self.winner = Some(player);
            }
        } else {
            seat.opens_at = now + self.lock_ms;
        }
        Ok(())
    }

    /// Rings the bell when time is up or every connected phone ran out of questions.
    pub(crate) fn on_tick(&mut self, players: &[Player], now: ServerTime) {
        if self.winner.is_some() {
            return;
        }
        let drained = players
            .iter()
            .filter(|p| p.connected())
            .all(|p| self.seats.get(&p.id).is_none_or(|s| s.idx >= self.deck.len()));
        if now >= self.ends_at || drained {
            self.winner = self
                .seats
                .iter()
                .max_by(|(a_id, a), (b_id, b)| {
                    a.step.cmp(&b.step).then(b.reached_at.total_cmp(&a.reached_at)).then(b_id.cmp(a_id))
                })
                .map(|(&id, _)| id);
        }
    }

    /// Everyone from the top down: the winner, then by step, then who got there first.
    pub(crate) fn standings(&self) -> Vec<PlayerId> {
        let mut order: Vec<(&PlayerId, &Seat)> = self.seats.iter().collect();
        order.sort_by(|(a_id, a), (b_id, b)| {
            let won = |id: &PlayerId| self.winner == Some(*id);
            won(b_id)
                .cmp(&won(a_id))
                .then(b.step.cmp(&a.step))
                .then(a.reached_at.total_cmp(&b.reached_at))
                .then(a_id.cmp(b_id))
        });
        order.into_iter().map(|(&id, _)| id).collect()
    }

    /// Summit bonus: `step_points` per step reached, and from the bottom of
    /// `order` up, anyone not ahead of the player below them is lifted just
    /// past them, so the podium's points always follow the climb.
    pub(crate) fn award(&self, players: &mut [Player], order: &[PlayerId]) {
        let mut below: Option<i32> = None;
        for id in order.iter().rev() {
            let (Some(seat), Some(p)) = (self.seats.get(id), players.iter_mut().find(|p| p.id == *id)) else { continue };
            let climbed = p.score + i32::from(seat.step) * self.step_points;
            p.score = below.map_or(climbed, |b| climbed.max(b + self.step_points));
            below = Some(p.score);
        }
    }

    pub(crate) fn steps(&self) -> u8 {
        self.steps
    }

    pub(crate) fn climbers(&self) -> Vec<Climber> {
        self.seats.iter().map(|(&player, s)| Climber { player, step: s.step }).collect()
    }

    pub(crate) fn phone(&self, player: PlayerId) -> PhoneScreen {
        let seat = self.seats.get(&player);
        let Some((seat, round)) = seat.and_then(|s| self.deck.get(s.idx).map(|r| (s, r))) else {
            return PhoneScreen::Wait { title: "Keine Fragen mehr".into(), text: Some("Warte auf die anderen …".into()) };
        };
        PhoneScreen::Choice {
            prompt: Some(round.text.clone()),
            options: round
                .options
                .iter()
                .zip(AnswerColor::ALL)
                .map(|(label, color)| ChoiceOption { label: label.clone(), color })
                .collect(),
            opens_at: seat.opens_at,
            // No clock: a timed screen locks after one tap, and a tap the host
            // drops would then freeze the phone. The TV shows the climb's clock.
            closes_at: None,
            picked: None,
            tricks: vec![],
        }
    }
}

#[cfg(test)]
#[path = "kopfnuss_climb_tests.rs"]
mod tests;
