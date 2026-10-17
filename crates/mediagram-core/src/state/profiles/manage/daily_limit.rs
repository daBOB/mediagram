//! A kid's daily watching limit: the age limit's rule and PIN check, a
//! stamped write of minutes or of "none". Split out of `manage.rs` to keep it
//! under the line limit.

use rusqlite::{Connection, params};

use super::ProfileManager;
use crate::state::profiles::Answer;
use crate::state::profiles::ProfileOutcome::{Done, Invalid};
use crate::state::profiles::role_rows::LAST_STAMP;
use crate::state::profiles::rules::Action;

pub(super) fn valid_minutes(minutes: u16) -> bool {
    (1..=1440).contains(&minutes)
}

impl ProfileManager<'_> {
    /// A kid's daily limit in minutes, `None` taking it off; set by whoever
    /// may set its age limit.
    pub fn set_daily_limit(&mut self, actor_id: &str, pin: &str, id: &str, minutes: Option<u16>) -> Answer {
        if !minutes.is_none_or(valid_minutes) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::SetKidsAge, Some(id), false)? {
            return Ok(refused);
        }
        write(self.conn, id, minutes, self.now)?;
        Ok(Done)
    }
}

/// Dated like `write_kids_age`, and written even when unchanged: taking a
/// limit off is news to every device too.
fn write(conn: &Connection, id: &str, minutes: Option<u16>, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE profiles SET daily_limit = ?2,
                daily_limit_updated_at = MAX(?3, MIN(daily_limit_updated_at, ?4) + 1) WHERE id = ?1",
        params![id, minutes, now, LAST_STAMP],
    )?;
    Ok(())
}
