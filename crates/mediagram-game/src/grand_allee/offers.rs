//! What a field or a turn offers a seat: pure lookups the live game's asks
//! are built from, so every question reads the rules alike.

use super::board::{hotels_at, HOTELS};
use super::rules::Table;

impl Table {
    /// Grundstück: the bordering plots `seat` may take, with their price: a
    /// free one at its land price, another player's unbuilt one at the forced price.
    pub fn plot_offers(&self, seat: usize, at: u8) -> Vec<(u8, i32)> {
        hotels_at(at)
            .filter_map(|h| match self.estate(h).owner {
                None => Some((h, HOTELS[usize::from(h)].land)),
                Some(o) if o != seat && self.estate(h).levels == 0 => Some((h, self.forced_price(h))),
                _ => None,
            })
            .collect()
    }

    /// Baustelle: own bordering hotels with a stage left that needs a permit.
    pub fn site_hotels(&self, seat: usize, at: u8) -> Vec<u8> {
        hotels_at(at).filter(|&h| self.estate(h).owner == Some(seat) && self.permit_stages_left(h) > 0).collect()
    }

    /// Own built hotels with a frontage field still free for an entrance.
    pub fn entrance_hotels(&self, seat: usize) -> Vec<u8> {
        self.owned(seat).into_iter().filter(|&h| !self.free_frontage(h).is_empty()).collect()
    }

    /// Gratis bauen: own hotels with any stage left, the leisure wing included.
    pub fn open_hotels(&self, seat: usize) -> Vec<u8> {
        self.owned(seat).into_iter().filter(|&h| usize::from(self.estate(h).levels) < HOTELS[usize::from(h)].stages.len()).collect()
    }
}
