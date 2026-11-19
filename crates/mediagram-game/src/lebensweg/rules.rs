//! Lebensweg's table: who has what (cash, loans, cards, family, tokens) and
//! the money rules of the 2000 rulebook. The turn itself is `turn.rs`.

use rand::rngs::StdRng;
use rand::seq::{IndexedRandom, SliceRandom};
use rand::{Rng, SeedableRng};

use super::board::Board;
pub use super::numbers::Rules;
use super::cards::{Perk, CAREERS, EXTRA_TOKENS, SALARIES, TOKENS};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Home {
    /// Villa am See: the richest there takes the set-aside tokens.
    Villa,
    /// Landhaus: one token more and nobody can take them.
    Country,
}

#[derive(Debug, Clone, Default)]
pub struct Seat {
    pub field: usize,
    pub cash: i64,
    pub loans: u32,
    pub degree: bool,
    pub career: Option<usize>,
    /// Lehramt's perk: a second career for its perk only.
    pub second_career: Option<usize>,
    pub salary: Option<usize>,
    pub house: Option<usize>,
    pub home_insured: bool,
    pub car_insured: bool,
    pub stocks: Vec<u8>,
    /// Indices into `Table::tokens`.
    pub tokens: Vec<usize>,
    pub partner: bool,
    pub children: u8,
    pub miss_turn: bool,
    pub retired: Option<Home>,
    /// This player's spins, latest last (Bühne's perk looks at two in a row).
    pub spins: Vec<u8>,
}

pub struct Table {
    pub rules: Rules,
    pub board: Board,
    pub seats: Vec<Seat>,
    /// The token pool: the original's 25, more on a stretched board.
    pub tokens: Vec<super::cards::Token>,
    pub pile: Vec<usize>,
    pub estate: Vec<usize>,
    pub careers: Vec<usize>,
    pub salaries: Vec<usize>,
    pub houses: Vec<usize>,
    pub stocks: Vec<u8>,
    pub rng: StdRng,
    /// Spins to use before the wheel's own (tests, replays); None = between two numbers.
    pub queued: std::collections::VecDeque<Option<u8>>,
    /// What happened, for the TV and the log: (player, text).
    pub news: Vec<(usize, String)>,
}

impl Table {
    pub fn new(players: usize, rules: Rules, seed: u64) -> Self {
        let board = Board::new(rules.more);
        let mut rng = StdRng::seed_from_u64(seed);
        let count = ((TOKENS.len() as f64 * rules.more).floor() as usize).max(TOKENS.len());
        let tokens: Vec<_> = TOKENS.iter().chain(EXTRA_TOKENS.iter()).copied().take(count).collect();
        let mut pile: Vec<usize> = (0..tokens.len()).collect();
        pile.shuffle(&mut rng);
        let estate = pile.split_off(pile.len() - rules.once_count(rules.estate_tokens));
        let seat = Seat { cash: rules.once(rules.start_cash), ..Seat::default() };
        Self {
            seats: vec![seat; players],
            careers: (0..CAREERS.len()).collect(),
            salaries: (0..SALARIES.len()).collect(),
            houses: (0..super::cards::HOUSES.len()).collect(),
            stocks: super::cards::STOCKS.to_vec(),
            board, tokens, pile, estate, rng, rules,
            queued: Default::default(),
            news: Vec::new(),
        }
    }

    pub fn say(&mut self, p: usize, text: impl Into<String>) {
        self.news.push((p, text.into()));
    }

    /// Pays the bank, borrowing in whole loans when the cash runs out.
    pub fn pay(&mut self, p: usize, amount: i64) {
        let s = &mut self.seats[p];
        s.cash -= amount;
        while s.cash < 0 {
            s.cash += self.rules.loan;
            s.loans += 1;
        }
    }

    /// Pays another player, or the bank when `to` is None or the payer itself.
    pub fn pay_to(&mut self, p: usize, to: Option<usize>, amount: i64) {
        if to == Some(p) {
            return;
        }
        self.pay(p, amount);
        if let Some(t) = to {
            self.seats[t].cash += amount;
        }
    }

    /// Who holds the career with `perk`, as first or Lehramt's second career.
    pub fn perk_owner(&self, perk: Perk) -> Option<usize> {
        self.seats.iter().position(|s| [s.career, s.second_career].iter().flatten().any(|&c| CAREERS[c].perk == perk))
    }

    pub fn has_perk(&self, p: usize, perk: Perk) -> bool {
        self.perk_owner(perk) == Some(p)
    }

    /// Draws `n` cards at random from a free deck (fewer if it runs short).
    pub fn draw(&mut self, deck: Deck, n: usize, degree: bool) -> Vec<usize> {
        let free: Vec<usize> = match deck {
            Deck::Career => self.careers.iter().copied().filter(|&c| degree || !CAREERS[c].degree).collect(),
            Deck::Salary => self.salaries.clone(),
            Deck::House => self.houses.clone(),
        };
        free.choose_multiple(&mut self.rng, n).copied().collect()
    }

    /// Hands card `c` to `p`, returning the one it replaces to its deck.
    pub fn take(&mut self, p: usize, deck: Deck, c: usize) {
        let (free, slot) = match deck {
            Deck::Career => (&mut self.careers, &mut self.seats[p].career),
            Deck::Salary => (&mut self.salaries, &mut self.seats[p].salary),
            Deck::House => (&mut self.houses, &mut self.seats[p].house),
        };
        free.retain(|&x| x != c);
        if let Some(old) = slot.replace(c) {
            free.push(old);
        }
    }

    pub fn spin(&mut self) -> Option<u8> {
        if let Some(s) = self.queued.pop_front() {
            return s;
        }
        if self.rng.random_range(0..1_000) < self.rules.gap_per_mille {
            None
        } else {
            Some(self.rng.random_range(1..=10))
        }
    }

    /// Money plus the tokens' values: the final score once everyone retired.
    pub fn worth(&self, p: usize) -> i64 {
        self.seats[p].cash + self.seats[p].tokens.iter().map(|&t| self.tokens[t].value).sum::<i64>()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Deck {
    Career,
    Salary,
    House,
}
