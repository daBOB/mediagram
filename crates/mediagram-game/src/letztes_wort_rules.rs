//! Letztes Wort's turn rules: a buzz takes the floor, the host's verdict moves the stick or puts
//! the player out, and the round ends on the hidden clock or when nobody else could take the stick.

use crate::game::{ActionError, GameCtx};
use crate::letztes_wort::{LetztesWortGame, Stage};
use crate::protocol::{PlayerId, ServerTime};
use crate::room_seats::Player;

impl LetztesWortGame {
    pub(crate) fn buzz(&mut self, player: PlayerId, now: ServerTime) -> Result<(), ActionError> {
        let Stage::Open { opens_at } = self.stage else { return Err(ActionError::Ignored) };
        if now < opens_at - self.cfg.early_tolerance_ms || now >= self.time_up_at || !self.may_buzz(player) {
            return Err(ActionError::Ignored);
        }
        self.buzzes += 1;
        self.stage = Stage::Floor { player, since: now };
        Ok(())
    }

    /// The host's verdict: a listed answer, then Falsch, then Auch richtig.
    pub(crate) fn judge(&mut self, ctx: &mut GameCtx, index: u8) -> Result<(), ActionError> {
        let Stage::Floor { player, since } = self.stage else { return Err(ActionError::Ignored) };
        // Nobody judges a spoken answer this fast: it is the late half of a double tap on the last one.
        if ctx.now < since + self.cfg.verdict_gap_ms {
            return Err(ActionError::Ignored);
        }
        let (index, listed) = (usize::from(index), self.prompt.answers.len());
        let right = match index {
            // A repeat is no new answer.
            i if i < listed => (!self.is_given(i)).then_some(Some(i)),
            i if i == listed => None,
            i if i == listed + 1 => Some(None),
            _ => return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht.")),
        };
        match right {
            Some(answer) => {
                self.given.push((answer, player));
                self.holder = Some(player);
            }
            None => {
                self.out.insert(player);
            }
        }
        let now = ctx.now;
        let all_given = (0..listed).all(|i| self.is_given(i));
        let anyone = ctx.players.iter().any(|p| p.connected() && self.may_buzz(p.id));
        if now >= self.time_up_at || all_given || !anyone {
            self.end_round(ctx.players, now);
        } else {
            self.stage = Stage::Open { opens_at: now + self.cfg.verdict_gap_ms };
        }
        Ok(())
    }

    pub(crate) fn end_round(&mut self, players: &mut [Player], now: ServerTime) {
        if let Some(p) = self.holder.and_then(|w| players.iter_mut().find(|p| p.id == w)) {
            p.score += 1;
        }
        self.stage = Stage::RoundEnd { winner: self.holder, ends_at: now + self.cfg.round_end_ms };
    }

    /// The first present player in seat order from `from` (itself included unless `skip`).
    pub(crate) fn present_from(players: &[Player], from: PlayerId, skip: bool) -> Option<PlayerId> {
        let at = players.iter().position(|p| p.id == from)?;
        (usize::from(skip)..players.len()).map(|k| &players[(at + k) % players.len()]).find(|p| p.connected()).map(|p| p.id)
    }
}
