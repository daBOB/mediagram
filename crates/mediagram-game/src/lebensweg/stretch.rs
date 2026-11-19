//! Which spaces come twice on a stretched board. Each stretch keeps its
//! length (`round(m × more)`); the copies are picked one at a time so that every
//! kind of space keeps its running total at `more` × the original's along the
//! board: paydays and life fields by count, money fields by amount, each
//! career's fields apart. Bots measured why each part is needed: copies spread
//! by position gave a fork branch a second payday (+11 points), copies counted
//! instead of weighed doubled a career's 125,000 field (+15).

use std::collections::HashMap;

use super::cards::Perk;
use super::spaces::{space, Effect};

/// What makes two spaces the same kind for the balance: the effect, and for a
/// career field whose career collects.
type Class = (u8, Option<Perk>);

fn class(e: Effect) -> Class {
    let perk = if let Effect::Career(p, _) = e { Some(p) } else { None };
    let tag = match e {
        Effect::Payday => 0,
        Effect::Life => 1,
        Effect::Baby(_) => 2,
        Effect::Collect(_) => 3,
        Effect::Pay(_) => 4,
        Effect::Career(..) => 5,
        Effect::Taxes => 6,
        Effect::Car(_) => 7,
        Effect::Home(_) => 8,
        Effect::MissTurn => 9,
        Effect::NightSchool => 10,
        Effect::NewCareer => 11,
        Effect::TradeSalary => 12,
        Effect::SpinAgainIfBehind => 13,
        Effect::StockBoom => 14,
        Effect::StockCrash => 15,
    };
    (tag, perk)
}

/// Money fields weigh their amount, every other space one.
fn weight(e: Effect) -> f64 {
    match e {
        Effect::Collect(a) | Effect::Pay(a) | Effect::Career(_, a) | Effect::Car(a) | Effect::Home(a) => a as f64,
        _ => 1.0,
    }
}

pub struct Stretcher {
    more: f64,
    total: HashMap<Class, f64>,
    /// Weight of the original's spaces passed so far, and of the fields made from them.
    seen: HashMap<Class, f64>,
    made: HashMap<Class, f64>,
}

impl Stretcher {
    /// `stretches` are every stretch of the board, in board order.
    pub fn new(more: f64, stretches: &[(u8, u8)]) -> Self {
        let mut total = HashMap::new();
        for &(a, b) in stretches {
            for id in a..=b {
                let e = space(id).0;
                *total.entry(class(e)).or_insert(0.0) += weight(e);
            }
        }
        Self { more, total, seen: HashMap::new(), made: HashMap::new() }
    }

    pub fn stretch(&mut self, range: (u8, u8)) -> Vec<u8> {
        self.place(&[range]).remove(0)
    }

    /// A fork's two branches, placed together; a branch's payday never comes
    /// twice (one extra payday on one branch made it win 11 points more).
    pub fn stretch_pair(&mut self, a: (u8, u8), b: (u8, u8)) -> [Vec<u8>; 2] {
        let mut v = self.place(&[a, b]);
        let second = v.remove(1);
        [v.remove(0), second]
    }

    fn place(&mut self, ranges: &[(u8, u8)]) -> Vec<Vec<u8>> {
        let ids: Vec<Vec<u8>> = ranges.iter().map(|&(a, b)| (a..=b).collect()).collect();
        let mut extra: Vec<usize> = ids.iter().map(|v| ((v.len() as f64 * self.more).round() as usize).max(1) - v.len()).collect();
        for &id in ids.iter().flatten() {
            let e = space(id).0;
            *self.seen.entry(class(e)).or_insert(0.0) += weight(e);
            *self.made.entry(class(e)).or_insert(0.0) += weight(e);
        }
        let fork = ids.len() == 2;
        let mut twice: Vec<u8> = Vec::new();
        while let Some(r) = (0..ids.len()).find(|&r| extra[r] > 0) {
            let pick = ids[r]
                .iter()
                .copied()
                .filter(|id| !twice.contains(id) && !(fork && space(*id).0 == Effect::Payday))
                .min_by(|&x, &y| self.gain(x).total_cmp(&self.gain(y)).then(spread(&ids[r], &twice, y).cmp(&spread(&ids[r], &twice, x))));
            let Some(id) = pick else { break };
            let e = space(id).0;
            *self.made.entry(class(e)).or_insert(0.0) += weight(e);
            twice.push(id);
            extra[r] -= 1;
        }
        ids.iter().map(|v| v.iter().flat_map(|&id| std::iter::repeat_n(id, 1 + usize::from(twice.contains(&id)))).collect()).collect()
    }

    /// How much a copy of `id` moves its class away from `more` × its share so
    /// far (negative: closer), relative to the class's total. Looking ahead to
    /// the board's end as well was tried: Profisport +12, IT-Beratung −10 points.
    fn gain(&self, id: u8) -> f64 {
        let e = space(id).0;
        let c = class(e);
        let target = self.more * self.seen[&c];
        let made = self.made[&c];
        ((made + weight(e) - target).abs() - (made - target).abs()) / (self.more * self.total[&c])
    }
}

/// Distance to the nearest space already doubled in this stretch: ties go to
/// the space farthest from the other copies.
fn spread(ids: &[u8], twice: &[u8], id: u8) -> usize {
    twice.iter().filter(|t| ids.contains(t)).map(|&t| usize::from(t.abs_diff(id))).min().unwrap_or(usize::MAX)
}
