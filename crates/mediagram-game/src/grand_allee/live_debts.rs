//! Paying in Grand Allee: a mover short of cash puts properties of their
//! choice up for debt auctions until the debt is covered. Each auction runs
//! on every other phone at once, sealed; the bank buys what nobody bids on.

use super::live::{Auction, Bid, GrandAlleeGame, Stage};
use super::money::best_bid;
use crate::game::{ActionError, GameCtx};
use crate::protocol::ServerTime;

/// Bid buttons as shares of the property's value.
// shortcut: a few fixed shares instead of a free amount in steps of 100; the phone controls get a real bid input later
const BID_PCTS: [i32; 5] = [10, 25, 50, 75, 100];

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
        if self.t.seats[s].cash < amount && !self.t.owned(s).is_empty() {
            let ask = self.sell_ask(amount);
            return self.ask(now, ask);
        }
        self.debt = None;
        if self.t.pay(s, to, amount) {
            return self.proceed(now);
        }
        self.gone.push(s);
        let text = format!("{} kann nicht zahlen und ist raus.", self.who(ctx.players, s));
        self.end_move(now, Some(text));
    }

    pub(crate) fn open_auction(&mut self, now: ServerTime, hotel: u8) {
        let bids = self.t.bidders(self.turn).into_iter().map(|b| (b, Bid::Open)).collect();
        self.stage = Stage::Auction(Auction { hotel, since: now, bids });
    }

    /// What `seat` may bid: multiples of 100 up to its cash, no minimum.
    pub(crate) fn bid_amounts(&self, seat: usize, hotel: u8) -> Vec<i32> {
        let (value, cash) = (self.t.value(hotel), self.t.seats[seat].cash);
        let mut amounts: Vec<i32> = BID_PCTS.iter().map(|pct| (value * pct / 100).min(cash) / 100 * 100).filter(|&a| a >= 100).collect();
        amounts.dedup();
        amounts
    }

    /// A bidder's answer: one of the amounts offered, or the option after them, passing.
    pub(crate) fn bid(&mut self, seat: usize, index: u8, at: ServerTime) -> Result<(), ActionError> {
        let Stage::Auction(a) = &self.stage else {
            return Err(ActionError::Ignored);
        };
        if at < a.since - self.cfg.early_ms {
            return Err(ActionError::Ignored);
        }
        let amounts = self.bid_amounts(seat, a.hotel);
        let bid = match usize::from(index) {
            i if i < amounts.len() => Bid::Amount(amounts[i]),
            i if i == amounts.len() => Bid::Pass,
            _ => return Err(ActionError::NotAllowed("Das gibt es nicht.")),
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
