//! What happens when a Bauherr player has to pay: selling property back to the
//! bank, going out, and the final standings.

use crate::bauherr_rules::Table;

impl Table {
    /// `seat` pays `to` (the bank when `None`). Short of cash the host sells
    /// property back to the bank at half price, top stages first and then plots,
    /// dearest first; still short, the player pays what is left and is out.
    /// Returns what was paid.
    pub fn pay(&mut self, seat: usize, to: Option<usize>, amount: i32) -> i32 {
        while self.seats[seat].cash < amount && self.sell_one(seat) {}
        let paid = self.seats[seat].cash.min(amount);
        self.seats[seat].cash -= paid;
        if let Some(to) = to {
            self.seats[to].cash += paid;
        }
        if paid < amount {
            let outs = self.seats.iter().filter(|s| s.out).count() as u8;
            self.seats[seat].out = true;
            self.seats[seat].out_n = outs + 1;
        }
        paid
    }

    fn sell_one(&mut self, seat: usize) -> bool {
        let top = |t: &Self, p: u8| t.plot(p).levels[usize::from(t.levels(p)) - 1].cost;
        let built = self.owned(seat).filter(|&p| self.levels(p) > 0).max_by_key(|&p| top(self, p));
        if let Some(plot) = built {
            self.seats[seat].cash += top(self, plot) / 2;
            let e = &mut self.estates[usize::from(plot)];
            e.levels -= 1;
            if e.levels == 0 {
                self.entrances.iter_mut().filter(|e| **e == Some(plot)).for_each(|e| *e = None);
            }
            return true;
        }
        let Some(plot) = self.owned(seat).max_by_key(|&p| self.plot(p).land) else {
            return false;
        };
        self.seats[seat].cash += self.plot(plot).land / 2;
        self.estates[usize::from(plot)].owner = None;
        true
    }

    /// Cash + land + built stages.
    pub fn worth(&self, seat: usize) -> i32 {
        let property: i32 = self
            .owned(seat)
            .map(|p| self.plot(p).land + self.plot(p).levels[..usize::from(self.levels(p))].iter().map(|l| l.cost).sum::<i32>())
            .sum();
        self.seats[seat].cash + property
    }

    /// Seat indices, first to last: players still in by net worth, then those
    /// out, whoever lasted longest first.
    pub fn standings(&self) -> Vec<usize> {
        let mut order: Vec<usize> = (0..self.seats.len()).collect();
        order.sort_by_key(|&s| (self.seats[s].out, std::cmp::Reverse(self.seats[s].out_n), std::cmp::Reverse(self.worth(s)), s));
        order
    }
}
