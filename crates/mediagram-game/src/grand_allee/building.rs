//! Building in Grand Allee: the permit die, what named stages cost under it,
//! the leisure wing that needs no permit, and placing entrances.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use super::board::HOTELS;
use super::rules::Table;

/// The permit die: three in six approve, one each rejects, waives or doubles.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export, rename = "GrandAlleePermit")]
pub enum Permit {
    Approved,
    Rejected,
    /// The first named stage is free.
    Free,
    /// Every named stage costs double.
    Double,
}

pub fn permit_for(die: u8) -> Permit {
    match die {
        1..=3 => Permit::Approved,
        4 => Permit::Rejected,
        5 => Permit::Free,
        _ => Permit::Double,
    }
}

impl Table {
    /// Stages that need a permit: all but the leisure wing.
    pub fn permit_stages_left(&self, hotel: u8) -> u8 {
        (HOTELS[usize::from(hotel)].stages.len() as u8 - 1).saturating_sub(self.estate(hotel).levels)
    }

    /// Price of the next `count` stages under `permit` (Rejected costs nothing and builds nothing).
    pub fn stage_price(&self, hotel: u8, count: u8, permit: Permit) -> i32 {
        let from = usize::from(self.estate(hotel).levels);
        let costs = &HOTELS[usize::from(hotel)].stages[from..from + usize::from(count)];
        let sum: i32 = costs.iter().map(|s| s.0).sum();
        match permit {
            Permit::Approved => sum,
            Permit::Free => sum - costs.first().map_or(0, |s| s.0),
            Permit::Double => sum * 2,
            Permit::Rejected => 0,
        }
    }

    /// Builds `count` stages for `price`; true when this was the main building,
    /// which grants the hotel's first entrance.
    pub fn build(&mut self, seat: usize, hotel: u8, count: u8, price: i32) -> bool {
        self.seats[seat].cash -= price;
        let estate = &mut self.estates[usize::from(hotel)];
        let first = estate.levels == 0 && count > 0;
        estate.levels += count;
        first
    }

    /// The leisure wing's cost when it is the only stage left (no permit needed).
    pub fn leisure_cost(&self, hotel: u8) -> Option<i32> {
        let stages = HOTELS[usize::from(hotel)].stages;
        (usize::from(self.estate(hotel).levels) == stages.len() - 1).then(|| stages[stages.len() - 1].0)
    }

    /// Frontage fields of a built hotel with no entrance yet.
    pub fn free_frontage(&self, hotel: u8) -> Vec<u8> {
        if self.estate(hotel).levels == 0 {
            return Vec::new();
        }
        HOTELS[usize::from(hotel)].frontage_fields().filter(|&f| self.entrances[usize::from(f)].is_none()).collect()
    }

    pub fn place_entrance(&mut self, seat: usize, hotel: u8, field: u8, price: i32) {
        self.seats[seat].cash -= price;
        self.entrances[usize::from(field)] = Some(hotel);
    }
}
