//! Paying and debt auctions as plain table operations: who bids, who wins,
//! where the property goes, and going out. The live game settles every debt
//! through these, whether a phone, the house or a simulator bot decides.

use super::rules::Table;

impl Table {
    /// `seat` pays `amount` to `to` (the bank when `None`) as far as its cash
    /// goes; short of it, it pays what it has and is out. False when it went out.
    pub fn pay(&mut self, seat: usize, to: Option<usize>, amount: i32) -> bool {
        let paid = amount.min(self.seats[seat].cash);
        self.seats[seat].cash -= paid;
        if let Some(to) = to {
            self.seats[to].cash += paid;
        }
        if paid < amount {
            self.eliminate(seat);
        }
        paid == amount
    }

    /// Who bids when `seller` auctions: everyone else still in, nearest after
    /// the seller first, which is the order that breaks a tie.
    pub fn bidders(&self, seller: usize) -> Vec<usize> {
        let n = self.seats.len();
        (1..n).map(|step| (seller + step) % n).filter(|&b| !self.seats[b].out).collect()
    }

    /// What the seller gets: the best bid, or the bank's share of the value.
    pub fn auction_price(&self, hotel: u8, best: Option<(i32, usize)>) -> i32 {
        best.map_or(self.value(hotel) * self.rules.bank_pct / 100, |(bid, _)| bid)
    }

    /// The winner pays and takes the property as it stands; with no bid the
    /// bank pays its share and the plot goes back to the bank, free.
    pub fn settle_auction(&mut self, seller: usize, hotel: u8, best: Option<(i32, usize)>) {
        self.seats[seller].cash += self.auction_price(hotel, best);
        match best {
            Some((bid, winner)) => {
                self.seats[winner].cash -= bid;
                self.transfer(hotel, winner);
            }
            None => self.release(hotel),
        }
    }
}

/// The highest of the sealed `(bid, bidder)` pairs, given in `bidders` order;
/// of equal bids the first wins, i.e. the bidder nearest after the seller.
pub fn best_bid(bids: impl IntoIterator<Item = (i32, usize)>) -> Option<(i32, usize)> {
    bids.into_iter().fold(None, |best, (bid, b)| if best.is_none_or(|(top, _)| bid > top) { Some((bid, b)) } else { best })
}
