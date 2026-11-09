//! Plays one whole game with bots, for the simulator: the turn order of the
//! design, a clock fed by the live game's timings, and a record of what the
//! acceptance criteria measure.

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use super::board::HOTELS;
use super::bots::{Bot, Style, STYLES};
use super::rules::{Rules, Table};

/// Seconds per beat: the die (flight 1.15 s + shown 0.9 s), a hop and a note
/// from the live game's timings; a tap (roll, extra turn), a real decision and
/// a sealed auction assumed until the test night measures them.
pub struct Pace {
    pub die: f64,
    pub hop: f64,
    pub note: f64,
    pub tap: f64,
    pub decide: f64,
    pub auction: f64,
}

pub const PACE: Pace = Pace { die: 2.05, hop: 0.28, note: 1.4, tap: 1.5, decide: 6.0, auction: 10.0 };

#[derive(Debug, Clone)]
pub struct Record {
    pub styles: Vec<Style>,
    pub minutes: f64,
    pub rounds: u32,
    pub last_standing: bool,
    pub winner: usize,
    pub half_leader: Option<usize>,
    pub owned_at_half: usize,
    /// Longest stretch, in seconds, any player waited between two of their own decisions.
    pub max_wait: f64,
    pub debt_auctions: u32,
    pub forced: u32,
    pub bankrupt: u32,
}

pub(super) struct Game {
    pub(super) t: Table,
    pub(super) bots: Vec<Bot>,
    pub(super) rng: StdRng,
    pub(super) clock: f64,
    last_decision: Vec<f64>,
    pub(super) rec: Record,
}

/// Plays a game for `players` seats with styles drawn from `seed`.
pub fn play(seed: u64, players: usize, rules: Rules) -> Record {
    let mut rng = StdRng::seed_from_u64(seed);
    let bots: Vec<Bot> = (0..players).map(|_| Bot { style: STYLES[rng.random_range(0..STYLES.len())] }).collect();
    let rounds = rules.rounds(players);
    let rec = Record {
        styles: bots.iter().map(|b| b.style).collect(),
        minutes: 0.0, rounds: 0, last_standing: false, winner: 0, half_leader: None,
        owned_at_half: 0, max_wait: 0.0, debt_auctions: 0, forced: 0, bankrupt: 0,
    };
    let mut g = Game { t: Table::new(players, rules), bots, rng, clock: 0.0, last_decision: vec![0.0; players], rec };
    for round in 1..=rounds {
        for seat in 0..players {
            if !g.t.seats[seat].out && g.t.active() > 1 {
                g.turn(seat);
            }
        }
        g.rec.rounds = round;
        if round == rounds / 2 {
            g.rec.half_leader = g.leader();
            g.rec.owned_at_half = g.t.estates.iter().filter(|e| e.owner.is_some()).count();
        }
        if g.t.active() <= 1 {
            break;
        }
    }
    g.rec.last_standing = g.t.active() == 1;
    g.rec.winner = g.leader().unwrap_or(0);
    g.rec.minutes = g.clock / 60.0;
    g.rec
}

impl Game {
    fn leader(&self) -> Option<usize> {
        (0..self.t.seats.len()).filter(|&s| !self.t.seats[s].out).max_by_key(|&s| (self.t.net_worth(s), std::cmp::Reverse(s)))
    }

    /// A decision by `seat` takes a human beat; the wait since its last one is measured.
    pub(super) fn decide(&mut self, seat: usize) {
        self.decide_together(&[seat], PACE.decide);
    }

    /// Several players deciding at once (an auction on every phone): one clock for all.
    pub(super) fn decide_together(&mut self, seats: &[usize], secs: f64) {
        for &s in seats {
            self.rec.max_wait = self.rec.max_wait.max(self.clock - self.last_decision[s]);
        }
        self.clock += secs;
        for &s in seats {
            self.last_decision[s] = self.clock;
        }
    }

    pub(super) fn die(&mut self) -> u8 {
        self.clock += PACE.die;
        self.rng.random_range(1..=6)
    }

    fn turn(&mut self, seat: usize) {
        for _ in 0..10 {
            self.leisure(seat);
            self.decide_together(&[seat], PACE.tap); // "Würfeln"
            let roll = self.die();
            let walk = self.t.walk(seat, roll);
            self.clock += PACE.hop * f64::from(roll);
            if walk.passed_rathaus {
                self.rathaus(seat);
            }
            if let Some((owner, hotel)) = self.t.host_at(seat, walk.to) {
                let nights = i32::from(self.die());
                self.pay(seat, Some(owner), nights * self.t.rate(hotel));
                self.clock += PACE.note;
            }
            if self.t.seats[seat].out {
                return;
            }
            self.field_action(seat, walk.to);
            if roll != 6 || self.t.seats[seat].out || self.t.active() <= 1 {
                return;
            }
            self.decide_together(&[seat], PACE.tap); // takes the extra turn
        }
    }

    /// Before rolling: the leisure wing of a completed hotel, no permit.
    fn leisure(&mut self, seat: usize) {
        for h in self.t.owned(seat) {
            if let Some(cost) = self.t.leisure_cost(h).filter(|&c| self.bots[seat].spends(&self.t, seat, c)) {
                self.decide(seat);
                self.t.build(seat, h, 1, cost);
            }
        }
    }

    /// Passing the Rathaus: one entrance per own built hotel.
    fn rathaus(&mut self, seat: usize) {
        for h in self.t.owned(seat) {
            let free = self.t.free_frontage(h);
            let price = HOTELS[usize::from(h)].entrance;
            if !free.is_empty() && self.bots[seat].spends(&self.t, seat, price) {
                self.decide(seat);
                let at = free[self.rng.random_range(0..free.len())];
                self.t.place_entrance(seat, h, at, price);
            }
        }
    }
}
