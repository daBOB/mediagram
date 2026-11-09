//! What each answer of the mover does, and the building that follows a permit.

use super::board::HOTELS;
use super::live::{GrandAlleeGame, Stage};
use super::live_asks::{Ask, Opt};
use super::live_debts::Debt;
use super::live_turn::Step;
use super::rules::{permit_for, Permit};
use super::live_text::taler;
use crate::game::GameCtx;
use crate::protocol::ServerTime;

impl GrandAlleeGame {
    /// Does what `opt` says; `step` is where the move goes on from the question it answered.
    pub(crate) fn choose(&mut self, ctx: &GameCtx, opt: Opt, step: Step) {
        let (now, s) = (ctx.now, self.turn);
        let later = now + self.cfg.chain_ms;
        let (who, price) = (self.who(ctx.players, s), opt.price(&self.t));
        let hotel = |h: u8| HOTELS[usize::from(h)].name;
        match opt {
            Opt::Roll => {
                let value = self.die();
                self.dice.die = Some(value);
                self.stage = Stage::Rolled { value, at: now };
            }
            Opt::EndTurn => self.next_turn(now),
            Opt::Leisure(h) => {
                self.t.build(s, h, 1, price);
                let ask = self.roll_ask();
                self.ask(later, ask, Step::Done);
            }
            Opt::Buy(h) => {
                self.t.buy(s, h);
                self.end_move(now, Some(format!("{who} kauft {}.", hotel(h))));
            }
            Opt::Force(h) => {
                let owner = self.t.estate(h).owner.map_or_else(String::new, |o| self.who(ctx.players, o));
                self.t.forced_buy(s, h);
                self.end_move(now, Some(format!("{who} übernimmt {} von {owner} für {}.", hotel(h), taler(price))));
            }
            Opt::Build { hotel, count } => {
                let permit = permit_for(self.die());
                self.dice.permit = Some(permit);
                self.stage = Stage::Permit { hotel, count, permit, at: now };
            }
            Opt::Hotel { hotel, free } => {
                let ask = self.place_ask(hotel, free, Some(if free { Opt::Skip } else { Opt::Back }));
                self.ask(later, ask, step);
            }
            Opt::Place { hotel: h, field, free } => {
                self.t.place_entrance(s, h, field, price);
                if free {
                    self.end_move(now, Some(format!("{who} setzt einen Eingang für {} auf Feld {}.", hotel(h), field + 1)));
                } else {
                    self.rathaus_menu(now, [step.served(), &[h]].concat());
                }
            }
            Opt::FreeStage(h) => {
                let first = self.t.build(s, h, 1, 0);
                self.built(ctx, h, first);
            }
            Opt::Sell(h) => {
                let owing = match step {
                    Step::Owing(debt) => Some(debt),
                    Step::Rathaus(_) | Step::Done => None,
                };
                self.open_auction(now, h, owing);
            }
            Opt::Raise { hotel, count } => {
                let price = self.t.stage_price(hotel, count, Permit::Double);
                self.settle(ctx, Debt::Permit { hotel, count, price });
            }
            Opt::Skip => self.end_move(later, None),
            Opt::Done => self.go_to_guest(later),
            Opt::Back => self.rathaus_menu(now, step.served().to_vec()),
        }
    }

    /// Back to the Rathaus menu while it offers something, else on to the guest.
    fn rathaus_menu(&mut self, now: ServerTime, served: Vec<u8>) {
        let later = now + self.cfg.chain_ms;
        match self.rathaus_ask(&served) {
            Some(ask) => self.ask(later, ask, Step::Rathaus(served)),
            None => self.go_to_guest(later),
        }
    }

    pub(crate) fn permit_lands(&mut self, ctx: &GameCtx, hotel: u8, count: u8, permit: Permit) {
        let (now, s) = (ctx.now, self.turn);
        if permit == Permit::Rejected {
            return self.end_move(now, Some(format!("{}: Genehmigung abgelehnt.", self.who(ctx.players, s))));
        }
        let price = self.t.stage_price(hotel, count, permit);
        if price > self.t.seats[s].cash {
            // a granted permit must be used: the mover auctions the hotel in question, and the
            // permit costs nothing, or auctions other properties until it can be paid
            if self.t.owned(s).len() < 2 {
                return self.open_auction(now, hotel, None);
            }
            let prompt = format!("Doppelt: {} kostet {} – oder du versteigerst es.", HOTELS[usize::from(hotel)].name, taler(price));
            return self.ask(now, Ask { prompt, hotel: Some(hotel), opts: vec![Opt::Sell(hotel), Opt::Raise { hotel, count }] }, Step::Done);
        }
        let first = self.t.build(s, hotel, count, price);
        self.built(ctx, hotel, first);
    }

    /// The first stage brings a free entrance, on a frontage field the builder picks.
    pub(crate) fn built(&mut self, ctx: &GameCtx, hotel: u8, first: bool) {
        if first && !self.t.free_frontage(hotel).is_empty() {
            let ask = self.place_ask(hotel, true, None);
            return self.ask(ctx.now + self.cfg.chain_ms, ask, Step::Done);
        }
        let (h, levels) = (&HOTELS[usize::from(hotel)], self.t.estate(hotel).levels);
        let text = format!("{} baut {}: Stufe {levels} von {}.", self.who(ctx.players, self.turn), h.name, h.stages.len());
        self.end_move(ctx.now, Some(text));
    }
}
