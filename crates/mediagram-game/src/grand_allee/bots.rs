//! Simulation players. Three styles differ only in how much cash they keep
//! back and how high they bid, so a balance problem shows across styles
//! rather than hiding behind one strategy.

use super::board::HOTELS;
use super::rules::{Permit, Table};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Style {
    Cautious,
    Balanced,
    Bold,
}

pub const STYLES: [Style; 3] = [Style::Cautious, Style::Balanced, Style::Bold];

#[derive(Debug, Clone, Copy)]
pub struct Bot {
    pub style: Style,
}

impl Bot {
    /// Cash the bot keeps in hand after building or buying entrances.
    fn reserve(&self) -> i32 {
        match self.style {
            Style::Cautious => 4000,
            Style::Balanced => 2500,
            Style::Bold => 1000,
        }
    }

    /// Share of a property's value the bot bids at most.
    fn bid_pct(&self) -> i32 {
        match self.style {
            Style::Cautious => 60,
            Style::Balanced => 75,
            Style::Bold => 90,
        }
    }

    fn cash(t: &Table, seat: usize) -> i32 {
        t.seats[seat].cash
    }

    /// Land is the start of everything, so it is bought down to half the reserve.
    pub fn takes_land(&self, t: &Table, seat: usize, price: i32) -> bool {
        Self::cash(t, seat) - price >= self.reserve() / 2
    }

    /// Building and entrances keep the full reserve.
    pub fn spends(&self, t: &Table, seat: usize, price: i32) -> bool {
        Self::cash(t, seat) - price >= self.reserve()
    }

    /// Of the plots on offer (hotel, price), the biggest one it can take.
    pub fn pick_plot(&self, t: &Table, seat: usize, offers: &[(u8, i32)]) -> Option<u8> {
        offers
            .iter()
            .filter(|&&(_, price)| self.takes_land(t, seat, price))
            .max_by_key(|&&(h, _)| HOTELS[usize::from(h)].land)
            .map(|&(h, _)| h)
    }

    /// At a Baustelle: the hotel whose next stage earns most, and as many
    /// stages as the reserve allows at the normal price.
    pub fn build_plan(&self, t: &Table, seat: usize, hotels: &[u8]) -> Option<(u8, u8)> {
        let next_rate = |h: u8| HOTELS[usize::from(h)].stages[usize::from(t.estate(h).levels)].1;
        let hotel = *hotels.iter().max_by_key(|&&h| next_rate(h))?;
        let count = (1..=t.permit_stages_left(hotel))
            .take_while(|&n| self.spends(t, seat, t.stage_price(hotel, n, Permit::Approved)))
            .last()?;
        Some((hotel, count))
    }

    /// For a free stage: the dearest one on offer.
    pub fn pick_free_stage(&self, t: &Table, hotels: &[u8]) -> Option<u8> {
        hotels.iter().copied().max_by_key(|&h| HOTELS[usize::from(h)].stages[usize::from(t.estate(h).levels)].0)
    }

    /// What to auction when money is short: the least valuable property.
    pub fn sell_choice(&self, t: &Table, seat: usize) -> Option<u8> {
        t.owned(seat).into_iter().min_by_key(|&h| t.value(h))
    }

    /// A sealed bid in steps of 100, or none.
    pub fn bid(&self, t: &Table, seat: usize, hotel: u8) -> Option<i32> {
        let most = (Self::cash(t, seat) - self.reserve() / 2).min(t.value(hotel) * self.bid_pct() / 100);
        let bid = most / 100 * 100;
        (bid >= 100).then_some(bid)
    }
}
