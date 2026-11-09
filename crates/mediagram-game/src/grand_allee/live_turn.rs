//! Grand Allee's turn flow: what follows what, and what each answer does.

use super::board::FIELDS;
use super::live::{GrandAlleeGame, Stage, Step};
use super::live_asks::Ask;
use crate::game::{GameCtx, name};
use crate::protocol::ServerTime;
use crate::room_seats::Player;

impl GrandAlleeGame {
    pub(crate) fn who(&self, players: &[Player], seat: usize) -> String {
        name(players, self.ids[seat]).into()
    }

    pub(crate) fn ask(&mut self, now: ServerTime, ask: Ask) {
        self.stage = Stage::Ask { ask, since: now };
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

    pub(crate) fn tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        let landed = |at: ServerTime| now >= at + self.cfg.roll_ms + self.cfg.show_ms;
        match self.stage.clone() {
            Stage::Ask { ask, since } if now >= self.house_at(ctx, since) => {
                let pick = self.auto_index(&ask);
                self.idle = true;
                self.choose(ctx, ask.opts[pick]);
            }
            Stage::Rolled { value, at } if landed(at) => self.walk(now, value),
            Stage::Walking { ends_at, .. } if now >= ends_at => self.proceed(now),
            Stage::Guest { owner, amount, at, .. } if landed(at) => self.owe(ctx, Some(owner), amount),
            Stage::Permit { hotel, count, permit, at } if landed(at) => self.permit_lands(ctx, hotel, count, permit),
            Stage::Auction(a) => self.auction_tick(ctx, a),
            Stage::Sold { at, .. } if now >= at + self.cfg.note_ms => self.settle(ctx),
            Stage::Note { at, .. } if now >= at + self.cfg.note_ms => self.after_move(now),
            _ => {}
        }
    }

    fn walk(&mut self, now: ServerTime, value: u8) {
        let from = self.t.seats[self.turn].field;
        let walk = self.t.walk(self.turn, value);
        (self.roll, self.rathaus, self.step) = (value, walk.passed_rathaus, Step::Rathaus);
        self.served.clear();
        // occupied fields are passed over, so the pawn may hop further than the die says
        let hops = (walk.to + FIELDS - from) % FIELDS;
        let at = now + self.cfg.lead_ms;
        self.stage = Stage::Walking { value, from, at, ends_at: at + f64::from(hops) * self.cfg.hop_ms, bank: walk.bank_paid };
    }

    /// Settles the rest of the move in the design's order: Rathaus, guests, the field.
    pub(crate) fn proceed(&mut self, now: ServerTime) {
        let s = self.turn;
        loop {
            match self.step {
                Step::Rathaus => {
                    self.step = Step::Guest;
                    if let Some(ask) = self.rathaus.then(|| self.rathaus_ask()).flatten() {
                        return self.ask(now, ask);
                    }
                }
                Step::Guest => {
                    self.step = Step::Field;
                    if let Some((owner, hotel)) = self.t.host_at(s, self.t.seats[s].field) {
                        let nights = self.die();
                        self.dice.nights = Some(nights);
                        let amount = i32::from(nights) * self.t.rate(hotel);
                        self.stage = Stage::Guest { owner, hotel, nights, amount, at: now };
                        return;
                    }
                }
                Step::Field => {
                    self.step = Step::Done;
                    if let Some(ask) = self.field_ask() {
                        return self.ask(now, ask);
                    }
                }
                Step::Done => return self.end_move(now, None),
            }
        }
    }

    pub(crate) fn end_move(&mut self, now: ServerTime, note: Option<String>) {
        match note {
            Some(text) => self.stage = Stage::Note { text, broke: false, at: now },
            None => self.after_move(now),
        }
    }

    /// After a 6 the mover may roll again, once everything is settled; otherwise the next player.
    fn after_move(&mut self, now: ServerTime) {
        if self.roll == 6 && !self.t.seats[self.turn].out && self.t.active() > 1 {
            let ask = self.roll_ask();
            self.ask(now, ask);
        } else {
            self.next_turn(now);
        }
    }

    /// Hands over to the next player still in; ends the game after the round
    /// limit or when one player is left.
    pub(crate) fn next_turn(&mut self, now: ServerTime) {
        let n = self.t.seats.len();
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
            return;
        }
        (self.turn, self.roll, self.idle) = (next, 0, false);
        let ask = self.roll_ask();
        self.ask(now, ask);
    }

    pub(crate) fn sync_scores(&self, players: &mut [Player]) {
        for (s, &id) in self.ids.iter().enumerate() {
            if let Some(p) = players.iter_mut().find(|p| p.id == id) {
                p.score = self.t.net_worth(s);
            }
        }
    }
}
