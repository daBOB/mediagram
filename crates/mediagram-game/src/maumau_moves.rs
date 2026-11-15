//! The moves of a Mau-Mau turn: drawing, playing, wishing and finishing, and the
//! draw pile they share.

use crate::ask::Ask;
use crate::card_table::sort_hand;
use crate::cards::{Card, Rank, Suit, shuffled};
use crate::game::GameCtx;
use crate::maumau::{MauMauGame, Stage};
use crate::maumau_rules::{SEVEN_DRAWS, ranking};
use crate::protocol::{CardsEventKind, PlayerId, ServerTime};

const MAU: &str = " ruft Mau!";
const PENALTY_START: &str = " vergisst Mau";
const PENALTY: &str = " vergisst Mau – Strafkarte!";

/// What the banner says after a forced draw of whatever number of cards was really dealt.
fn drawn_text(n: usize) -> String {
    match n {
        0 => " zieht keine Karte".into(),
        1 => " zieht eine Karte".into(),
        n => format!(" zieht {n} Karten"),
    }
}

impl MauMauGame {
    pub(crate) fn advance(&mut self, now: ServerTime, steps: usize) {
        self.turn = (self.turn + steps) % self.table.players().len();
        self.stage = Stage::Turn { ask: self.table.ask(self.turn, now) };
    }

    /// Next card of the draw pile; an empty pile is refilled from the discards under the top card.
    pub(crate) fn draw_one(&mut self, now: ServerTime, player: PlayerId) -> Option<Card> {
        if self.deck.is_empty() && self.discard.len() > 1 {
            let top = self.discard.pop().expect("checked above");
            self.deck = shuffled(std::mem::replace(&mut self.discard, vec![top]), &mut self.rng);
            self.table.event(now, player, CardsEventKind::Reshuffle);
        }
        self.deck.pop()
    }

    /// Deals up to `n` cards into a hand, as one event; returns the cards actually dealt.
    pub(crate) fn give(&mut self, now: ServerTime, seat: usize, n: u8) -> Vec<Card> {
        let player = self.table.player_at(seat);
        let mut dealt = Vec::new();
        for _ in 0..n {
            let Some(card) = self.draw_one(now, player) else { break };
            self.hands[seat].push(card);
            dealt.push(card);
        }
        if !dealt.is_empty() {
            sort_hand(&mut self.hands[seat]);
            self.table.event(now, player, CardsEventKind::Draw { count: dealt.len() as u8 });
        }
        dealt
    }

    pub(crate) fn draw(&mut self, now: ServerTime) {
        let (seat, player) = (self.turn, self.table.player_at(self.turn));
        if self.owed > 0 {
            let n = std::mem::take(&mut self.owed);
            let drawn = self.give(now, seat, n).len();
            self.table.say(now, player, drawn_text(drawn));
            return self.advance(now, 1);
        }
        // The turn pauses on every drawn card, so nobody can tell from outside whether it fits.
        match self.give(now, seat, 1).first() {
            Some(&card) => self.stage = Stage::Drawn { card, ask: self.table.ask(seat, now) },
            None => self.advance(now, 1),
        }
        self.table.say(now, player, " zieht eine Karte");
    }

    pub(crate) fn play(&mut self, ctx: &mut GameCtx, index: usize, mau: bool) {
        let now = ctx.now;
        let seat = self.turn;
        let player = self.table.player_at(seat);
        let card = self.hands[seat].remove(index);
        self.discard.push(card);
        self.wish = None;
        self.suit = Some(card.suit);
        self.table.event(now, player, CardsEventKind::Play { card, pile: 0 });
        if self.hands[seat].is_empty() {
            return self.finish(ctx, seat);
        }
        match card.rank {
            Rank::Seven => {
                self.owed += SEVEN_DRAWS;
                self.advance(now, 1);
            }
            Rank::Eight => {
                self.table.say(now, self.table.player_at((seat + 1) % self.table.players().len()), " setzt aus");
                self.advance(now, 2);
            }
            Rank::Jack => self.stage = Stage::Wish { ask: self.table.ask(seat, now) },
            _ => self.advance(now, 1),
        }
        if self.hands[seat].len() == 1 {
            if mau {
                self.table.say(now, player, MAU);
            } else {
                // the old top card is still under the played one, so the pile never runs dry here
                self.give(now, seat, 1);
                self.table.say(now, player, PENALTY);
            }
        }
    }

    pub(crate) fn finish(&mut self, ctx: &mut GameCtx, winner: usize) {
        let order = ranking(winner, &self.hands);
        for (place, &seat) in order.iter().enumerate() {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == self.table.player_at(seat)) {
                p.score = (order.len() - 1 - place) as i32;
            }
        }
        self.table.say(ctx.now, self.table.player_at(winner), " gewinnt!");
        self.stage = Stage::Final { ranking: order };
    }

    pub(crate) fn choose_suit(&mut self, now: ServerTime, suit: Suit) {
        self.suit = Some(suit);
        self.wish = Some(suit);
        let wish = format!(" wünscht {}", suit.name());
        let player = self.table.player_at(self.turn);
        // Mau, or the penalty for forgetting it, named a moment ago stays up its full time; the wish follows.
        if self.table.said(now).is_some_and(|b| b.text == MAU || b.text.starts_with(PENALTY_START)) {
            self.table.say_next(now, player, wish);
        } else {
            self.table.say(now, player, wish);
        }
        self.advance(now, 1);
    }

    pub(crate) fn index_of(&self, card: Card) -> usize {
        self.hands[self.turn].iter().position(|&c| c == card).expect("the drawn card is in hand")
    }

    /// What the player whose turn it is is being asked; nothing once the game is over.
    pub(crate) fn open_ask(&self) -> Option<Ask> {
        match self.stage {
            Stage::Turn { ask } | Stage::Drawn { ask, .. } | Stage::Wish { ask } => Some(ask),
            Stage::Final { .. } => None,
        }
    }
}
