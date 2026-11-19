//! Lebensweg's board as data: the life path up the Lebensberg, from Start to
//! one of two retirement homes, with forks where the path splits and rejoins.
//! Its spaces are the original's, in order (`spaces.rs`); `more` stretches
//! every stretch by one factor for a longer game, repeating spaces evenly.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::spaces::{self, Effect};
use super::stretch::Stretcher;

/// The factor the user's board uses: more fields, same mix (2026-11-12).
pub const MORE: f64 = 1.3;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export, rename = "LebenswegFieldKind")]
pub enum Kind {
    /// A plain event: the text decides (miss a turn, spin again, …).
    Event,
    /// Zahltag: the salary, also when passing.
    Payday,
    /// A life token.
    Life,
    /// A child joins the car.
    Baby,
    /// Collect money from the bank.
    Collect,
    /// Pay money to the bank.
    Pay,
    /// Every car stops here, whatever it spun.
    Stop,
}

/// One stretch of the original board, before `more` stretches it. Spaces are
/// the original's numbers, inclusive (`spaces.rs`).
enum Part {
    Stop { name: &'static str, stage: u8, age: u8 },
    Run { name: &'static str, stage: u8, spaces: (u8, u8), age_end: Option<u8> },
    /// Two branches that rejoin, or the two retirement homes.
    Fork { stage: u8, branches: [Branch; 2] },
}

struct Branch {
    name: &'static str,
    /// None for a retirement home.
    spaces: Option<(u8, u8)>,
    age: Option<u8>,
}

/// The original's 147 spaces in our stretches (research §14.1); the clone's
/// fork spaces 48 and 84 are the last fields before each fork.
const PARTS: &[Part] = &[
    Part::Stop { name: "Start", stage: 0, age: 18 },
    Part::Fork { stage: 0, branches: [
        Branch { name: "Studium", spaces: Some((1, 10)), age: None },
        Branch { name: "Ausbildung", spaces: Some((12, 14)), age: None },
    ] },
    Part::Stop { name: "Berufswahl", stage: 0, age: 22 },
    Part::Run { name: "Beruf", stage: 1, spaces: (15, 24), age_end: None },
    Part::Stop { name: "Hochzeit", stage: 1, age: 27 },
    Part::Run { name: "Erste Wohnung", stage: 1, spaces: (26, 35), age_end: None },
    Part::Stop { name: "Hauskauf", stage: 1, age: 31 },
    Part::Run { name: "Kinder", stage: 2, spaces: (37, 48), age_end: Some(37) },
    Part::Fork { stage: 2, branches: [
        Branch { name: "Familienweg", spaces: Some((49, 55)), age: None },
        Branch { name: "Abenteuerweg", spaces: Some((56, 61)), age: None },
    ] },
    Part::Run { name: "Lebensmitte", stage: 3, spaces: (62, 80), age_end: Some(47) },
    Part::Run { name: "Kreuzung", stage: 3, spaces: (82, 84), age_end: None },
    Part::Fork { stage: 3, branches: [
        Branch { name: "Riskanter Weg", spaces: Some((85, 91)), age: None },
        Branch { name: "Sicherer Weg", spaces: Some((92, 95)), age: None },
    ] },
    Part::Run { name: "Goldene Jahre", stage: 4, spaces: (96, 121), age_end: Some(57) },
    Part::Run { name: "Ruhestand naht", stage: 5, spaces: (122, 146), age_end: Some(65) },
    Part::Fork { stage: 5, branches: [
        Branch { name: "Villa am See", spaces: None, age: Some(67) },
        Branch { name: "Landhaus", spaces: None, age: Some(67) },
    ] },
];

pub const STAGES: [&str; 6] = ["Jugend", "Beruf & Heim", "Familie", "Lebensmitte", "Goldene Jahre", "Ruhestand"];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Field {
    pub kind: Kind,
    /// The original space this field repeats (`spaces.rs`); None on STOPs.
    pub space: Option<u8>,
    pub stage: u8,
    /// The stretch, branch or STOP the field belongs to.
    pub name: &'static str,
    /// 0 or 1 on a fork's branch.
    pub branch: Option<u8>,
    /// The age at this field, where the board fixes one (STOPs, ends of stretches).
    pub age: Option<u8>,
    /// Where a car goes next: two fields at a fork, none at a retirement home.
    pub next: Vec<usize>,
}

#[derive(Debug, Clone)]
pub struct Board {
    pub fields: Vec<Field>,
}

impl Board {
    /// The original board stretched by `more` (1.0 = the original's field count).
    pub fn new(more: f64) -> Self {
        let stretches: Vec<(u8, u8)> = PARTS.iter().flat_map(|p| match p {
            Part::Run { spaces, .. } => vec![*spaces],
            Part::Fork { branches, .. } => branches.iter().filter_map(|b| b.spaces).collect(),
            Part::Stop { .. } => vec![],
        }).collect();
        let mut st = Stretcher::new(more, &stretches);
        let mut fields: Vec<Field> = Vec::new();
        let mut tails: Vec<usize> = Vec::new();
        let stop = |name, stage, age| Field { kind: Kind::Stop, space: None, stage, name, branch: None, age, next: vec![] };
        for part in PARTS {
            match part {
                Part::Stop { name, stage, age } => tails = vec![push(&mut fields, &tails, stop(*name, *stage, Some(*age)))],
                Part::Run { name, stage, spaces, age_end } => {
                    let ids = st.stretch(*spaces);
                    let n = ids.len();
                    for (i, id) in ids.into_iter().enumerate() {
                        let mut f = field(id, name, *stage, None);
                        f.age = if i + 1 == n { *age_end } else { None };
                        tails = vec![push(&mut fields, &tails, f)];
                    }
                }
                Part::Fork { stage, branches } => {
                    let from = std::mem::take(&mut tails);
                    let ids = match (branches[0].spaces, branches[1].spaces) {
                        (Some(a), Some(b)) => st.stretch_pair(a, b).map(Some),
                        _ => [None, None],
                    };
                    for (b, (branch, ids)) in branches.iter().zip(ids).enumerate() {
                        let mut prev = from.clone();
                        match ids {
                            Some(ids) => {
                                for id in ids {
                                    prev = vec![push(&mut fields, &prev, field(id, branch.name, *stage, Some(b as u8)))];
                                }
                            }
                            None => {
                                let mut home = stop(branch.name, *stage, None);
                                home.branch = Some(b as u8);
                                prev = vec![push(&mut fields, &prev, home)];
                            }
                        }
                        fields[prev[0]].age = branch.age;
                        tails.push(prev[0]);
                    }
                }
            }
        }
        Self { fields }
    }

    pub fn start(&self) -> usize {
        0
    }

    /// The fields a car may stop on at the end of the game.
    pub fn is_end(&self, field: usize) -> bool {
        self.fields[field].next.is_empty()
    }
}

/// Appends `field` behind every field in `from` and returns its id.
fn push(fields: &mut Vec<Field>, from: &[usize], field: Field) -> usize {
    let id = fields.len();
    for &t in from {
        fields[t].next.push(id);
    }
    fields.push(field);
    id
}

fn field(space: u8, name: &'static str, stage: u8, branch: Option<u8>) -> Field {
    let kind = match spaces::space(space).0 {
        Effect::Payday => Kind::Payday,
        Effect::Life => Kind::Life,
        Effect::Baby(_) => Kind::Baby,
        Effect::Collect(_) => Kind::Collect,
        Effect::Pay(_) | Effect::Career(..) | Effect::Taxes | Effect::Car(_) | Effect::Home(_) => Kind::Pay,
        _ => Kind::Event,
    };
    Field { kind, space: Some(space), stage, name, branch, age: None, next: vec![] }
}
