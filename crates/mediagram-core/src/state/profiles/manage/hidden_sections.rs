//! Which sections of the library a profile sees, set by whoever the rule
//! says (`set-access` in `rules`): a kid's by its own grown-up, a grown-up's
//! by the admin, the admin's by nobody. Split out of `manage.rs` to keep it
//! under the line limit; it answers in the same order and through the same
//! `check` as every other management call.
//!
//! Not a lock, as a kid's age limit is not: the app filters the catalogue,
//! and nothing here stops a request for a title in a hidden section.

use rusqlite::{Connection, params};

use super::ProfileManager;
use crate::state::profiles::Answer;
use crate::state::profiles::ProfileOutcome::{Done, Invalid};
use crate::state::profiles::role_writes::LAST_STAMP;
use crate::state::profiles::rules::Action;
use crate::state::record::hidden_sections_of;

impl ProfileManager<'_> {
    /// The sections `id` does not see, as a whole list; an empty one hides
    /// nothing. Stored sorted, each key once.
    pub fn set_hidden_sections(&mut self, actor_id: &str, pin: &str, id: &str, sections: Vec<String>) -> Answer {
        let Some(hidden) = hidden_sections_of(sections) else {
            return Ok(Invalid);
        };
        if let Some(refused) = self.check(actor_id, pin, Action::SetAccess, Some(id), false)? {
            return Ok(refused);
        }
        write(self.conn, id, &hidden, self.now)?;
        Ok(Done)
    }
}

/// The whole list, dated like a limit, so it wins over an older list
/// elsewhere — and written even when unchanged, as a limit is.
fn write(conn: &Connection, id: &str, sections: &[String], now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE profiles SET hidden_sections = ?2,
                hidden_sections_updated_at = MAX(?3, MIN(hidden_sections_updated_at, ?4) + 1) WHERE id = ?1",
        params![id, serde_json::json!(sections).to_string(), now, LAST_STAMP],
    )?;
    Ok(())
}
