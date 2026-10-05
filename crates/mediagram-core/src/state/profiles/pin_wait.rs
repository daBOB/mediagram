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
//! Kept in `state.db`, read and written around every comparison, not held in
//! memory: on a device a child holds, swiping the app away is a restart, and
//! a count that ended with the process made the day of guessing a few hours.
//! The web keeps its count in memory — its server restarts rarely, and only
//! at its admin's hand. Local only, in `state_meta`: never exported, never
//! synced, so one device's guesses never lock a profile on another.

use std::collections::HashMap;

use rusqlite::{Connection, OptionalExtension, params};
use serde::{Deserialize, Serialize};

/// Wrong PINs in a row that start a profile's wait.
pub const MAX_WRONG_PINS: u32 = 5;
/// How long a wait lasts.
pub const WAIT_MS: i64 = 60_000;

const KEY: &str = "pin_wait";

/// Profile id → its wrong PINs in a row, and when its wait ends (0 for
/// none). The time is always passed in, never read here, so a test can say
/// what time it is.
#[derive(Debug, Default, Serialize, Deserialize)]
pub struct PinWait {
    counts: HashMap<String, Count>,
}

#[derive(Debug, Default, Serialize, Deserialize)]
struct Count {
    wrong: u32,
    until: i64,
}

impl PinWait {
    /// Whole seconds until `id`'s PIN is compared again, rounded up; 0 is now.
    pub fn seconds_left(&mut self, id: &str, now_ms: i64) -> u32 {
        let Some(count) = self.counts.get_mut(id) else {
            return 0;
        };
        if count.until == 0 {
            return 0;
        }
        // A wait further off than a whole one means the clock went back since
        // it began — a box booting before its time is set. Kept, it could
        // hold the profile shut for years; it lasts a minute from now instead.
        if count.until.saturating_sub(now_ms) > WAIT_MS {
            count.until = now_ms.saturating_add(WAIT_MS);
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

    /// The counts this store holds. One that cannot be read counts nothing:
    /// only a hand editing the file writes such a value, and that hand is
    /// already past every PIN here.
    pub(crate) fn load(conn: &Connection) -> rusqlite::Result<Self> {
        let stored: Option<String> = conn
            .query_row("SELECT value FROM state_meta WHERE key = ?1", [KEY], |row| row.get(0))
            .optional()?;
        Ok(stored.and_then(|text| serde_json::from_str(&text).ok()).unwrap_or_default())
    }

    pub(crate) fn save(&self, conn: &Connection) -> rusqlite::Result<()> {
        if self.counts.is_empty() {
            conn.execute("DELETE FROM state_meta WHERE key = ?1", [KEY])?;
            return Ok(());
        }
        let text = serde_json::to_string(self)
            .map_err(|error| rusqlite::Error::ToSqlConversionFailure(Box::new(error)))?;
        conn.execute(
            "INSERT INTO state_meta(key, value) VALUES (?1, ?2)
               ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            params![KEY, text],
        )?;
        Ok(())
    }
}
