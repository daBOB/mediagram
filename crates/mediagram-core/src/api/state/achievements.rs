//! One profile's achievements for Kotlin: this device's state rows and the
//! installed catalog, read on every call and handed to the pure rules in
//! `crate::state::stats::achievements`. Like the rest of the watch-state
//! surface nothing here throws — a profile this device does not hold, or a
//! store that cannot be read, answers as nothing earned; a catalog that
//! cannot be read, as an empty library.

use std::sync::Arc;

use rusqlite::Connection;

use crate::state::stats::achievements::{
    self, AchievementInput, Achievements, LibraryCollection, LibraryTitle,
};
use crate::state::stats::exchange;
use crate::state::{profiles, record::DayStatRow, rows};

use super::super::Core;
use super::super::store::open_installed;

/// A profile's kids flag, every device's day rows and its live watched marks.
type ProfileRows = (bool, Vec<DayStatRow>, Vec<rows::WatchedRow>);

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// What `profile_id` has earned and the few closest to come. `today` is
    /// this device's local date (`YYYY-MM-DD`) and `utc_offset_minutes` its
    /// offset from UTC now: the day boundaries the day-based ones fall on.
    pub async fn achievements(
        self: Arc<Self>,
        profile_id: String,
        today: String,
        utc_offset_minutes: i32,
    ) -> Achievements {
        self.blocking(move |core| {
            let Some((kids, days, watched)) = core
                .state_db
                .with(|conn| profile_rows(conn, &profile_id))
                .flatten()
            else {
                return Achievements::default();
            };
            let (library, collections) = installed_library(core);
            achievements::achievements(&AchievementInput {
                today,
                utc_offset_minutes,
                kids,
                days,
                watched,
                library,
                collections,
            })
        })
        .await
    }
}

/// `None` for a profile this device does not hold.
fn profile_rows(conn: &Connection, profile_id: &str) -> rusqlite::Result<Option<ProfileRows>> {
    let Some(kids) = profiles::list(conn)?
        .into_iter()
        .find(|profile| profile.id == profile_id)
        .map(|p| p.kids)
    else {
        return Ok(None);
    };
    let (_, days) = exchange::export(conn, profile_id)?;
    Ok(Some((kids, days, rows::watched_for(conn, profile_id)?)))
}

/// The installed catalog as the rules read it: empty before one is installed
/// or when it cannot be read — which still counts hours and streaks.
fn installed_library(core: &Core) -> (Vec<LibraryTitle>, Vec<LibraryCollection>) {
    let Some(conn) = open_installed(core, "achievements") else {
        return Default::default();
    };
    crate::catalog_achievements::library_facts(&conn).unwrap_or_else(|err| {
        tracing::warn!(error = %err, "achievements: the catalog could not be read");
        Default::default()
    })
}

#[cfg(test)]
#[path = "achievements_tests.rs"]
mod tests;
