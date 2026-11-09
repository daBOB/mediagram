//! Paying in Grand Allee: a mover short of cash puts properties of their
//! choice up for debt auctions until the debt is covered. Each auction runs
//! on every other phone at once, sealed; the bank buys what nobody bids on.

use super::live::{Auction, Bid, GrandAlleeGame, Stage};
use super::money::best_bid;
use crate::game::{ActionError, GameCtx};
use crate::protocol::ServerTime;

/// Bids go in whole hundreds.
pub(crate) const BID_STEP: i32 = 100;

impl GrandAlleeGame {
    pub(crate) fn owe(&mut self, ctx: &GameCtx, to: Option<usize>, amount: i32) {
        self.debt = Some((to, amount));
        self.settle(ctx);
    }

    /// Pays the mover's debt once they can, asking what to auction while they
    /// cannot; with nothing left to sell they pay what they have and are out.
    /// Without a debt the move goes on.
    pub(crate) fn settle(&mut self, ctx: &GameCtx) {
        let (now, s) = (ctx.now, self.turn);
        let Some((to, amount)) = self.debt else {
            return self.proceed(now);
        };
        let short = self.t.seats[s].cash < amount;
        if short && !self.sellable().is_empty() {
            let ask = self.sell_ask(amount);
            return self.ask(now, ask);
        }
        self.debt = None;
        if let Some((hotel, count)) = self.building.take() {
            // nothing else left to sell: the hotel in question goes after all, and the permit costs nothing
            if short {
                return self.open_auction(now, hotel);
            }
            let first = self.t.build(s, hotel, count, amount);
            return self.built(ctx, hotel, first);
        }
        if self.t.pay(s, to, amount) {
            return self.proceed(now);
        }
        self.gone.push(s);
        let text = format!("{} kann nicht zahlen und ist raus.", self.who(ctx.players, s));
        self.stage = Stage::Note { text, broke: true, at: now };
    }

    pub(crate) fn open_auction(&mut self, now: ServerTime, hotel: u8) {
        let bids = self.t.bidders(self.turn).into_iter().map(|b| (b, Bid::Open)).collect();
        self.stage = Stage::Auction(Auction { hotel, since: now, bids });
    }

    /// The highest bid `seat` may make: its cash in whole steps (no minimum).
    pub(crate) fn bid_max(&self, seat: usize) -> i32 {
        self.t.seats[seat].cash / BID_STEP * BID_STEP
    }

    /// A bidder's sealed answer: a whole number of steps up to their cash, or 0 to pass.
    pub(crate) fn bid(&mut self, seat: usize, amount: i32, at: ServerTime) -> Result<(), ActionError> {
        let Stage::Auction(a) = &self.stage else {
            return Err(ActionError::Ignored);
        };
        if at < a.since - self.cfg.early_ms {
            return Err(ActionError::Ignored);
        }
        let bid = match amount {
            0 => Bid::Pass,
            x if x > 0 && x % BID_STEP == 0 && x <= self.bid_max(seat) => Bid::Amount(x),
            _ => return Err(ActionError::NotAllowed("So viel hast du nicht.")),
        };
        let Stage::Auction(a) = &mut self.stage else {
            return Err(ActionError::Ignored);
        };
        // sealed and final: a second tap changes nothing
        let slot = a.bids.iter_mut().find(|(b, answer)| *b == seat && *answer == Bid::Open).ok_or(ActionError::Ignored)?;
        slot.1 = bid;
        Ok(())
    }

    /// Closes once every bidder still at the table has answered, or when the
    /// clock runs out; whoever stayed silent passed.
    pub(crate) fn auction_tick(&mut self, ctx: &GameCtx, a: Auction) {
        let here = |seat: usize| ctx.players.iter().any(|p| p.id == self.ids[seat] && p.connected());
        let waiting = a.bids.iter().any(|&(b, answer)| answer == Bid::Open && here(b));
        if waiting && ctx.now < a.since + self.cfg.auction_ms {
            return;
        }
        let amounts = a.bids.iter().filter_map(|&(b, answer)| match answer {
            Bid::Amount(x) => Some((x, b)),
            Bid::Open | Bid::Pass => None,
        });
        let best = best_bid(amounts);
        let price = self.t.auction_price(a.hotel, best);
        self.t.settle_auction(self.turn, a.hotel, best);
        self.stage = Stage::Sold { hotel: a.hotel, winner: best.map(|(_, w)| w), price, at: ctx.now };
    }
}
