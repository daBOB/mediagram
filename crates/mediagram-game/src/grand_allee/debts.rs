//! Paying, and what happens when a player cannot: debt auctions of whole
//! properties among the others (sealed, no minimum), the bank as the buyer of
//! last resort, and going out.

use super::money::best_bid;
use super::play::{Game, PACE};

impl Game {
    /// `seat` pays `amount` to `to` (or the bank), auctioning properties until
    /// it can; if everything is gone it pays what it has and is out.
    pub(super) fn pay(&mut self, seat: usize, to: Option<usize>, amount: i32) {
        while self.t.seats[seat].cash < amount {
            let Some(h) = self.bots[seat].sell_choice(&self.t, seat) else { break };
            self.auction(seat, h);
        }
        if !self.t.pay(seat, to, amount) {
            self.rec.bankrupt += 1;
        }
    }

    /// Every other player still in bids once, sealed; the highest bid wins (a
    /// tie goes to the bidder nearest after the seller). No bid: the bank buys.
    pub(super) fn auction(&mut self, seller: usize, hotel: u8) {
        let bidders = self.t.bidders(seller);
        let best = best_bid(bidders.iter().filter_map(|&b| self.bots[b].bid(&self.t, b, hotel).map(|bid| (bid, b))));
        // the seller picks the property and everyone bids on their phones at once: one clock
        let mut deciding = bidders;
        deciding.push(seller);
        self.decide_together(&deciding, PACE.auction);
        self.t.settle_auction(seller, hotel, best);
        self.rec.debt_auctions += 1;
    }
}
