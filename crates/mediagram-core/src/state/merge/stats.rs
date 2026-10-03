//! The viewing-stats half of `merge_states`: per viewer, the newest copy of
//! each device's row per title and per day. A device writes only rows that
//! name it, so keeping one per (key, device) and summing across devices can
//! never count a minute twice. Split out to keep `merge.rs` under the line
//! limit.

use std::collections::HashMap;

use super::tie_break::{Held, keep};
use crate::state::record::{DayStatRow, ProfileState, TitleStatRow};

#[derive(Default)]
pub(super) struct Kept {
    titles: HashMap<String, Held<TitleStatRow>>,
    days: HashMap<String, Held<DayStatRow>>,
}

/// Folds one device's document in; an exact tie goes to the greater
/// document device id, as for every other kept row.
pub(super) fn absorb(into: &mut Kept, profile: &ProfileState, device: &str) {
    for row in &profile.title_stats {
        keep(
            &mut into.titles,
            key(&row.set_id, &row.device),
            row.clone(),
            device,
        );
    }
    for row in &profile.day_stats {
        keep(
            &mut into.days,
            key(&row.day, &row.device),
            row.clone(),
            device,
        );
    }
}

/// Length-prefixed, as `preferences::absorb` does: either half may hold any
/// text, so a plain join could make two different keys collide.
fn key(first: &str, device: &str) -> String {
    format!("{}:{first}{device}", first.len())
}

pub(super) fn rows(kept: Kept) -> (Vec<TitleStatRow>, Vec<DayStatRow>) {
    (
        kept.titles.into_values().map(|held| held.row).collect(),
        kept.days.into_values().map(|held| held.row).collect(),
    )
}

#[cfg(test)]
#[path = "stats_tests.rs"]
mod tests;
