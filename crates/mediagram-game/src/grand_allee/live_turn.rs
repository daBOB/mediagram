//! Grand Allee's turn flow: what follows what, and what each answer does.

use super::board::FIELDS;
use super::live::{GrandAlleeGame, Stage, HOUSE};
use super::live_asks::Ask;
use super::live_debts::Debt;
use super::live_text::Phrase;
use crate::game::{GameCtx, name};
use crate::protocol::ServerTime;
use crate::room_seats::Player;

/// Where a move goes on after a question, beyond what its answer says.
#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Step {
    /// The Rathaus menu, with the hotels that got an entrance on this pass; the guest comes next.
    Rathaus(Vec<u8>),
    /// A debt the mover is short of: the property they pick is auctioned toward it.
    Owing(Debt),
    /// Nothing waits on the answer: it says itself what follows.
    Done,
}

impl Step {
    /// The hotels served on this pass of the Rathaus; none outside its menu.
    pub(crate) fn served(&self) -> &[u8] {
        match self {
            Step::Rathaus(served) => served,
            Step::Owing(_) | Step::Done => &[],
        }
    }
}

impl GrandAlleeGame {
    pub(crate) fn who(&self, players: &[Player], seat: usize) -> String {
        name(players, self.ids[seat]).into()
    }

    pub(crate) fn ask(&mut self, now: ServerTime, ask: Ask, step: Step) {
        self.stage = Stage::Ask { ask, since: now, step };
    }

    /// When the house decides an ask opened at `since`: when its clock runs
    /// out, or the grace after an absent mover left. Once a clock ran out this
    /// turn the house goes on at once, so a phone left on the table costs one
    /// clock per turn, not one per follow-up question.
    pub(crate) fn house_at(&self, ctx: &GameCtx, since: ServerTime) -> ServerTime {
        if self.idle {
            return since;
        }
        let clock = since + self.cfg.ask_ms;
        clock.min(ctx.house_at(self.ids[self.turn], since).unwrap_or(clock))
    }

    /// When the current stage acts: the house answers an ask, a die lands, a
    /// walk ends, an auction closes, a note goes; `None` once the game is over.
    /// `tick` acts only once this time has come.
    pub(crate) fn due(&self, ctx: &GameCtx) -> Option<ServerTime> {
        Some(match &self.stage {
            Stage::Intro { at, .. } => at + self.cfg.intro_ms,
            Stage::Ask { since, .. } => self.house_at(ctx, *since),
            Stage::Rolled { at, .. } | Stage::Guest { at, .. } | Stage::Permit { at, .. } => at + self.cfg.roll_ms + self.cfg.show_ms,
            Stage::Walking { ends_at, .. } => *ends_at,
            Stage::Auction(a) => self.auction_close(ctx, a),
            Stage::Sold { at, .. } | Stage::Note { at, .. } => at + self.cfg.note_ms,
            Stage::Final => return None,
        })
    }

    pub(crate) fn tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        if self.due(ctx).is_none_or(|due| now < due) {
            return;
        }
        match self.stage.clone() {
            Stage::Intro { card, .. } => self.next_card(now, card),
            Stage::Ask { ask, since, step } => {
                let opt = ask.opts[HOUSE.pick(&self.t, self.turn, &ask)];
                let by = self.silent_by(ctx, since);
                self.log_decision(ask.kind(), since, opt, now, by);
                self.idle = true;
                self.choose(ctx, opt, step);
            }
            Stage::Rolled { value, .. } => self.walk(now, value),
            Stage::Walking { rathaus, .. } => self.after_walk(now, rathaus),
            Stage::Guest { owner, amount, .. } => self.settle(ctx, Debt::Rent { owner, amount }),
            Stage::Permit { hotel, count, permit, .. } => self.permit_lands(ctx, hotel, count, permit),
            Stage::Auction(a) => self.close_auction(ctx, a),
            Stage::Sold { owing: Some(debt), .. } => self.settle(ctx, debt),
            Stage::Sold { owing: None, .. } | Stage::Note { .. } => self.after_move(now),
            Stage::Final => {}
        }
    }

    fn walk(&mut self, now: ServerTime, value: u8) {
        let from = self.t.seats[self.turn].field;
        let walk = self.t.walk(self.turn, value);
        self.six = value == 6;
        // occupied fields are passed over, so the pawn may hop further than the die says
        let hops = (walk.to + FIELDS - from) % FIELDS;
        let at = now + self.cfg.lead_ms;
        let ends_at = at + f64::from(hops) * self.cfg.hop_ms;
        self.stage = Stage::Walking { value, from, at, ends_at, bank: walk.bank_paid, rathaus: walk.passed_rathaus };
    }

    /// After the walk the move settles in the design's order, Rathaus, guest,
    /// field: the Rathaus menu when the walk passed it and it offers something.
    fn after_walk(&mut self, now: ServerTime, rathaus: bool) {
        match rathaus.then(|| self.rathaus_ask(&[])).flatten() {
            Some(ask) => self.ask(now, ask, Step::Rathaus(Vec::new())),
            None => self.go_to_guest(now),
        }
    }

    /// A stay at the hotel whose entrance the mover stopped on, else the field.
    pub(crate) fn go_to_guest(&mut self, now: ServerTime) {
        let s = self.turn;
        let Some((owner, hotel)) = self.t.host_at(s, self.t.seats[s].field) else {
            return self.go_to_field(now);
        };
        let nights = self.die();
        self.dice.nights = Some(nights);
        let amount = i32::from(nights) * self.t.rate(hotel);
        self.stage = Stage::Guest { owner, hotel, nights, amount, at: now };
    }

    /// What the field offers, else the move ends.
    pub(crate) fn go_to_field(&mut self, now: ServerTime) {
        match self.field_ask() {
            Some(ask) => self.ask(now, ask, Step::Done),
            None => self.end_move(now, None),
        }
    }

    pub(crate) fn end_move(&mut self, now: ServerTime, note: Option<Phrase>) {
        match note {
            Some(text) => self.stage = Stage::Note { text, broke: false, at: now },
            None => self.after_move(now),
        }
    }

    /// After a 6 the mover may roll again, once everything is settled; otherwise the next player.
    fn after_move(&mut self, now: ServerTime) {
        if self.six && !self.t.seats[self.turn].out && self.t.active() > 1 {
            let ask = self.roll_ask();
            self.ask(now, ask, Step::Done);
        } else {
            self.next_turn(now);
        }
    }

    /// Hands over to the next player still in; ends the game after the round
    /// limit or when one player is left.
    pub(crate) fn next_turn(&mut self, now: ServerTime) {
        // the round the move that just ended belonged to
        let (n, played) = (self.t.seats.len(), self.round);
        let mut next = self.turn;
        loop {
            next += 1;
            if next == n {
                next = 0;
                self.round += 1;
            }
            if !self.t.seats[next].out || self.t.active() <= 1 {
                break;
            }
        }
        if self.round > self.rounds || self.t.active() <= 1 {
            self.stage = Stage::Final;
            return self.log_end(now, played);
        }
        self.begin_move(next, now);
    }

    /// Starts `seat`'s move with the roll question. The extra roll after a 6
    /// belongs to the same move, so it does not come through here.
    pub(crate) fn begin_move(&mut self, seat: usize, now: ServerTime) {
        (self.turn, self.six, self.idle) = (seat, false, false);
        let ask = self.roll_ask();
        self.ask(now, ask, Step::Done);
    }

    pub(crate) fn sync_scores(&self, players: &mut [Player]) {
        for (s, &id) in self.ids.iter().enumerate() {
            if let Some(p) = players.iter_mut().find(|p| p.id == id) {
                p.score = self.t.net_worth(s);
            }
        }
    }
}
