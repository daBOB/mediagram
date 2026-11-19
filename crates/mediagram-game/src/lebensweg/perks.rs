//! What a spin pays (stocks and the spin perks), what a player buys at the
//! start of a turn, and the first choice of life at Start.

use rand::seq::IndexedRandom;

use super::cards::{Perk, HOUSES, SALARIES};
use super::rules::Deck;
use super::turn::{Game, Opt};

const TOP_SALARY: usize = SALARIES.len() - 1;

impl Game {
    /// What a spin pays, whoever spun it: stocks, Kunst on a 1, Polizei on a 10, Bühne's streak.
    pub(super) fn spun(&mut self, p: usize, n: u8) {
        let t = &mut self.table;
        let payout = t.rules.stock_payout;
        for s in t.seats.iter_mut().filter(|s| s.stocks.contains(&n)) {
            s.cash += payout;
        }
        let fine = match n {
            1 => t.perk_owner(Perk::Art).map(|o| (o, t.rules.art_sale)),
            10 => t.perk_owner(Perk::Police).map(|o| (o, t.rules.police_fine)),
            _ => None,
        };
        if let Some((o, a)) = fine.filter(|&(o, _)| o != p) {
            t.pay_to(p, Some(o), a);
        }
        let spins = &mut t.seats[p].spins;
        spins.push(n);
        let streak = n >= 8 && spins.len() >= 2 && spins[spins.len() - 2] == n;
        if streak && t.has_perk(p, Perk::Stage) {
            self.top_salary(p);
        }
    }

    /// Bühne and Profisport reach for the top salary: from the deck, or swapped with whoever holds it.
    pub(super) fn top_salary(&mut self, p: usize) {
        let t = &mut self.table;
        match t.seats.iter().position(|s| s.salary == Some(TOP_SALARY)) {
            Some(q) if q == p => {}
            Some(q) => (t.seats[p].salary, t.seats[q].salary) = (t.seats[q].salary, t.seats[p].salary),
            None => t.take(p, Deck::Salary, TOP_SALARY),
        }
    }

    pub(super) fn buy(&mut self, p: usize, o: Opt) {
        let t = &mut self.table;
        let price = match o {
            Opt::CarInsurance => {
                t.seats[p].car_insured = true;
                t.rules.once(t.rules.car_insurance)
            }
            Opt::HomeInsurance => {
                t.seats[p].home_insured = true;
                t.seats[p].house.map_or(0, |h| t.rules.once(HOUSES[h].insurance))
            }
            Opt::Stock => {
                let Some(&n) = t.stocks.choose(&mut t.rng) else { return };
                t.stocks.retain(|&x| x != n);
                t.seats[p].stocks.push(n);
                t.rules.once(t.rules.stock_price)
            }
            Opt::SportTrade => {
                let back: Vec<usize> = t.seats[p].tokens.drain(..4).collect();
                t.pile.extend(back);
                self.top_salary(p);
                return;
            }
            Opt::SecondCareer => {
                if let Some(&c) = t.draw(Deck::Career, 1, true).first() {
                    t.careers.retain(|&x| x != c);
                    t.seats[p].second_career = Some(c);
                }
                return;
            }
            _ => return,
        };
        t.pay(p, price);
        if let Some(o) = t.perk_owner(Perk::Sales).filter(|&o| o != p) {
            t.seats[o].cash += t.rules.commission;
        }
    }

    /// Start's fork is the first choice of life: Studium costs tuition and opens
    /// the degree careers, Ausbildung draws a career and salary right away.
    pub(super) fn chose_branch(&mut self, p: usize, b: u8) {
        let t = &mut self.table;
        if t.seats[p].field != t.board.start() {
            return;
        }
        if b == 0 {
            t.seats[p].degree = true;
            t.pay(p, t.rules.once(t.rules.tuition));
            return;
        }
        for deck in [Deck::Career, Deck::Salary] {
            if let Some(&c) = t.draw(deck, 1, false).first() {
                t.take(p, deck, c);
            }
        }
    }
}
