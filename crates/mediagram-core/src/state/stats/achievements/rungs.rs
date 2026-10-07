//! The rules behind each achievement, one ladder each — split out of
//! `achievements.rs`, which gathers them. A port of
//! `web/src/state/achievement-rungs.ts`: every function answers a [`Rung`]
//! per id — when it was earned, or `None`, and the progress towards it.

use std::collections::{HashMap, HashSet};

use mlib_spec::Kind;

use super::day_totals::DayTotal;
use super::{LibraryCollection, LibraryTitle};

pub(super) const DAY_MS: i64 = 86_400_000;

/// One achievement of a ladder: earned at `earned_at`, or not yet.
pub(super) struct Rung {
    pub id: String,
    pub earned_at: Option<i64>,
    pub have: u32,
    pub need: u32,
}

/// A finish the library still holds.
pub(super) struct Finish<'a> {
    pub title: &'a LibraryTitle,
    pub at: i64,
}

fn count(n: usize) -> u32 {
    u32::try_from(n).unwrap_or(u32::MAX)
}

/// Rung N of a counting ladder is earned by the Nth time in `times`, which is ascending.
pub(super) fn counted(name: &str, rungs: &[u32], times: &[i64]) -> Vec<Rung> {
    rungs
        .iter()
        .map(|&need| Rung {
            id: format!("{name}-{need}"),
            earned_at: times.get(need as usize - 1).copied(),
            have: count(times.len()),
            need,
        })
        .collect()
}

/// When each new distinct genre arrived: entry K is the finish that brought the (K+1)th.
pub(super) fn genre_arrivals(finishes: &[Finish<'_>]) -> Vec<i64> {
    let mut seen: HashSet<&str> = HashSet::new();
    let mut arrivals = Vec::new();
    for finish in finishes {
        seen.extend(finish.title.genres.iter().map(String::as_str));
        arrivals.resize(seen.len(), finish.at);
    }
    arrivals
}

/// Cumulative watching across days; a rung is earned on the day the total reaches it.
pub(super) fn hours(rungs: &[u32], days: &[DayTotal], noon: impl Fn(i64) -> i64) -> Vec<Rung> {
    let mut total = 0.0;
    let mut reached: HashMap<u32, i64> = HashMap::new();
    for day in days {
        total += day.seconds;
        for &need in rungs {
            if total >= f64::from(need) * 3600.0 {
                reached.entry(need).or_insert_with(|| noon(day.day));
            }
        }
    }
    let have = (total / 3600.0).floor() as u32;
    rungs
        .iter()
        .map(|&need| Rung {
            id: format!("hours-{need}"),
            earned_at: reached.get(&need).copied(),
            have,
            need,
        })
        .collect()
}

/// Consecutive days with any watching; a rung is earned on the day completing the first such run.
pub(super) fn streak(rungs: &[u32], days: &[DayTotal], noon: impl Fn(i64) -> i64) -> Vec<Rung> {
    let (mut run, mut longest, mut previous) = (0_u32, 0_u32, None::<i64>);
    let mut reached: HashMap<u32, i64> = HashMap::new();
    for day in days.iter().filter(|day| day.seconds > 0.0) {
        run = if previous == Some(day.day - 1) {
            run + 1
        } else {
            1
        };
        previous = Some(day.day);
        longest = longest.max(run);
        for &need in rungs {
            if run == need {
                reached.entry(need).or_insert_with(|| noon(day.day));
            }
        }
    }
    rungs
        .iter()
        .map(|&need| Rung {
            id: format!("streak-{need}"),
            earned_at: reached.get(&need).copied(),
            have: longest,
            need,
        })
        .collect()
}

/// Episodes finished on one local day; earned on the first day reaching `need`.
pub(super) fn binge(
    need: u32,
    finishes: &[Finish<'_>],
    offset_ms: i64,
    noon: impl Fn(i64) -> i64,
) -> Rung {
    let mut per_day: HashMap<i64, u32> = HashMap::new();
    let (mut most, mut first) = (0, None);
    for finish in finishes
        .iter()
        .filter(|finish| finish.title.kind == Kind::Ep.as_str())
    {
        let day = finish.at.saturating_add(offset_ms).div_euclid(DAY_MS);
        let on_day = per_day.entry(day).or_default();
        *on_day += 1;
        most = most.max(*on_day);
        if *on_day == need && first.is_none() {
            first = Some(day);
        }
    }
    Rung {
        id: format!("binge-{need}"),
        earned_at: first.map(noon),
        have: most,
        need,
    }
}

/// Every set of one collection finished. Earned when the first collection
/// was completed; until then, the progress of the one closest to it.
/// Nothing at all for a library with no collections.
pub(super) fn whole_show(
    collections: &[LibraryCollection],
    finished_at: &HashMap<&str, i64>,
) -> Vec<Rung> {
    let mut earned_at: Option<i64> = None;
    let mut best: Option<(u32, u32)> = None;
    for collection in collections {
        let need = count(collection.set_ids.len());
        if need == 0 {
            continue;
        }
        let times: Vec<i64> = collection
            .set_ids
            .iter()
            .filter_map(|set_id| finished_at.get(set_id.as_str()).copied())
            .collect();
        let have = count(times.len());
        if have == need {
            let done = times.iter().copied().max().unwrap_or_default();
            earned_at = Some(earned_at.map_or(done, |at| at.min(done)));
        }
        // Closest first; equal shares with equal counts are equal answers.
        let closer = best.is_none_or(|(best_have, best_need)| {
            let (mine, theirs) = (
                u64::from(have) * u64::from(best_need),
                u64::from(best_have) * u64::from(need),
            );
            mine > theirs || (mine == theirs && have > best_have)
        });
        if closer {
            best = Some((have, need));
        }
    }
    best.map(|(have, need)| Rung {
        id: "whole-show".into(),
        earned_at,
        have,
        need,
    })
    .into_iter()
    .collect()
}

#[cfg(test)]
#[path = "rungs_tests.rs"]
mod tests;
