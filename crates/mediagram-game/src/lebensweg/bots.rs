//! Simulation players. Forks are a coin toss and each seat buys insurance or
//! stock by a coin toss at the start, so the simulator can compare the win
//! share of every route and habit; everything else is played sensibly.

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use super::cards::SALARIES;
use super::turn::{Ask, Opt, Question};
use super::rules::Table;

pub struct Bot {
    pub insure: bool,
    pub stock: bool,
    rng: StdRng,
}

impl Bot {
    pub fn new(seed: u64) -> Self {
        let mut rng = StdRng::seed_from_u64(seed);
        Self { insure: rng.random(), stock: rng.random(), rng }
    }

    pub fn answer(&mut self, t: &Table, ask: &Ask) -> usize {
        let p = ask.player;
        let pay = |s: Option<usize>| s.map_or(0, |s| SALARIES[s].pay);
        let at = |o: Opt| ask.options.iter().position(|&x| x == o);
        let best = |score: &dyn Fn(Opt) -> i64| (0..ask.options.len()).max_by_key(|&i| score(ask.options[i])).unwrap_or(0);
        match ask.question {
            Question::Turn => {
                let wants = [
                    (Opt::CarInsurance, self.insure),
                    (Opt::HomeInsurance, self.insure),
                    (Opt::Stock, self.stock),
                    (Opt::SportTrade, pay(t.seats[p].salary) < 80_000),
                    (Opt::SecondCareer, true),
                ];
                wants.iter().find_map(|&(o, yes)| yes.then(|| at(o)).flatten()).unwrap_or(0)
            }
            Question::Fork => {
                if t.board.fields[t.seats[p].field].next.iter().all(|&n| t.board.is_end(n)) {
                    // The rulebook's advice: the Villa if you think you have the most money.
                    let richest = t.seats.iter().all(|s| s.cash <= t.seats[p].cash);
                    usize::from(!richest)
                } else {
                    self.rng.random_range(0..2)
                }
            }
            Question::Career => self.rng.random_range(0..ask.options.len()),
            Question::Salary => best(&|o| match o {
                Opt::Card(s) => SALARIES[s].pay,
                _ => pay(t.seats[p].salary),
            }),
            Question::NightSchool => usize::from(pay(t.seats[p].salary) >= 50_000),
            Question::TradeSalary => best(&|o| match o {
                Opt::Player(q) => pay(t.seats[q].salary),
                _ => pay(t.seats[p].salary),
            }),
            Question::Steal => best(&|o| match o {
                Opt::Player(q) => t.seats[q].tokens.len() as i64,
                _ => 0,
            }),
            Question::HomeInsurance => usize::from(!self.insure),
        }
    }
}
