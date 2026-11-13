//! Flüchtig's cities, each with a river, bridges and a ferry along it. The big
//! city is the one the room plays: 199 fields, read from
//! `cities/fluechtig-big-city.json`. The small city, 48 fields numbered row by
//! row with the U-Bahn crossing at field 21, is built here for the short chase.

use std::sync::LazyLock;

use serde::Deserialize;

use crate::protocol::FluechtigTicket as Ticket;

pub(crate) type Field = u8;

#[derive(Debug)]
pub(crate) struct Link {
    pub a: Field,
    pub b: Field,
    pub kind: Ticket,
}

#[derive(Debug)]
pub(crate) struct City {
    /// The plan's width and height; `spots` and `river` lie on it.
    pub size: (i16, i16),
    /// `spots[field - 1]`: where the field sits on the plan.
    pub spots: Vec<(i16, i16)>,
    pub links: Vec<Link>,
    /// The middle of the river from west to east, past both edges of the plan.
    pub river: Vec<(i16, i16)>,
    /// Where hunter pieces start, drawn in this order's shuffle.
    pub hunter_starts: Vec<Field>,
    /// Where the fugitive may start; the room knows only this list.
    pub fugitive_starts: Vec<Field>,
    exits: Vec<Vec<(Field, Ticket)>>,
}

impl City {
    /// Every link out of `from`, with its transport.
    pub fn exits(&self, from: Field) -> &[(Field, Ticket)] {
        &self.exits[usize::from(from - 1)]
    }

    fn new(size: (i16, i16), spots: Vec<(i16, i16)>, links: Vec<Link>, river: Vec<(i16, i16)>, starts: [Vec<Field>; 2]) -> Self {
        let mut exits = vec![Vec::new(); spots.len()];
        for l in &links {
            exits[usize::from(l.a - 1)].push((l.b, l.kind));
            exits[usize::from(l.b - 1)].push((l.a, l.kind));
        }
        let [hunter_starts, fugitive_starts] = starts;
        Self { size, spots, links, river, hunter_starts, fugitive_starts, exits }
    }
}

/// Which city a chase runs in.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CityKind {
    /// The lobby does not offer the short chase yet; the rules' tests play it.
    #[cfg_attr(not(test), allow(dead_code))]
    Small,
    Big,
}

impl CityKind {
    pub(crate) fn city(self) -> &'static City {
        match self {
            Self::Small => &SMALL,
            Self::Big => &BIG,
        }
    }
}

static SMALL: LazyLock<City> = LazyLock::new(small);
static BIG: LazyLock<City> = LazyLock::new(|| big(include_str!("../cities/fluechtig-big-city.json")));

/// The big city's file: every line as pairs of fields. `tools/fluechtig-city`
/// makes it in two steps: `generate_city.py`, then `add_bridges.py`, which
/// puts streets and buses over the river on six bridges only (regenerating
/// without it brings links over open water back). `balance_sim.py` measures
/// how evenly the two sides play on it.
#[derive(Deserialize)]
struct CityFile {
    size: (i16, i16),
    spots: Vec<(i16, i16)>,
    taxi: Vec<(Field, Field)>,
    bus: Vec<(Field, Field)>,
    ubahn: Vec<(Field, Field)>,
    ferry: Vec<(Field, Field)>,
    river: Vec<(i16, i16)>,
    hunter_starts: Vec<Field>,
    fugitive_starts: Vec<Field>,
}

fn big(json: &str) -> City {
    let f: CityFile = serde_json::from_str(json).expect("bundled city parses");
    // the ferry's links are of the shadow ride's own kind: no other ticket boards it
    let links = [(Ticket::Taxi, f.taxi), (Ticket::Bus, f.bus), (Ticket::Ubahn, f.ubahn), (Ticket::Schatten, f.ferry)]
        .into_iter()
        .flat_map(|(kind, pairs)| pairs.into_iter().map(move |(a, b)| Link { a, b, kind }))
        .collect();
    City::new(f.size, f.spots, links, f.river, [f.hunter_starts, f.fugitive_starts])
}

const COLS: u8 = 8;
const ROWS: u8 = 6;
/// Streets without a taxi: a park and two dead ends that give the city corners.
const NO_TAXI: [(Field, Field); 5] = [(11, 12), (36, 37), (5, 13), (38, 46), (20, 21)];
/// Columns where a taxi crosses the river.
const BRIDGES: [u8; 3] = [1, 4, 6];
const DIAGONALS: [(Field, Field); 5] = [(2, 11), (14, 23), (27, 36), (31, 40), (35, 44)];
const BUS_LINES: [&[Field]; 5] = [&[9, 11, 13, 15], &[34, 36, 38, 40], &[4, 21, 37], &[2, 18, 26, 42], &[7, 23, 31, 47]];
const UBAHN_LINES: [&[Field]; 2] = [&[9, 21, 47], &[7, 21, 42]];
/// The ferry's piers down the river, bank to bank where no bridge stands.
const FERRY: [&[Field]; 1] = [&[17, 27, 22, 32]];
/// The river between rows 3 and 4.
const RIVER: [(i16, i16); 15] = [
    (-240, 270), (0, 268), (80, 262), (160, 264), (240, 270), (320, 275), (400, 277), (480, 270),
    (560, 260), (640, 254), (720, 252), (800, 255), (880, 261), (960, 272), (1200, 272),
];
const HUNTER_STARTS: [Field; 8] = [3, 14, 19, 28, 35, 44, 16, 41];
const FUGITIVE_STARTS: [Field; 8] = [6, 10, 24, 27, 33, 46, 39, 12];

fn small() -> City {
    let id = |r: u8, c: u8| r * COLS + c + 1;
    let spots = (1..=COLS * ROWS)
        .map(|n| {
            let (r, c, n) = (i16::from((n - 1) / COLS), i16::from((n - 1) % COLS), i16::from(n));
            // a fixed jitter, so the grid reads as streets rather than graph paper
            (70 + c * 117 + (n * 37) % 21 - 10, 50 + r * 88 + (n * 53) % 17 - 8)
        })
        .collect::<Vec<_>>();
    let mut links = Vec::new();
    let mut taxi = |a: Field, b: Field| {
        if !NO_TAXI.contains(&(a, b)) {
            links.push(Link { a, b, kind: Ticket::Taxi });
        }
    };
    for r in 0..ROWS {
        for c in 0..COLS {
            if c + 1 < COLS {
                taxi(id(r, c), id(r, c + 1));
            }
            if r + 1 < ROWS && (r != 2 || BRIDGES.contains(&c)) {
                taxi(id(r, c), id(r + 1, c));
            }
        }
    }
    DIAGONALS.iter().for_each(|&(a, b)| taxi(a, b));
    for (kind, lines) in [(Ticket::Bus, &BUS_LINES[..]), (Ticket::Ubahn, &UBAHN_LINES[..]), (Ticket::Schatten, &FERRY[..])] {
        for stops in lines {
            links.extend(stops.windows(2).map(|w| Link { a: w[0], b: w[1], kind }));
        }
    }
    City::new((960, 540), spots, links, RIVER.to_vec(), [HUNTER_STARTS.to_vec(), FUGITIVE_STARTS.to_vec()])
}
