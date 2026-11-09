//! Paying in Grand Allee: a mover short of cash puts properties of their
//! choice up for debt auctions until the debt is covered. Each auction runs
//! on every other phone at once, sealed; the bank buys what nobody bids on.

use super::live::{Auction, Bid, GrandAlleeGame, Stage};
use super::live_text::Phrase;
use super::live_turn::Step;
use super::money::best_bid;
use crate::game::{ActionError, GameCtx};
use crate::protocol::ServerTime;

/// Bids go in whole hundreds.
pub(crate) const BID_STEP: i32 = 100;

/// What the mover owes and is settling, and so what follows once it is met.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Debt {
    /// A guest's stay, to the hotel's owner; the field comes next.
    Rent { owner: usize, amount: i32 },
    /// A doubled permit, to the bank, raised by auctioning other properties;
    /// once paid, `count` stages of `hotel` are built.
    Permit { hotel: u8, count: u8, price: i32 },
}

impl Debt {
    /// What the mover has to pay.
    pub(crate) fn amount(self) -> i32 {
        match self {
            Debt::Rent { amount, .. } => amount,
            Debt::Permit { price, .. } => price,
        }
    }

    /// The hotel a doubled permit is being paid for, which is not for sale meanwhile.
    pub(crate) fn building(self) -> Option<u8> {
        match self {
            Debt::Rent { .. } => None,
            Debt::Permit { hotel, .. } => Some(hotel),
        }
    }
}

impl GrandAlleeGame {
    /// Pays the mover's debt once they can, asking what to auction while they
    /// cannot; with nothing left to sell they pay what they have and are out.
    pub(crate) fn settle(&mut self, ctx: &GameCtx, debt: Debt) {
        let (now, s) = (ctx.now, self.turn);
        let short = self.t.seats[s].cash < debt.amount();
        if short && !self.sellable(debt).is_empty() {
            let ask = self.sell_ask(debt);
            return self.ask(now, ask, Step::Owing(debt));
        }
        let owner = match debt {
            // nothing else left to sell: the hotel in question goes after all, and the permit costs nothing
            Debt::Permit { hotel, .. } if short => return self.open_auction(now, hotel, None),
            Debt::Permit { hotel, count, price } => {
                let first = self.t.build(s, hotel, count, price);
                return self.built(ctx, hotel, first);
            }
            Debt::Rent { owner, .. } => owner,
        };
        if self.t.pay(s, Some(owner), debt.amount()) {
            return self.go_to_field(now);
        }
        self.gone.push(s);
        let text = Phrase::new("Du kannst nicht zahlen und bist raus.", "kann nicht zahlen und ist raus.");
        self.stage = Stage::Note { text, broke: true, at: now };
    }

    pub(crate) fn open_auction(&mut self, now: ServerTime, hotel: u8, owing: Option<Debt>) {
        let bids = self.t.bidders(self.turn).into_iter().map(|b| (b, Bid::Open)).collect();
        self.stage = Stage::Auction(Auction { hotel, since: now, bids, owing });
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

    /// When an auction closes: once no open bidder can still answer, one at the
    /// table until the clock, an absent one until the table's grace (never past the clock).
    pub(crate) fn auction_close(&self, ctx: &GameCtx, a: &Auction) -> ServerTime {
        let clock = a.since + self.cfg.auction_ms;
        let open = a.bids.iter().filter(|&&(_, answer)| answer == Bid::Open);
        let holds = open.map(|&(b, _)| ctx.house_at(self.ids[b], a.since).map_or(clock, |at| at.min(clock)));
        holds.reduce(f64::max).unwrap_or(ctx.now)
    }

    /// The highest bid takes the property; whoever stayed silent passed.
    pub(crate) fn close_auction(&mut self, ctx: &GameCtx, a: Auction) {
        let amounts = a.bids.iter().filter_map(|&(b, answer)| match answer {
            Bid::Amount(x) => Some((x, b)),
            Bid::Open | Bid::Pass => None,
        });
        let best = best_bid(amounts);
        let price = self.t.auction_price(a.hotel, best);
        self.t.settle_auction(self.turn, a.hotel, best);
        self.stage = Stage::Sold { hotel: a.hotel, winner: best.map(|(_, w)| w), price, at: ctx.now, owing: a.owing };
    }
}
