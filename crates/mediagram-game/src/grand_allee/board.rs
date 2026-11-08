//! Grand Allee's board as data: a closed oval of 40 fields with ten hotels on
//! both sides of its two straights. Hotels, prices and names are our own.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

/// Fields on the loop, clockwise from the Bank.
pub const FIELDS: u8 = 40;
pub const BANK: u8 = 0;
pub const RATHAUS: u8 = 20;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export, rename = "GrandAlleeFieldKind")]
pub enum Field {
    Bank,
    Rathaus,
    /// Grundstück: buy a bordering free plot or force the sale of an unbuilt one.
    Plot,
    /// Baustelle: apply for a permit at an own bordering hotel.
    Site,
    /// Freier Eingang: one entrance free.
    FreeEntrance,
    /// Gratis bauen: one stage free, no permit.
    FreeBuild,
    Allee,
}

/// Field kinds by id: B Bank, R Rathaus, P Grundstück, S Baustelle, E Freier
/// Eingang, G Gratis bauen, . Allee. West curve 0–3, north 4–19, east 20–23, south 24–39.
const KINDS: &[u8; 40] = b"B.GEP.S..P.SP.S.P.S.R.GEP.S..P.SP.S.P.S.";

pub fn field(id: u8) -> Field {
    match KINDS[usize::from(id % FIELDS)] {
        b'B' => Field::Bank,
        b'R' => Field::Rathaus,
        b'P' => Field::Plot,
        b'S' => Field::Site,
        b'E' => Field::FreeEntrance,
        b'G' => Field::FreeBuild,
        _ => Field::Allee,
    }
}

pub struct Hotel {
    pub name: &'static str,
    /// First and last field the hotel borders (inclusive).
    pub frontage: (u8, u8),
    pub land: i32,
    pub entrance: i32,
    /// Per stage: (cost, nightly rate once built). The last is the leisure wing.
    /// Rates are half the first draft's: full rates ended most games by bankruptcy within 16–27 min (simulation, 2026-11-08).
    pub stages: &'static [(i32, i32)],
}

impl Hotel {
    pub fn borders(&self, field: u8) -> bool {
        (self.frontage.0..=self.frontage.1).contains(&field)
    }
    pub fn frontage_fields(&self) -> impl Iterator<Item = u8> {
        self.frontage.0..=self.frontage.1
    }
}

/// Ten world-themed hotels with our own names and designs (user, 2026-11-08):
/// sea side N1–N3 and town side S1–S3 outside the loop, park P1–P4 inside it.
pub const HOTELS: [Hotel; 10] = [
    Hotel { name: "Lagune", frontage: (4, 8), land: 1200, entrance: 300, stages: &[(1200, 75), (900, 150), (900, 225)] },
    Hotel { name: "Kirschblüte", frontage: (9, 13), land: 2000, entrance: 500, stages: &[(2000, 100), (1500, 200), (1500, 325), (1200, 425)] },
    Hotel { name: "Fjordhus", frontage: (14, 19), land: 2600, entrance: 600, stages: &[(2400, 125), (1800, 250), (1800, 400), (1600, 525), (1400, 650)] },
    Hotel { name: "Hacienda Sol", frontage: (24, 28), land: 1000, entrance: 300, stages: &[(1200, 75), (900, 150), (900, 225)] },
    Hotel { name: "Riad Atlas", frontage: (29, 33), land: 1300, entrance: 350, stages: &[(1400, 75), (1000, 150), (1000, 225), (800, 300)] },
    Hotel { name: "Alpenrose", frontage: (34, 39), land: 2300, entrance: 550, stages: &[(2200, 125), (1600, 250), (1600, 375), (1400, 500), (1200, 575)] },
    Hotel { name: "Maharadscha", frontage: (4, 11), land: 3000, entrance: 700, stages: &[(2800, 125), (2000, 275), (2000, 425), (1800, 575), (1600, 675), (1400, 750)] },
    Hotel { name: "Skyline", frontage: (12, 19), land: 3500, entrance: 800, stages: &[(2800, 150), (2200, 300), (2200, 425), (1800, 550), (1600, 650), (1400, 750)] },
    Hotel { name: "Savanne", frontage: (24, 31), land: 2800, entrance: 650, stages: &[(2600, 125), (1900, 250), (1900, 400), (1700, 525), (1500, 650)] },
    Hotel { name: "Belle Époque", frontage: (32, 39), land: 3200, entrance: 750, stages: &[(2800, 150), (2100, 300), (2100, 425), (1800, 550), (1600, 650), (1400, 750)] },
];

/// Hotels whose frontage includes `field` (none, one, or one per side).
pub fn hotels_at(field: u8) -> impl Iterator<Item = u8> {
    (0..HOTELS.len() as u8).filter(move |&h| HOTELS[usize::from(h)].borders(field))
}
