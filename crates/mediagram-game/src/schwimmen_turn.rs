//! One turn of Schwimmen: the four moves and what follows each of them.

use crate::game::ActionError;
use crate::protocol::{CardsEventKind, ServerTime};
use crate::maumau_rules::sort_hand;
use crate::schwimmen::{SchwimmenGame, Stage};
use crate::schwimmen_rules::{FEUER, HAND, Swap, apply, value};

impl SchwimmenGame {
    pub(crate) fn swap(&mut self, now: ServerTime, swap: Swap) {
        let (seat, player) = (self.turn, self.player_at(self.turn));
        let before = self.hands[seat].clone();
        apply(&mut self.hands[seat], &mut self.middle, swap);
        sort_hand(&mut self.hands[seat]);
        // each card that went down flies to the middle slot it took
        let slots: Vec<usize> = match swap {
            Swap::One { middle, .. } => vec![middle],
            Swap::All => (0..HAND).collect(),
        };
        for (k, &slot) in slots.iter().enumerate() {
            let card = match swap {
                Swap::One { hand, .. } => before[hand],
                Swap::All => before[k],
            };
            self.event(now, player, CardsEventKind::Play { card, pile: slot as u8 });
        }
        self.passes = 0;
        self.say(now, player, if slots.len() == 1 { " tauscht eine Karte" } else { " tauscht alle drei Karten" });
        self.end_turn(now);
    }

    pub(crate) fn pass(&mut self, now: ServerTime) {
        let player = self.player_at(self.turn);
        self.passes += 1;
        self.say(now, player, " schiebt");
        if self.knocker.is_none() && self.passes >= self.alive().len() {
            // everyone passed: the old middle goes under the deck, three new cards are turned up
            let fresh = self.deck.split_off(self.deck.len() - HAND);
            let old = std::mem::replace(&mut self.middle, fresh);
            self.deck.splice(0..0, old);
            self.passes = 0;
            self.say(now, player, " schiebt – neue Karten in der Mitte");
        }
        self.end_turn(now);
    }

    pub(crate) fn knock(&mut self, now: ServerTime) {
        self.knocker = Some(self.turn);
        self.say(now, self.player_at(self.turn), " klopft!");
        self.end_turn(now);
    }

    /// 31 ends the round now; otherwise the next player moves, and the knocker's turn coming round again is the reveal.
    pub(crate) fn end_turn(&mut self, now: ServerTime) {
        let seat = self.turn;
        if value(&self.hands[seat]) == FEUER {
            return self.reveal_round(now);
        }
        let next = self.after(seat);
        if self.knocker == Some(next) {
            return self.reveal_round(now);
        }
        self.turn = next;
        self.stage = Stage::Turn { since: now };
    }

    pub(crate) fn pick_swap(&self, hand: &[u8], table: Option<u8>) -> Result<Swap, ActionError> {
        let [h] = hand[..] else { return Err(ActionError::NotAllowed("Wähle eine Handkarte und eine Karte aus der Mitte.")) };
        let m = table.ok_or(ActionError::NotAllowed("Wähle eine Handkarte und eine Karte aus der Mitte."))?;
        let (h, m) = (usize::from(h), usize::from(m));
        if h >= self.hands[self.turn].len() || m >= self.middle.len() {
            return Err(ActionError::Ignored);
        }
        Ok(Swap::One { hand: h, middle: m })
    }
}
