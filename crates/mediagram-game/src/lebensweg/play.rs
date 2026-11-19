//! Plays one whole game with a bot in every seat and records what the
//! simulator compares: each seat's choices, its final worth, and how long the
//! game would take at the table.

use super::bots::Bot;
use super::rules::{Home, Rules};
use super::turn::{Game, Opt, Question};

/// Seconds at the TV: a tap, the wheel, the car's walk per field, the field's
/// news; a decision on a phone. Assumed until a test night measures them.
pub const TAP: f64 = 1.5;
pub const WHEEL: f64 = 2.5;
pub const PER_FIELD: f64 = 0.4;
pub const NEWS: f64 = 3.0;
pub const DECIDE: f64 = 6.0;

#[derive(Debug, Clone, Default)]
pub struct Record {
    pub worth: Vec<i64>,
    pub winner: usize,
    /// Per seat, the branch taken at each fork (Start, Kinder, Kreuzung).
    pub forks: Vec<[Option<u8>; 3]>,
    pub home: Vec<Option<Home>>,
    /// The career each seat retired from.
    pub career: Vec<Option<usize>>,
    pub insure: Vec<bool>,
    pub stock: Vec<bool>,
    pub spins: u32,
    pub seconds: f64,
    /// Seats that end with less than nothing.
    pub debt: usize,
    /// Share of the game's spins gone when the token pile ran out.
    pub pile_empty_at: Option<f64>,
}

pub fn play(seed: u64, players: usize, rules: Rules) -> Record {
    let mut g = Game::new(players, rules, seed);
    let mut bots: Vec<Bot> = (0..players as u64).map(|i| Bot::new(seed.wrapping_mul(31).wrapping_add(i))).collect();
    let mut r = Record { forks: vec![[None; 3]; players], home: vec![None; players], career: vec![None; players], ..Record::default() };
    r.insure = bots.iter().map(|b| b.insure).collect();
    r.stock = bots.iter().map(|b| b.stock).collect();
    let mut empty_at = None;
    while let Some(ask) = g.ask().cloned() {
        let p = ask.player;
        let choice = bots[p].answer(&g.table, &ask);
        match (ask.question, ask.options[choice]) {
            (Question::Turn, Opt::Spin) => r.spins += 1,
            (Question::Turn, _) => r.seconds += DECIDE,
            (Question::Fork, Opt::Branch(b)) => {
                let name = g.table.board.fields[g.table.seats[p].field].name;
                match ["Start", "Kinder", "Kreuzung"].iter().position(|&n| n == name) {
                    Some(i) => r.forks[p][i] = Some(b),
                    None => r.career[p] = g.table.seats[p].career,
                }
                r.seconds += DECIDE;
            }
            _ => r.seconds += DECIDE,
        }
        let before = g.table.seats[p].spins.len();
        g.answer(choice);
        let spun: u32 = g.table.seats[p].spins[before..].iter().map(|&n| u32::from(n)).sum();
        r.seconds += TAP + WHEEL + f64::from(spun) * PER_FIELD + NEWS;
        if empty_at.is_none() && g.table.pile.is_empty() {
            empty_at = Some(r.spins);
        }
        assert!(r.spins < 20_000, "seed {seed}: the game does not end");
    }
    r.home = g.table.seats.iter().map(|s| s.retired).collect();
    r.worth = (0..players).map(|p| g.table.worth(p)).collect();
    r.winner = (0..players).max_by_key(|&p| (r.worth[p], std::cmp::Reverse(p))).unwrap_or(0);
    r.debt = g.table.seats.iter().filter(|s| s.cash < 0).count();
    r.pile_empty_at = empty_at.map(|s| f64::from(s) / f64::from(r.spins.max(1)));
    r
}
