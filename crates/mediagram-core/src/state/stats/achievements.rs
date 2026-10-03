//! Achievements, worked out on every read from rows this store already keeps
//! — every device's day rows and the live watched marks — and the installed
//! library; never stored or synced: an achievement is a fact about those
//! rows, so two devices holding the same rows cannot disagree about one.
//!
//! A port of `web/src/state/achievements.ts`, held to it by
//! `web/test/fixtures/watch-state/achievements.json` (see
//! `tests/shared_watch_state_fixtures.rs`).

mod day_totals;
mod rungs;

use std::collections::HashMap;

use serde::Deserialize;

use crate::state::record::DayStatRow;
use crate::state::rows::WatchedRow;
use rungs::{DAY_MS, Finish};

/// One set the library holds, as the rules read it.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryTitle {
    pub set_id: String,
    /// `movie`, `ep`, `tut`, `doc` or `docu` — the index's own spelling.
    pub kind: String,
    /// The provider's genres; an episode carries its show's.
    pub genres: Vec<String>,
    /// The show or course it belongs to, if any.
    pub collection: Option<String>,
}

/// A show's episodes or a course's lessons, as the library holds them now.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LibraryCollection {
    pub id: String,
    pub set_ids: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AchievementInput {
    /// The reading engine's local date, `YYYY-MM-DD`.
    pub today: String,
    /// The reading engine's offset from UTC now, in minutes: `120` in CEST.
    pub utc_offset_minutes: i32,
    /// A kids profile is offered finishing and exploring achievements only.
    pub kids: bool,
    /// Every device's day rows.
    pub days: Vec<DayStatRow>,
    /// Live watched marks only.
    pub watched: Vec<WatchedRow>,
    pub library: Vec<LibraryTitle>,
    pub collections: Vec<LibraryCollection>,
}

/// An achievement earned, and when, in epoch milliseconds.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct EarnedAchievement {
    pub id: String,
    pub earned_at: i64,
}

/// One still to come, and how far along it is.
#[derive(Debug, Clone, PartialEq, Eq, Deserialize, uniffi::Record)]
pub struct NextAchievement {
    pub id: String,
    pub have: u32,
    pub need: u32,
}

/// What one profile has earned, newest first, and the few closest to come.
#[derive(Debug, Clone, Default, PartialEq, Eq, Deserialize, uniffi::Record)]
pub struct Achievements {
    pub earned: Vec<EarnedAchievement>,
    pub next: Vec<NextAchievement>,
}

const FILMS: [u32; 4] = [1, 10, 50, 100];
const GENRES: [u32; 2] = [5, 10];
const DOCS: [u32; 1] = [10];
const HOURS: [u32; 3] = [10, 100, 500];
const STREAKS: [u32; 2] = [7, 30];
const BINGE: u32 = 5;
/// How many of the closest unearned achievements a page shows.
const NEXT_SHOWN: usize = 3;

pub fn achievements(input: &AchievementInput) -> Achievements {
    let offset_ms = i64::from(input.utc_offset_minutes) * 60_000;
    // A day is dated at its local noon, at the offset the reader is at now —
    // applied to every day alike, so a day from before a clock change reads
    // an hour out, and noon leaves that hour no way to reach another date.
    let noon = move |day: i64| {
        day.saturating_mul(DAY_MS)
            .saturating_add(DAY_MS / 2)
            .saturating_sub(offset_ms)
    };
    let by_set: HashMap<&str, &LibraryTitle> = input
        .library
        .iter()
        .map(|title| (title.set_id.as_str(), title))
        .collect();
    // A finish of a set the library no longer holds counts for nothing.
    let mut finishes: Vec<Finish<'_>> = input
        .watched
        .iter()
        .filter_map(|row| {
            by_set.get(row.set_id.as_str()).map(|title| Finish {
                title,
                at: row.finished_at,
            })
        })
        .collect();
    finishes.sort_by_key(|finish| finish.at);
    let times_of = |kind: &str| -> Vec<i64> {
        finishes
            .iter()
            .filter(|finish| finish.title.kind == kind)
            .map(|finish| finish.at)
            .collect()
    };
    let finished_at: HashMap<&str, i64> = finishes
        .iter()
        .map(|finish| (finish.title.set_id.as_str(), finish.at))
        .collect();

    let mut ladders = vec![
        rungs::counted("films", &FILMS, &times_of("movie")),
        rungs::counted("genres", &GENRES, &rungs::genre_arrivals(&finishes)),
        rungs::counted("docs", &DOCS, &times_of("docu")),
        rungs::whole_show(&input.collections, &finished_at),
    ];
    if !input.kids {
        let days = day_totals::day_totals(&input.days);
        ladders.push(rungs::hours(&HOURS, &days, noon));
        ladders.push(rungs::streak(&STREAKS, &days, noon));
        ladders.push(vec![rungs::binge(BINGE, &finishes, offset_ms, noon)]);
    }

    let mut earned = Vec::new();
    let mut next = Vec::new();
    for ladder in ladders {
        if let Some(open) = ladder.iter().find(|rung| rung.earned_at.is_none()) {
            next.push(NextAchievement {
                id: open.id.clone(),
                have: open.have,
                need: open.need,
            });
        }
        earned.extend(ladder.into_iter().filter_map(|rung| {
            rung.earned_at.map(|earned_at| EarnedAchievement {
                id: rung.id,
                earned_at,
            })
        }));
    }
    earned.sort_by(|a, b| b.earned_at.cmp(&a.earned_at).then_with(|| a.id.cmp(&b.id)));
    // Closest first: have/need compared without dividing.
    next.sort_by(|a, b| {
        let (theirs, mine) = (
            u64::from(b.have) * u64::from(a.need),
            u64::from(a.have) * u64::from(b.need),
        );
        theirs.cmp(&mine).then_with(|| a.id.cmp(&b.id))
    });
    next.truncate(NEXT_SHOWN);
    Achievements { earned, next }
}
