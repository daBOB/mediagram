//! Simulation players and the house. Three styles differ only in how much
//! cash they keep back and how high they bid, so a balance problem shows
//! across styles rather than hiding behind one strategy.

use super::board::HOTELS;
use super::live_asks::{Ask, Opt};
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
    /// Decides for a player who did not choose: lets an extra turn go and
    /// auctions the hotel in question rather than raising money for a Doppelt.
    pub house: bool,
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

    /// For a free entrance: the hotel that earns most per night.
    pub fn pick_free_entrance(&self, t: &Table, hotels: &[u8]) -> Option<u8> {
        hotels.iter().copied().max_by_key(|&h| t.rate(h))
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

    /// The index of the option the bot answers `ask` with for `seat`. A
    /// playing bot rolls again after a 6, and on a Doppelt it cannot pay
    /// raises the money when its other properties, sold to the bank, cover the shortfall.
    pub(crate) fn pick(&self, t: &Table, seat: usize, ask: &Ask) -> usize {
        let opts = &ask.opts;
        let at = |want: Option<Opt>| want.and_then(|w| opts.iter().position(|&o| o == w));
        let mut hotels: Vec<u8> = opts.iter().filter_map(|o| o.hotel()).collect();
        hotels.dedup();
        let pick = match opts[0] {
            Opt::Roll => {
                let wing = opts.iter().position(|&o| matches!(o, Opt::Leisure(_)) && self.spends(t, seat, o.price(t)));
                wing.or((!self.house).then_some(0))
            }
            Opt::Buy(_) | Opt::Force(_) => {
                let offers: Vec<(u8, i32)> = opts.iter().filter_map(|&o| o.hotel().map(|h| (h, o.price(t)))).collect();
                self.pick_plot(t, seat, &offers).and_then(|h| opts.iter().position(|o| o.hotel() == Some(h)))
            }
            Opt::Build { .. } => at(self.build_plan(t, seat, &hotels).map(|(hotel, count)| Opt::Build { hotel, count })),
            Opt::Hotel { free: true, .. } => at(self.pick_free_entrance(t, &hotels).map(|hotel| Opt::Hotel { hotel, free: true })),
            Opt::Hotel { free: false, .. } => {
                let fits = opts.iter().filter(|&&o| o != Opt::Done && self.spends(t, seat, o.price(t)));
                at(fits.min_by_key(|&&o| o.price(t)).copied())
            }
            Opt::FreeStage(_) => at(self.pick_free_stage(t, &hotels).map(Opt::FreeStage)),
            Opt::Sell(_) => match opts.iter().position(|o| matches!(o, Opt::Raise { .. })) {
                // a Doppelt it cannot pay: the hotel in question is the first option
                Some(raise) => Some(if !self.house && self.raises(t, seat, opts[raise]) { raise } else { 0 }),
                None => at(self.sell_choice(t, seat).map(Opt::Sell)),
            },
            Opt::Place { .. } => Some(0),
            _ => None,
        };
        let out = || opts.iter().position(|o| matches!(o, Opt::Skip | Opt::Done | Opt::Back | Opt::EndTurn));
        pick.or_else(out).unwrap_or(0)
    }

    /// Whether the bot's other properties at the bank's price cover what a doubled permit leaves it short.
    fn raises(&self, t: &Table, seat: usize, raise: Opt) -> bool {
        let Opt::Raise { hotel, count } = raise else { return false };
        let short = t.stage_price(hotel, count, Permit::Double) - Self::cash(t, seat);
        let others: i32 = t.owned(seat).into_iter().filter(|&h| h != hotel).map(|h| t.auction_price(h, None)).sum();
        others >= short
    }
}
