//! The moves of a Tiến Lên turn: laying a play, passing, and who is asked next.

use crate::cards::Card;
use crate::game::GameCtx;
use crate::protocol::{CardsEventKind, ServerTime};
use crate::tienlen::{Stage, TienLenGame, Top};
use crate::tienlen_rules::{Combo, sort_hand};

/// The cards of one play land one after another, so each is seen and heard.
const LAY_GAP_MS: ServerTime = 70.0;

impl TienLenGame {
    /// Lays `cards`, which make `made`, for the player in turn.
    pub(crate) fn play(&mut self, ctx: &mut GameCtx, mut cards: Vec<Card>, made: Combo) {
        let (now, seat) = (ctx.now, self.turn);
        let player = self.table.player_at(seat);
        sort_hand(&mut cards);
        self.hands[seat].retain(|c| !cards.contains(c));
        for (i, &card) in cards.iter().enumerate() {
            self.table.event(now + i as ServerTime * LAY_GAP_MS, player, CardsEventKind::Play { card, pile: 0 });
        }
        // only a bomb answers a play with another kind
        if self.top.as_ref().is_some_and(|top| top.combo.kind != made.kind) {
            self.table.say(now, player, " legt eine Bombe!");
        }
        self.opening = None;
        self.passed[seat] = false;
        self.top = Some(Top { cards, combo: made, owner: seat });
        if self.hands[seat].is_empty() {
            self.out.push(seat);
            if self.hands.iter().filter(|h| !h.is_empty()).count() < 2 {
                return self.finish(ctx);
            }
            self.table.say(now, player, format!(" ist fertig – Platz {}", self.out.len()));
        }
        self.next(now, seat);
    }

    pub(crate) fn pass(&mut self, now: ServerTime) {
        let seat = self.turn;
        self.passed[seat] = true;
        self.table.say(now, self.table.player_at(seat), " passt");
        self.next(now, seat);
    }

    /// Asks the next player after `from` who may still answer the play on the
    /// table. The turn stops at the play's owner: once it has gone round
    /// unbeaten, the table is cleared and the owner leads, or the next player
    /// with cards if the owner went out on it.
    fn next(&mut self, now: ServerTime, from: usize) {
        let n = self.hands.len();
        let after = |seat: usize| (1..n).map(move |k| (seat + k) % n);
        let top = self.top.as_ref().expect("a play is on the table");
        // twos call back those who passed, if they hold cards enough for a bomb: the count is open, the hand is not
        let (owner, bomb) = (top.owner, top.combo.twos().then(|| top.combo.bomb_len()));
        let answers = |seat: &usize| {
            let held = self.hands[*seat].len();
            held > 0 && (!self.passed[*seat] || bomb.is_some_and(|len| held >= len))
        };
        if let Some(seat) = after(from).take_while(|&seat| seat != owner).find(answers) {
            self.turn = seat;
            self.stage = Stage::Turn { ask: self.table.ask(seat, now) };
            return;
        }
        self.turn = std::iter::once(owner).chain(after(owner)).find(|&seat| !self.hands[seat].is_empty()).expect("two players still hold cards");
        // a play laid when nobody could answer any more has not been seen yet: it lies on show first
        if from == owner { self.stage = Stage::Settle { since: now } } else { self.clear(now) }
    }

    /// Clears the table for the player in turn to lead.
    pub(crate) fn clear(&mut self, now: ServerTime) {
        self.top = None;
        self.passed.fill(false);
        self.stage = Stage::Turn { ask: self.table.ask(self.turn, now) };
    }

    /// Ranks the players in the order they went out, the one left holding cards last.
    fn finish(&mut self, ctx: &mut GameCtx) {
        let last = (0..self.hands.len()).filter(|seat| !self.out.contains(seat));
        let order: Vec<usize> = self.out.iter().copied().chain(last).collect();
        for (place, &seat) in order.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.table.player_at(seat)) {
                p.score = (order.len() - 1 - place) as i32;
            }
        }
        self.table.say(ctx.now, self.table.player_at(order[0]), " gewinnt!");
        self.stage = Stage::Final { ranking: order };
    }
}
