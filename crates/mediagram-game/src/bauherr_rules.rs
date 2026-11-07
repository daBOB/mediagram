//! Rules of Bauherr: who owns and has built what, what walking, building,
//! entrances and guests cost, and what happens when a player cannot pay.
//! Plain data and pure functions, so every rule is tested without a room.
//! Seats are indexed in turn order.

use crate::bauherr_board::board;
use crate::protocol::{BauherrBoard, BauherrPermit, BauherrPlot, BauherrTileKind, PlayerId};

pub const START_CASH: i32 = 12_000;
pub const BANK_BONUS: i32 = 2_000;
/// Turns all players take together; the round limit is this divided by the players.
/// Tuned to a ~50 minute game at human pace (12-18 s a turn); re-time after the first real game.
pub const TURN_BUDGET: u8 = 180;
/// What an absent player keeps in hand when it plays itself.
pub const RESERVE: i32 = 2_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Seat {
    pub player: PlayerId,
    pub cash: i32,
    pub field: u8,
    pub out: bool,
    /// 1 for the first seat to go out, 2 for the next; 0 while in.
    pub out_n: u8,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct Estate {
    pub owner: Option<usize>,
    pub levels: u8,
}

#[derive(Debug, Clone)]
pub struct Table {
    pub board: BauherrBoard,
    pub seats: Vec<Seat>,
    pub estates: Vec<Estate>,
    /// Per field: the plot whose entrance stands there.
    pub entrances: Vec<Option<u8>>,
}

/// A permit die: three in six approve, one each rejects, waives or doubles.
pub fn permit_for(die: u8) -> BauherrPermit {
    match die {
        1..=3 => BauherrPermit::Approved,
        4 => BauherrPermit::Rejected,
        5 => BauherrPermit::Free,
        _ => BauherrPermit::Double,
    }
}

pub fn rounds_for(players: usize) -> u8 {
    (TURN_BUDGET / players.max(1) as u8).max(1)
}

impl Table {
    pub fn new(players: &[PlayerId]) -> Self {
        let board = board();
        let seats = players.iter().map(|&player| Seat { player, cash: START_CASH, field: 0, out: false, out_n: 0 }).collect();
        let (estates, entrances) = (vec![Estate::default(); board.plots.len()], vec![None; board.tiles.len()]);
        Self { board, seats, estates, entrances }
    }

    pub fn plot(&self, id: u8) -> &BauherrPlot {
        &self.board.plots[usize::from(id)]
    }

    pub fn plot_at(&self, field: u8) -> Option<u8> {
        self.board.plots.iter().find(|p| p.fields.contains(&field)).map(|p| p.id)
    }

    pub fn kind(&self, field: u8) -> BauherrTileKind {
        self.board.tiles[usize::from(field)].kind
    }

    pub fn active(&self) -> usize {
        self.seats.iter().filter(|s| !s.out).count()
    }

    pub fn levels(&self, plot: u8) -> u8 {
        self.estates[usize::from(plot)].levels
    }

    pub fn owner(&self, plot: u8) -> Option<usize> {
        self.estates[usize::from(plot)].owner
    }

    pub fn owned(&self, seat: usize) -> impl Iterator<Item = u8> + '_ {
        (0..self.estates.len() as u8).filter(move |&p| self.owner(p) == Some(seat))
    }

    /// Nightly rate of the stages built so far.
    pub fn rate(&self, plot: u8) -> i32 {
        self.levels(plot).checked_sub(1).map_or(0, |i| self.plot(plot).levels[usize::from(i)].rate)
    }

    /// Moves a pawn `steps` fields along the first exit; entering the bank,
    /// even to stop there, pays. Returns (from, to, paid).
    pub fn walk(&mut self, seat: usize, steps: u8) -> (u8, u8, bool) {
        let from = self.seats[seat].field;
        let mut at = from;
        let mut paid = false;
        for _ in 0..steps {
            at = self.board.tiles[usize::from(at)].exits[0];
            paid |= self.kind(at) == BauherrTileKind::Bank;
        }
        self.seats[seat].field = at;
        if paid {
            self.seats[seat].cash += BANK_BONUS;
        }
        (from, at, paid)
    }

    pub fn buy(&mut self, seat: usize, plot: u8) {
        self.seats[seat].cash -= self.plot(plot).land;
        self.estates[usize::from(plot)].owner = Some(seat);
    }

    /// Next stage's cost, if the plot can still grow.
    pub fn next_cost(&self, plot: u8) -> Option<i32> {
        self.plot(plot).levels.get(usize::from(self.levels(plot))).map(|l| l.cost)
    }

    /// Stages 1..=remaining with their price under `permit`, those `seat` can pay.
    pub fn level_prices(&self, seat: usize, plot: u8, permit: BauherrPermit) -> Vec<(u8, i32)> {
        let from = usize::from(self.levels(plot));
        let costs: Vec<i32> = self.plot(plot).levels[from..].iter().map(|l| l.cost).collect();
        (1..=costs.len())
            .filter_map(|n| {
                let sum: i32 = costs[..n].iter().sum();
                let price = match permit {
                    BauherrPermit::Free => sum - costs[0],
                    BauherrPermit::Double => sum * 2,
                    _ => sum,
                };
                (price <= self.seats[seat].cash).then_some((n as u8, price))
            })
            .collect()
    }

    pub fn build(&mut self, seat: usize, plot: u8, count: u8, price: i32) {
        self.seats[seat].cash -= price;
        self.estates[usize::from(plot)].levels += count;
    }

    /// Bordering fields of a built plot with no entrance yet.
    pub fn free_fields(&self, plot: u8) -> Vec<u8> {
        if self.levels(plot) == 0 {
            return Vec::new();
        }
        self.plot(plot).fields.iter().copied().filter(|&f| self.entrances[usize::from(f)].is_none()).collect()
    }

    pub fn place_entrance(&mut self, seat: usize, plot: u8, field: u8, price: i32) {
        self.seats[seat].cash -= price;
        self.entrances[usize::from(field)] = Some(plot);
    }

    /// The other player's hotel whose entrance `seat` stands at, if any.
    pub fn host_at(&self, seat: usize, field: u8) -> Option<(usize, u8)> {
        let plot = self.entrances[usize::from(field)]?;
        self.owner(plot).filter(|&o| o != seat).map(|o| (o, plot))
    }
}

#[cfg(test)]
#[path = "bauherr_rules_tests.rs"]
mod tests;
