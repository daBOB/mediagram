//! Grand Allee's rules: who owns and has built what, and what walking,
//! buying, building, entrances and guests cost. Plain data and pure
//! operations (dice come in as numbers), so every rule is tested without a room.

use super::board::{BANK, FIELDS, HOTELS, RATHAUS};
pub use super::building::{permit_for, Permit};

/// The tunable numbers of the design (`design/grand-allee-design.md`, Money).
#[derive(Debug, Clone)]
pub struct Rules {
    pub start_cash: i32,
    pub lap_bonus: i32,
    /// Forced purchase of another player's unbuilt plot, % of its land price.
    pub forced_pct: i32,
    /// What the bank pays when a debt auction gets no bid, % of the property's value.
    pub bank_pct: i32,
    pub round_turns: u32,
    pub min_rounds: u32,
    pub max_rounds: u32,
    /// Scales every nightly rate, for tuning (100 = the table's values).
    pub rent_pct: i32,
}

impl Default for Rules {
    fn default() -> Self {
        Self { start_cash: 12_000, lap_bonus: 2_000, forced_pct: 50, bank_pct: 50, round_turns: 280, min_rounds: 30, max_rounds: 120, rent_pct: 100 }
    }
}

impl Rules {
    pub fn rounds(&self, players: usize) -> u32 {
        (self.round_turns / players.max(1) as u32).clamp(self.min_rounds, self.max_rounds)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Seat {
    pub cash: i32,
    pub field: u8,
    pub out: bool,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Estate {
    pub owner: Option<usize>,
    pub levels: u8,
}

/// Where a walk ended and what it passed.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Walk {
    pub to: u8,
    pub bank_paid: bool,
    pub passed_rathaus: bool,
}

#[derive(Debug, Clone)]
pub struct Table {
    pub rules: Rules,
    pub seats: Vec<Seat>,
    pub estates: Vec<Estate>,
    /// Per field: the hotel whose entrance stands there.
    pub entrances: Vec<Option<u8>>,
}

impl Table {
    pub fn new(players: usize, rules: Rules) -> Self {
        let seats = (0..players).map(|_| Seat { cash: rules.start_cash, field: BANK, out: false }).collect();
        Self { rules, seats, estates: vec![Estate::default(); HOTELS.len()], entrances: vec![None; usize::from(FIELDS)] }
    }

    pub fn active(&self) -> usize {
        self.seats.iter().filter(|s| !s.out).count()
    }

    pub fn estate(&self, hotel: u8) -> Estate {
        self.estates[usize::from(hotel)]
    }

    pub fn owned(&self, seat: usize) -> Vec<u8> {
        (0..HOTELS.len() as u8).filter(|&h| self.estate(h).owner == Some(seat)).collect()
    }

    /// The Bank stops paying once players have gone out and only two are left
    /// (classic rule); a game that starts with two keeps its lap money.
    pub fn bank_pays(&self) -> bool {
        self.active() > 2 || self.seats.len() <= 2
    }

    /// Moves `steps` fields; a field another pawn stands on is passed over to
    /// the next free one. Entering the Bank pays while `bank_pays`.
    pub fn walk(&mut self, seat: usize, steps: u8) -> Walk {
        let occupied = |t: &Self, f: u8| t.seats.iter().enumerate().any(|(i, s)| i != seat && !s.out && s.field == f);
        let (mut at, mut bank, mut rathaus, mut left) = (self.seats[seat].field, false, false, steps);
        while left > 0 || occupied(self, at) {
            at = (at + 1) % FIELDS;
            left = left.saturating_sub(1);
            bank |= at == BANK;
            rathaus |= at == RATHAUS;
        }
        self.seats[seat].field = at;
        let bank_paid = bank && self.bank_pays();
        if bank_paid {
            self.seats[seat].cash += self.rules.lap_bonus;
        }
        Walk { to: at, bank_paid, passed_rathaus: rathaus }
    }

    pub fn buy(&mut self, seat: usize, hotel: u8) {
        self.seats[seat].cash -= HOTELS[usize::from(hotel)].land;
        self.estates[usize::from(hotel)].owner = Some(seat);
    }

    pub fn forced_price(&self, hotel: u8) -> i32 {
        HOTELS[usize::from(hotel)].land * self.rules.forced_pct / 100
    }

    /// Another player's unbuilt plot changes hands at the forced price, paid to its owner.
    pub fn forced_buy(&mut self, seat: usize, hotel: u8) {
        let (owner, price) = (self.estate(hotel).owner.expect("forced purchase of an owned plot"), self.forced_price(hotel));
        self.seats[seat].cash -= price;
        self.seats[owner].cash += price;
        self.estates[usize::from(hotel)].owner = Some(seat);
    }

    pub fn rate(&self, hotel: u8) -> i32 {
        let levels = usize::from(self.estate(hotel).levels);
        levels.checked_sub(1).map_or(0, |i| HOTELS[usize::from(hotel)].stages[i].1 * self.rules.rent_pct / 100)
    }

    /// Another player's hotel whose entrance stands at `field`: (owner, hotel).
    pub fn host_at(&self, seat: usize, field: u8) -> Option<(usize, u8)> {
        let hotel = self.entrances[usize::from(field)]?;
        self.estate(hotel).owner.filter(|&o| o != seat).map(|o| (o, hotel))
    }

    /// Land plus the cost of every built stage.
    pub fn value(&self, hotel: u8) -> i32 {
        let h = &HOTELS[usize::from(hotel)];
        h.land + h.stages[..usize::from(self.estate(hotel).levels)].iter().map(|s| s.0).sum::<i32>()
    }

    pub fn net_worth(&self, seat: usize) -> i32 {
        self.seats[seat].cash + self.owned(seat).iter().map(|&h| self.value(h)).sum::<i32>()
    }

    /// A whole property as it stands goes to `to` (an auction).
    pub fn transfer(&mut self, hotel: u8, to: usize) {
        self.estates[usize::from(hotel)].owner = Some(to);
    }

    /// Back to the bank as a free, unbuilt plot; its entrances go with it.
    pub fn release(&mut self, hotel: u8) {
        self.estates[usize::from(hotel)] = Estate::default();
        for e in self.entrances.iter_mut().filter(|e| **e == Some(hotel)) {
            *e = None;
        }
    }

    /// Out of the game: whatever they still own returns to the bank.
    pub fn eliminate(&mut self, seat: usize) {
        for h in self.owned(seat) {
            self.release(h);
        }
        self.seats[seat].out = true;
        self.seats[seat].cash = 0;
    }
}
