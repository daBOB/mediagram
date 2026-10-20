//! Own writes of a profile's PIN and limits, split from the rows' reads
//! (`role_rows`) to keep that file's size down — the web's
//! `profiles-write.ts`.

use rusqlite::{Connection, params};

use super::pin;
use crate::state::record::MAX_STAMP;

/// The latest stamp an own write of a PIN or a limit takes. Each is dated at
/// least a millisecond past the last change: an imported one can carry
/// another device's clock, and this one running behind must not write a
/// change that loses to the value it replaced. Kept below the largest time a
/// peer keeps, as `set_watched`'s stamps are.
pub(super) const LAST_STAMP: i64 = MAX_STAMP as i64 - 1;

/// A fresh salt, and the hash of `pin` under it. `proven`: set by someone
/// who knew the PIN before it, or the admin's reset — not a first PIN.
pub(crate) fn write_pin(conn: &Connection, id: &str, pin: &str, now: i64, proven: bool) -> rusqlite::Result<()> {
    let salt = pin::new_salt();
    conn.execute(
        "UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_proven = ?6,
                pin_updated_at = MAX(?4, MIN(pin_updated_at, ?5) + 1) WHERE id = ?1",
        params![id, pin::hash(&salt, pin), salt, now, LAST_STAMP, proven],
    )?;
    Ok(())
}

/// A kid's limit. Written, and dated, even when it is the limit already
/// there: a parent's choice is news to every device that hears of it.
pub(crate) fn write_kids_age(conn: &Connection, id: &str, age: u8, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE profiles SET kids_age = ?2,
                kids_age_updated_at = MAX(?3, MIN(kids_age_updated_at, ?4) + 1) WHERE id = ?1",
        params![id, age, now, LAST_STAMP],
    )?;
    Ok(())
}
