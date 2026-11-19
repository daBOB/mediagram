//! What a field, a STOP, a spin, a purchase or retirement does to the table
//! (2000 rulebook; the fan clone where the rulebook is silent, without its
//! defects: nobody pays their own career field or their own taxes).

use rand::seq::IndexedRandom;
use rand::Rng;

use super::cards::{Perk, HOUSES, SALARIES};
use super::rules::{Deck, Home};
use super::spaces::{space, Effect, NIGHT_SCHOOL};
use super::turn::{Game, Opt, Question, Step};

impl Game {
    pub(super) fn land(&mut self, p: usize) {
        let t = &mut self.table;
        let Some(id) = t.board.fields[t.seats[p].field].space else { return };
        let (effect, text) = space(id);
        t.say(p, text);
        match effect {
            Effect::Payday => {}
            Effect::Life => self.token(p),
            Effect::Baby(n) => {
                t.seats[p].children += n;
                self.token(p);
            }
            Effect::Collect(a) => t.seats[p].cash += a,
            Effect::Pay(a) => t.pay(p, a),
            Effect::Career(perk, a) => t.pay_to(p, t.perk_owner(perk), a),
            Effect::Taxes => {
                if let Some(s) = t.seats[p].salary {
                    t.pay_to(p, t.perk_owner(Perk::Taxes), SALARIES[s].taxes);
                }
            }
            Effect::Car(a) if !t.seats[p].car_insured => t.pay(p, a),
            Effect::Home(a) if t.seats[p].house.is_some() && !t.seats[p].home_insured => t.pay(p, a),
            Effect::Car(_) | Effect::Home(_) => {}
            Effect::MissTurn => t.seats[p].miss_turn = true,
            Effect::NightSchool => self.then(Step::Ask(Question::NightSchool, vec![Opt::Yes, Opt::No])),
            Effect::NewCareer => {
                let degree = t.seats[p].degree;
                for (deck, d) in [(Deck::Career, degree), (Deck::Salary, true)] {
                    if let Some(&c) = t.draw(deck, 1, d).first() {
                        t.take(p, deck, c);
                    }
                }
            }
            Effect::TradeSalary => {
                let mut o: Vec<Opt> = (0..t.seats.len()).filter(|&q| q != p && t.seats[q].salary.is_some()).map(Opt::Player).collect();
                if !o.is_empty() && t.seats[p].salary.is_some() {
                    o.push(Opt::Keep);
                    self.then(Step::Ask(Question::TradeSalary, o));
                }
            }
            Effect::SpinAgainIfBehind => {
                let mine = t.seats[p].cash;
                if t.seats.iter().enumerate().any(|(q, s)| q != p && s.cash >= mine) {
                    self.then(Step::Turn { again: true });
                }
            }
            Effect::StockBoom => {
                if let Some(&n) = t.stocks.choose(&mut t.rng) {
                    t.stocks.retain(|&x| x != n);
                    t.seats[p].stocks.push(n);
                }
            }
            Effect::StockCrash => {
                if let Some(n) = t.seats[p].stocks.pop() {
                    t.stocks.push(n);
                }
            }
        }
    }

    /// A life token from the pile; once it is empty, taken from another player.
    pub(super) fn token(&mut self, p: usize) {
        let t = &mut self.table;
        if let Some(tok) = t.pile.pop() {
            t.seats[p].tokens.push(tok);
            return;
        }
        let victims: Vec<Opt> = (0..t.seats.len())
            .filter(|&q| q != p && !t.seats[q].tokens.is_empty() && t.seats[q].retired != Some(Home::Country))
            .map(Opt::Player)
            .collect();
        if !victims.is_empty() {
            self.then(Step::Ask(Question::Steal, victims));
        }
    }

    pub(super) fn stop(&mut self, p: usize) {
        let name = self.table.board.fields[self.table.seats[p].field].name;
        self.table.say(p, name);
        self.then(Step::Turn { again: true });
        match name {
            "Berufswahl" => {
                let careers = self.table.draw(Deck::Career, 3, true);
                let salaries = self.table.draw(Deck::Salary, 3, true);
                self.then(Step::Pick { deck: Deck::Salary, from: salaries, keep_own: false });
                self.then(Step::Pick { deck: Deck::Career, from: careers, keep_own: false });
            }
            "Hochzeit" => {
                self.table.seats[p].partner = true;
                self.token(p);
            }
            "Hauskauf" => {
                let t = &mut self.table;
                if let Some(&h) = t.draw(Deck::House, 1, true).first() {
                    t.take(p, Deck::House, h);
                    t.pay(p, t.rules.once(HOUSES[h].price));
                    self.then(Step::Ask(Question::HomeInsurance, vec![Opt::Yes, Opt::No]));
                }
            }
            _ => {}
        }
    }

    /// Repays every loan with interest, hands the cards back and moves in.
    pub(super) fn retire(&mut self, p: usize) {
        let t = &mut self.table;
        let home = if t.board.fields[t.seats[p].field].branch == Some(0) { Home::Villa } else { Home::Country };
        let s = &mut t.seats[p];
        s.cash -= i64::from(s.loans) * (t.rules.loan + t.rules.interest);
        s.loans = 0;
        s.retired = Some(home);
        (s.home_insured, s.car_insured) = (false, false);
        let cards = [(s.career.take(), Deck::Career), (s.second_career.take(), Deck::Career), (s.salary.take(), Deck::Salary), (s.house.take(), Deck::House)];
        for (card, deck) in cards {
            if let Some(c) = card {
                match deck {
                    Deck::Career => t.careers.push(c),
                    Deck::Salary => t.salaries.push(c),
                    Deck::House => t.houses.push(c),
                }
            }
        }
        if home == Home::Country {
            self.token(p);
        }
        if self.table.seats.iter().all(|s| s.retired.is_some()) {
            self.award_estate();
        }
    }

    /// The set-aside tokens go to the richest at the Villa am See, shared on a tie.
    fn award_estate(&mut self) {
        let t = &mut self.table;
        let villa: Vec<usize> = (0..t.seats.len()).filter(|&q| t.seats[q].retired == Some(Home::Villa)).collect();
        let Some(top) = villa.iter().map(|&q| t.seats[q].cash).max() else { return };
        let winners: Vec<usize> = villa.into_iter().filter(|&q| t.seats[q].cash == top).collect();
        let share = t.estate.len() / winners.len();
        for q in winners {
            let got: Vec<usize> = t.estate.drain(..share).collect();
            t.seats[q].tokens.extend(got);
        }
    }

    pub(super) fn answered(&mut self, p: usize, q: Question, o: Opt) {
        let t = &mut self.table;
        match (q, o) {
            (Question::NightSchool, Opt::Yes) => {
                t.pay(p, NIGHT_SCHOOL);
                t.seats[p].degree = true;
                let careers = t.draw(Deck::Career, 2, true);
                let salaries = t.draw(Deck::Salary, 2, true);
                self.then(Step::Pick { deck: Deck::Salary, from: salaries, keep_own: true });
                self.then(Step::Pick { deck: Deck::Career, from: careers, keep_own: true });
            }
            (Question::TradeSalary, Opt::Player(q)) => (t.seats[p].salary, t.seats[q].salary) = (t.seats[q].salary, t.seats[p].salary),
            (Question::Steal, Opt::Player(q)) => {
                let i = t.rng.random_range(0..t.seats[q].tokens.len());
                let tok = t.seats[q].tokens.swap_remove(i);
                t.seats[p].tokens.push(tok);
            }
            (Question::HomeInsurance, Opt::Yes) => self.buy(p, Opt::HomeInsurance),
            _ => {}
        }
    }
}
