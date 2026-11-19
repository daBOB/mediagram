//! A Lebensweg game as a queue of steps that halts at every decision: the
//! live game shows the pending `Ask` on the player's phone, the simulator lets
//! a bot answer it, and both call `answer`. Landing effects, STOPs and
//! retirement are `effects.rs`.

use std::collections::VecDeque;

use super::cards::{Perk, CAREERS, SALARIES};
use super::rules::{Deck, Rules, Table};
use super::walk::{walk, Halt};
pub use super::asks::{Ask, Opt, Question};

#[derive(Debug, Clone)]
pub(super) enum Step {
    Turn { again: bool },
    Move { steps: u8, branch: Option<u8> },
    Land,
    Pick { deck: Deck, from: Vec<usize>, keep_own: bool },
    Ask(Question, Vec<Opt>),
}

pub struct Game {
    pub table: Table,
    /// Whose turn it is.
    pub turn: usize,
    pub(super) steps: VecDeque<Step>,
    ask: Option<Ask>,
    /// Steps still to walk once the player picked a branch.
    left: u8,
    pub over: bool,
}

impl Game {
    pub fn new(players: usize, rules: Rules, seed: u64) -> Self {
        let mut g = Self { table: Table::new(players, rules, seed), turn: 0, steps: VecDeque::from([Step::Turn { again: false }]), ask: None, left: 0, over: false };
        g.advance();
        g
    }

    pub fn ask(&self) -> Option<&Ask> {
        self.ask.as_ref()
    }

    pub(super) fn then(&mut self, step: Step) {
        self.steps.push_front(step);
    }

    fn advance(&mut self) {
        while self.ask.is_none() && !self.over {
            match self.steps.pop_front() {
                Some(step) => self.run(step),
                None => self.next_player(),
            }
        }
    }

    fn next_player(&mut self) {
        let n = self.table.seats.len();
        match (1..=n).map(|i| (self.turn + i) % n).find(|&p| self.table.seats[p].retired.is_none()) {
            Some(p) => {
                self.turn = p;
                self.steps.push_back(Step::Turn { again: false });
            }
            None => self.over = true,
        }
    }

    fn run(&mut self, step: Step) {
        let p = self.turn;
        match step {
            Step::Turn { again } => {
                let s = &self.table.seats[p];
                if s.retired.is_some() {
                    return;
                }
                if !again && s.miss_turn {
                    self.table.seats[p].miss_turn = false;
                    self.table.say(p, "setzt aus");
                    return;
                }
                let options = if again { vec![Opt::Spin] } else { self.purchases(p) };
                self.ask = Some(Ask { player: p, question: Question::Turn, options });
            }
            Step::Move { steps, branch } => self.walk(p, steps, branch),
            Step::Land => self.land(p),
            Step::Pick { deck, from, keep_own } => {
                let mut options: Vec<Opt> = from.into_iter().map(Opt::Card).collect();
                if keep_own {
                    options.push(Opt::Keep);
                }
                let question = if deck == Deck::Career { Question::Career } else { Question::Salary };
                self.ask = Some(Ask { player: p, question, options });
            }
            Step::Ask(question, options) => self.ask = Some(Ask { player: p, question, options }),
        }
    }

    /// The start-of-turn menu: spin, or buy first (2000 rulebook: at the start of any turn).
    fn purchases(&self, p: usize) -> Vec<Opt> {
        let (t, s) = (&self.table, &self.table.seats[p]);
        let mut o = vec![Opt::Spin];
        if !s.car_insured {
            o.push(Opt::CarInsurance);
        }
        if s.house.is_some() && !s.home_insured {
            o.push(Opt::HomeInsurance);
        }
        if s.stocks.is_empty() && !t.stocks.is_empty() {
            o.push(Opt::Stock);
        }
        if t.has_perk(p, Perk::Sport) && s.tokens.len() >= 4 && s.salary != Some(SALARIES.len() - 1) {
            o.push(Opt::SportTrade);
        }
        let all_working = t.seats.iter().all(|x| x.career.is_some() || x.retired.is_some());
        if s.career.is_some_and(|c| CAREERS[c].perk == Perk::Teaching) && s.second_career.is_none() && all_working && !t.careers.is_empty() {
            o.push(Opt::SecondCareer);
        }
        o
    }

    fn walk(&mut self, p: usize, steps: u8, branch: Option<u8>) {
        let t = &mut self.table;
        // Berufswahl stops only who has no career yet (the Ausbildung players drew theirs at Start).
        let skip = t.seats[p].career.is_some().then(|| t.board.fields.iter().position(|f| f.name == "Berufswahl")).flatten();
        let w = walk(&t.board, t.seats[p].field, steps, branch.map(usize::from), skip);
        t.seats[p].field = w.to;
        if let Some(s) = t.seats[p].salary {
            t.seats[p].cash += SALARIES[s].pay * i64::from(w.paydays);
        }
        match w.halt {
            Halt::Landed => self.then(Step::Land),
            Halt::Stop => self.stop(p),
            Halt::Home => self.retire(p),
            Halt::Fork { left } => {
                self.left = left;
                self.ask = Some(Ask { player: p, question: Question::Fork, options: vec![Opt::Branch(0), Opt::Branch(1)] });
            }
        }
    }

    /// Spins (again while the spinner stops between numbers), pays out what a
    /// spin pays, then moves.
    fn spin(&mut self, p: usize) {
        let n = loop {
            match self.table.spin() {
                Some(n) => break n,
                None => {
                    if let Some(o) = self.table.perk_owner(Perk::Repair).filter(|&o| o != p) {
                        self.table.seats[o].cash += self.table.rules.repair_fee;
                    }
                    self.table.say(p, "Die Scheibe bleibt zwischen zwei Zahlen stehen");
                }
            }
        };
        self.table.say(p, format!("dreht eine {n}"));
        self.spun(p, n);
        self.then(Step::Move { steps: n, branch: None });
    }

    pub fn answer(&mut self, choice: usize) {
        let Some(ask) = self.ask.take() else { return };
        let (p, opt) = (ask.player, ask.options[choice.min(ask.options.len() - 1)]);
        match (ask.question, opt) {
            (Question::Turn, Opt::Spin) => self.spin(p),
            (Question::Turn, o) => {
                self.buy(p, o);
                self.then(Step::Turn { again: false });
            }
            (Question::Fork, Opt::Branch(b)) => {
                self.chose_branch(p, b);
                let left = self.left;
                self.then(Step::Move { steps: left, branch: Some(b) });
            }
            (Question::Career, Opt::Card(c)) => self.table.take(p, Deck::Career, c),
            (Question::Salary, Opt::Card(c)) => self.table.take(p, Deck::Salary, c),
            (q, o) => self.answered(p, q, o),
        }
        self.advance();
    }
}
