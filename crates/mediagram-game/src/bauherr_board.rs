//! Bauherr's board as data: a closed ring of 36 fields around a town, with
//! eight hotel plots inside it. Nothing here is geometry the rules rely on;
//! they read exits and bordering fields only, so another board is another table.

use crate::protocol::{BauherrBoard, BauherrLevel, BauherrPlot, BauherrTile, BauherrTileKind as K};

const SIDE: u8 = 10;
pub const FIELDS: u8 = 4 * (SIDE - 1);

/// Field kinds by id: B(ank), R(athaus), P bauplatz, S baustelle, F reier Eingang, . plain.
const KINDS: &str = "BP.P.S..SF.PP.S..SR.PP.S..SF.PP.S..S";

struct Hotel {
    name: &'static str,
    rect: (u8, u8, u8, u8),
    fields: [u8; 4],
    land: i32,
    entrance: i32,
    leisure: &'static str,
    costs: &'static [i32],
    rates: &'static [i32],
}

/// Own invented hotels, cheap and quick to repay up to dear and big.
const HOTELS: [Hotel; 8] = [
    Hotel { name: "Kastanienhof", rect: (1, 1, 2, 2), fields: [1, 2, 35, 34], land: 1000, entrance: 300, leisure: "Biergarten", costs: &[1200, 900, 900], rates: &[150, 300, 450] },
    Hotel { name: "Seeblick", rect: (3, 1, 4, 2), fields: [3, 4, 5, 6], land: 2000, entrance: 500, leisure: "Seeterrasse", costs: &[2000, 1500, 1500, 1200], rates: &[200, 400, 650, 850] },
    Hotel { name: "Waldhaus", rect: (7, 1, 2, 2), fields: [7, 8, 10, 11], land: 1300, entrance: 350, leisure: "Saunahaus", costs: &[1400, 1000, 1000, 800], rates: &[150, 300, 450, 600] },
    Hotel { name: "Stadtpalais", rect: (7, 3, 2, 4), fields: [12, 13, 14, 15], land: 3500, entrance: 800, leisure: "Dachgarten", costs: &[2800, 2200, 2200, 1800, 1600, 1400], rates: &[300, 600, 850, 1100, 1300, 1500] },
    Hotel { name: "Alpenrose", rect: (7, 7, 2, 2), fields: [16, 17, 19, 20], land: 1800, entrance: 450, leisure: "Wellnessgarten", costs: &[1800, 1200, 1200, 1000], rates: &[200, 400, 600, 750] },
    Hotel { name: "Sternwarte", rect: (3, 7, 4, 2), fields: [21, 22, 23, 24], land: 3000, entrance: 700, leisure: "Planetarium", costs: &[2800, 2000, 2000, 1800, 1600, 1400], rates: &[250, 550, 850, 1150, 1350, 1500] },
    Hotel { name: "Kurhaus", rect: (1, 7, 2, 2), fields: [25, 26, 28, 29], land: 2300, entrance: 550, leisure: "Thermalbad", costs: &[2200, 1600, 1600, 1400, 1200], rates: &[250, 500, 750, 1000, 1150] },
    Hotel { name: "Leuchtturm", rect: (1, 3, 2, 4), fields: [30, 31, 32, 33], land: 2600, entrance: 600, leisure: "Aussichtsdeck", costs: &[2400, 1800, 1800, 1600, 1400], rates: &[250, 500, 800, 1050, 1300] },
];

/// Grid position of ring field `id`, clockwise from the top left corner.
fn ring(id: u8) -> (u8, u8) {
    let last = SIDE - 1;
    match id {
        0..=9 => (id, 0),
        10..=18 => (last, id - last),
        19..=27 => (last - (id - 2 * last), last),
        _ => (0, last - (id - 3 * last)),
    }
}

fn kind(c: char) -> K {
    match c {
        'B' => K::Bank,
        'R' => K::Rathaus,
        'P' => K::Bauplatz,
        'S' => K::Baustelle,
        'F' => K::Eingang,
        _ => K::Strasse,
    }
}

fn levels(h: &Hotel) -> Vec<BauherrLevel> {
    let n = h.costs.len();
    (0..n)
        .map(|i| {
            let name = match i {
                0 => "Hauptgebäude".to_string(),
                _ if i == n - 1 => h.leisure.to_string(),
                _ if n == 3 => "Anbau".to_string(),
                _ => format!("{}. Anbau", i),
            };
            BauherrLevel { name, cost: h.costs[i], rate: h.rates[i] }
        })
        .collect()
}

pub fn board() -> BauherrBoard {
    let tiles = KINDS
        .chars()
        .enumerate()
        .map(|(i, c)| {
            let id = i as u8;
            let (x, y) = ring(id);
            BauherrTile { id, kind: kind(c), x, y, exits: vec![(id + 1) % FIELDS] }
        })
        .collect();
    let plots = HOTELS
        .iter()
        .enumerate()
        .map(|(i, h)| BauherrPlot {
            id: i as u8,
            name: h.name.into(),
            x: h.rect.0,
            y: h.rect.1,
            w: h.rect.2,
            h: h.rect.3,
            fields: h.fields.to_vec(),
            land: h.land,
            entrance: h.entrance,
            levels: levels(h),
        })
        .collect();
    BauherrBoard { tiles, plots }
}

#[cfg(test)]
#[path = "bauherr_board_tests.rs"]
mod tests;
