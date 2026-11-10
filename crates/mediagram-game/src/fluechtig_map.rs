//! Flüchtig's city: 48 fields numbered row by row on a 960×540 plan, a river
//! with three bridges between rows 3 and 4, and the U-Bahn crossing at field 21.

use std::sync::LazyLock;

use crate::protocol::FluechtigTicket as Ticket;

pub(crate) type Field = u8;

const COLS: u8 = 8;
const ROWS: u8 = 6;
/// Streets without a taxi: a park and two dead ends that give the city corners.
const NO_TAXI: [(Field, Field); 5] = [(11, 12), (36, 37), (5, 13), (38, 46), (20, 21)];
/// Columns where a taxi crosses the river.
const BRIDGES: [u8; 3] = [1, 4, 6];
const DIAGONALS: [(Field, Field); 5] = [(2, 11), (14, 23), (27, 36), (31, 40), (35, 44)];
const BUS_LINES: [&[Field]; 5] = [&[9, 11, 13, 15], &[34, 36, 38, 40], &[4, 21, 37], &[2, 18, 26, 42], &[7, 23, 31, 47]];
const UBAHN_LINES: [&[Field]; 2] = [&[9, 21, 47], &[7, 21, 42]];

/// Where hunter pieces start, drawn in this order's shuffle.
pub(crate) const HUNTER_STARTS: [Field; 8] = [3, 14, 19, 28, 35, 44, 16, 41];
/// Where the fugitive may start; the room knows only this list.
pub(crate) const FUGITIVE_STARTS: [Field; 8] = [6, 10, 24, 27, 33, 46, 39, 12];

pub(crate) struct Link {
    pub a: Field,
    pub b: Field,
    pub kind: Ticket,
}

pub(crate) struct City {
    /// `spots[field - 1]`: where the field sits on the plan.
    pub spots: Vec<(i16, i16)>,
    pub links: Vec<Link>,
    exits: Vec<Vec<(Field, Ticket)>>,
}

impl City {
    /// Every link out of `from`, with its transport.
    pub fn exits(&self, from: Field) -> &[(Field, Ticket)] {
        &self.exits[usize::from(from - 1)]
    }
}

pub(crate) static CITY: LazyLock<City> = LazyLock::new(build);

fn build() -> City {
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
    for (kind, lines) in [(Ticket::Bus, &BUS_LINES[..]), (Ticket::Ubahn, &UBAHN_LINES[..])] {
        for stops in lines {
            links.extend(stops.windows(2).map(|w| Link { a: w[0], b: w[1], kind }));
        }
    }
    let mut exits = vec![Vec::new(); spots.len()];
    for l in &links {
        exits[usize::from(l.a - 1)].push((l.b, l.kind));
        exits[usize::from(l.b - 1)].push((l.a, l.kind));
    }
    City { spots, links, exits }
}
