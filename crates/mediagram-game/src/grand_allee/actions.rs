//! What the field a pawn stops on offers: Grundstück, Baustelle, Freier
//! Eingang and Gratis bauen, decided by the seat's bot.

use rand::Rng;

use super::board::{field, Field};
use super::play::{Game, PACE};
use super::rules::{permit_for, Permit};

impl Game {
    pub(super) fn field_action(&mut self, seat: usize, at: u8) {
        match field(at) {
            Field::Plot => self.plot(seat, at),
            Field::Site => self.site(seat, at),
            Field::FreeEntrance => {
                if let Some(h) = self.bots[seat].pick_free_entrance(&self.t, &self.t.entrance_hotels(seat)) {
                    self.decide(seat);
                    self.first_entrance(seat, h);
                }
            }
            Field::FreeBuild => {
                if let Some(h) = self.bots[seat].pick_free_stage(&self.t, &self.t.open_hotels(seat)) {
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
        let offers = self.t.plot_offers(seat, at);
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
        let Some((h, count)) = self.bots[seat].build_plan(&self.t, seat, &self.t.site_hotels(seat, at)) else { return };
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
