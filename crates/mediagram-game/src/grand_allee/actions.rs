//! What the field a pawn stops on offers: Grundstück, Baustelle, Freier
//! Eingang and Gratis bauen, decided by the seat's bot.

use rand::Rng;

use super::board::{field, hotels_at, Field, HOTELS};
use super::play::{Game, PACE};
use super::rules::{permit_for, Permit};

impl Game {
    pub(super) fn field_action(&mut self, seat: usize, at: u8) {
        match field(at) {
            Field::Plot => self.plot(seat, at),
            Field::Site => self.site(seat, at),
            Field::FreeEntrance => {
                let built: Vec<u8> = self.t.owned(seat).into_iter().filter(|&h| !self.t.free_frontage(h).is_empty()).collect();
                if let Some(&h) = built.iter().max_by_key(|&&h| self.t.rate(h)) {
                    self.decide(seat);
                    self.first_entrance(seat, h);
                }
            }
            Field::FreeBuild => {
                let open: Vec<u8> = self.t.owned(seat).into_iter().filter(|&h| usize::from(self.t.estate(h).levels) < HOTELS[usize::from(h)].stages.len()).collect();
                if let Some(h) = self.bots[seat].pick_free_stage(&self.t, &open) {
                    self.decide(seat);
                    if self.t.build(seat, h, 1, 0) {
                        self.first_entrance(seat, h);
                    }
                }
            }
            Field::Bank | Field::Rathaus | Field::Allee => {}
        }
    }

    /// Grundstück: one bordering plot, free at its land price or another
    /// player's unbuilt one at the forced price.
    fn plot(&mut self, seat: usize, at: u8) {
        let offers: Vec<(u8, i32)> = hotels_at(at)
            .filter_map(|h| match self.t.estate(h).owner {
                None => Some((h, HOTELS[usize::from(h)].land)),
                Some(o) if o != seat && self.t.estate(h).levels == 0 => Some((h, self.t.forced_price(h))),
                _ => None,
            })
            .collect();
        if offers.is_empty() {
            return;
        }
        self.decide(seat);
        if let Some(h) = self.bots[seat].pick_plot(&self.t, seat, &offers) {
            if self.t.estate(h).owner.is_some() {
                self.t.forced_buy(seat, h);
                self.rec.forced += 1;
            } else {
                self.t.buy(seat, h);
            }
        }
    }

    /// Baustelle: name the hotel and stages, roll the permit; a granted one must be used.
    fn site(&mut self, seat: usize, at: u8) {
        let own: Vec<u8> = hotels_at(at).filter(|&h| self.t.estate(h).owner == Some(seat) && self.t.permit_stages_left(h) > 0).collect();
        let Some((h, count)) = self.bots[seat].build_plan(&self.t, seat, &own) else { return };
        self.decide(seat);
        let permit = permit_for(self.die());
        if permit == Permit::Rejected {
            return;
        }
        let price = self.t.stage_price(h, count, permit);
        if price > self.t.seats[seat].cash {
            // the classic way out of a permit one cannot pay: auction the plot in question instead
            self.auction(seat, h);
            return;
        }
        if self.t.build(seat, h, count, price) {
            self.first_entrance(seat, h);
        }
        self.clock += PACE.note;
    }

    /// A free entrance on a random free frontage field.
    fn first_entrance(&mut self, seat: usize, h: u8) {
        let free = self.t.free_frontage(h);
        if !free.is_empty() {
            let at = free[self.rng.random_range(0..free.len())];
            self.t.place_entrance(seat, h, at, 0);
        }
    }
}
