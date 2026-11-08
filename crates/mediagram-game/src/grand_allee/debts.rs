//! Paying, and what happens when a player cannot: debt auctions of whole
//! properties among the others (sealed, no minimum), the bank as the buyer of
//! last resort, and going out.

use super::play::{Game, PACE};

impl Game {
    /// `seat` pays `amount` to `to` (or the bank), auctioning properties until
    /// it can; if everything is gone it pays what it has and is out.
    pub(super) fn pay(&mut self, seat: usize, to: Option<usize>, amount: i32) {
        while self.t.seats[seat].cash < amount {
            let Some(h) = self.bots[seat].sell_choice(&self.t, seat) else { break };
            self.auction(seat, h);
        }
        let paid = amount.min(self.t.seats[seat].cash);
        self.t.seats[seat].cash -= paid;
        if let Some(to) = to {
            self.t.seats[to].cash += paid;
        }
        if paid < amount {
            self.t.eliminate(seat);
            self.rec.bankrupt += 1;
        }
    }

    /// Every other player still in bids once, sealed; the highest bid wins (a
    /// tie goes to the bidder nearest after the seller). No bid: the bank buys.
    pub(super) fn auction(&mut self, seller: usize, hotel: u8) {
        let n = self.t.seats.len();
        // in seat order after the seller, so the first of equal bids is the nearest
        let bidders: Vec<usize> = (1..n).map(|step| (seller + step) % n).filter(|&b| !self.t.seats[b].out).collect();
        let mut best: Option<(i32, usize)> = None;
        for &bidder in &bidders {
            if let Some(bid) = self.bots[bidder].bid(&self.t, bidder, hotel)
                && best.is_none_or(|(b, _)| bid > b)
            {
                best = Some((bid, bidder));
            }
        }
        // the seller picks the property and everyone bids on their phones at once: one clock
        let mut deciding = bidders;
        deciding.push(seller);
        self.decide_together(&deciding, PACE.auction);
        match best {
            Some((bid, winner)) => {
                self.t.seats[winner].cash -= bid;
                self.t.seats[seller].cash += bid;
                self.t.transfer(hotel, winner);
            }
            None => {
                self.t.seats[seller].cash += self.t.value(hotel) * self.t.rules.bank_pct / 100;
                self.t.release(hotel);
            }
        }
        self.rec.debt_auctions += 1;
    }
}
