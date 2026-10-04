//! The wait after wrong PINs, kept per profile — a port of the web's
//! `profiles-wait.ts`, pinned to it by `pin-wait.json`. Five wrong PINs in a
//! row for one profile and its PIN is not compared for a minute; a right PIN
//! for that profile wipes its count, and so does the wait running out.
//!
//! Per profile, because one count for the whole player was washed out by a
//! single right answer: four guesses at the admin, then one's own PIN, over
//! and over, never waited. Four digits are ten thousand guesses; at five a
//! minute that is more than a day of a child pressing buttons at one profile.
//!
//! In memory only, one per `StateDb` and so one per `Core`: a restart forgets
//! the counts, which costs a guesser a restart and is not worth a table.

use std::collections::HashMap;

/// Wrong PINs in a row that start a profile's wait.
pub const MAX_WRONG_PINS: u32 = 5;
/// How long a wait lasts.
pub const WAIT_MS: i64 = 60_000;

/// Profile id → its wrong PINs in a row, and when its wait ends (0 for
/// none). The time is always passed in, never read here, so a test can say
/// what time it is.
#[derive(Debug, Default)]
pub struct PinWait {
    counts: HashMap<String, Count>,
}

#[derive(Debug, Default)]
struct Count {
    wrong: u32,
    until: i64,
}

impl PinWait {
    /// Whole seconds until `id`'s PIN is compared again, rounded up; 0 is now.
    pub fn seconds_left(&mut self, id: &str, now_ms: i64) -> u32 {
        let Some(count) = self.counts.get(id) else {
            return 0;
        };
        if count.until == 0 {
            return 0;
        }
        let left = count.until.saturating_sub(now_ms);
        if left > 0 {
            return u32::try_from(left.saturating_add(999) / 1000).unwrap_or(u32::MAX);
        }
        // The wait has run out: that profile's count starts again from nothing.
        self.counts.remove(id);
        0
    }

    pub fn failed(&mut self, id: &str, now_ms: i64) {
        self.seconds_left(id, now_ms);
        let count = self.counts.entry(id.to_string()).or_default();
        count.wrong += 1;
        if count.wrong >= MAX_WRONG_PINS {
            count.until = now_ms.saturating_add(WAIT_MS);
        }
    }

    pub fn succeeded(&mut self, id: &str) {
        self.counts.remove(id);
    }
}
