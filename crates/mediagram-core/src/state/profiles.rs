//! Who's watching: list, create, choose, and delete — a port of the web's
//! `web/src/state/profiles.ts`. Adding to and removing from a household,
//! behind a grown-up's PIN, is `manage`.
//!
//! Rename stays deferred: a sync can only ever add a profile from another
//! device's document, never carry a rename, and offering one here would let
//! this device drift from what the others still believe. Delete is safe in
//! a way rename is not — a profile removed here simply returns the moment
//! another device that still holds it syncs, the same as the web's own
//! `DELETE FROM profiles`, so nothing here can make two devices disagree for
//! longer than one round.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::normal_name;

pub mod manage;
mod outcome;
pub mod pin;
pub mod pin_wait;
pub(crate) mod role_rows;
pub mod rules;

pub use manage::ProfileManager;
pub use outcome::{Answer, ProfileOutcome};

const CHOSEN_KEY: &str = "chosen_profile";
/// How long a name may be. Long enough for a sentence, short enough to show
/// — the same cap `store.ts`'s `MAX_NAME` uses.
const MAX_NAME: usize = 120;

/// A profile as the app sees it. Says whether there is a PIN, never what.
/// Every field after `name` is defaulted in the generated Kotlin (uniffi
/// 0.32 supports field defaults), so `Profile(id, name)` call sites keep
/// compiling.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Profile {
    pub id: String,
    pub name: String,
    /// Sees only what its own limit allows, and manages nothing.
    #[uniffi(default = false)]
    pub kids: bool,
    /// FSK 6 or 12 on a kid; `None` on a grown-up.
    #[uniffi(default = None)]
    pub kids_age: Option<u8>,
    /// The grown-up who made this kid. `None`, or one not here, is the admin's.
    #[uniffi(default = None)]
    pub parent_id: Option<String>,
    /// The household's admin: a grown-up, and at most one.
    #[uniffi(default = false)]
    pub admin: bool,
    /// Whether a PIN is set — never the PIN, its hash or its salt.
    #[uniffi(default = false)]
    pub has_pin: bool,
}

/// The profile the app sees. A kid always has a limit, whatever the column
/// holds — 12 is what every kid saw before there was a choice — and is never
/// the admin and never has a PIN: it manages nothing and opens freely. A
/// grown-up another device later calls a kid keeps its old claim and PIN in
/// their columns; they must not make it an admin, or a profile with a PIN.
impl From<role_rows::Stored> for Profile {
    fn from(row: role_rows::Stored) -> Self {
        Profile {
            kids_age: row.kids.then_some(if row.kids_age == Some(6) { 6 } else { 12 }),
            admin: row.is_admin(),
            has_pin: !row.kids && row.pin_hash.is_some(),
            id: row.id,
            name: row.name,
            kids: row.kids,
            parent_id: row.parent_id,
        }
    }
}

/// Who watches this library, oldest first. Empty until someone says.
pub fn list(conn: &Connection) -> rusqlite::Result<Vec<Profile>> {
    Ok(role_rows::load(conn)?.into_iter().map(Profile::from).collect())
}

/// A profile with no role beside `kids` — the way sync makes a viewer it has
/// not met. A kid starts at FSK 12 dated 0, so any limit a parent chose
/// outdates it. `None` for a name with nothing left after trimming — never
/// a stored profile with no way to show it.
pub fn create(conn: &Connection, name: &str, kids: bool) -> rusqlite::Result<Option<Profile>> {
    let role = role_rows::NewProfile {
        kids,
        ..Default::default()
    };
    role_rows::insert(conn, name, &role, now_ms())
}

pub fn exists(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    conn.query_row("SELECT 1 FROM profiles WHERE id = ?1", [id], |_| Ok(()))
        .optional()
        .map(|r| r.is_some())
}

/// Takes everything that was theirs with it: every table that scopes a row
/// to a profile cascades on `profile_id`. `false` when `id` names nobody —
/// nothing to cascade from. `chosen` clears itself implicitly the moment
/// this was the profile it named; see `chosen` on why that needs no code
/// here.
pub fn delete(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    Ok(conn.execute("DELETE FROM profiles WHERE id = ?1", params![id])? > 0)
}

/// This install's remembered "who's watching" — cleared implicitly if the
/// chosen profile was later deleted, since `exists` is checked on read
/// rather than trusted at write time.
pub fn chosen(conn: &Connection) -> rusqlite::Result<Option<String>> {
    let id: Option<String> = conn
        .query_row(
            "SELECT value FROM state_meta WHERE key = ?1",
            [CHOSEN_KEY],
            |row| row.get(0),
        )
        .optional()?;
    match id {
        Some(id) if exists(conn, &id)? => Ok(Some(id)),
        _ => Ok(None),
    }
}

/// Records the choice. `false` when `id` names no profile — the caller
/// asked to choose someone who is not there, so nothing was remembered.
pub fn choose(conn: &Connection, id: &str) -> rusqlite::Result<bool> {
    if !exists(conn, id)? {
        return Ok(false);
    }
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2)
           ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![CHOSEN_KEY, id],
    )?;
    Ok(true)
}

/// This player's id for a viewer, made if it has never seen them.
///
/// Made rather than skipped, because the first thing a second machine knows
/// about a viewer is a document written by the first — refusing to create
/// one would mean sync could only ever flow towards a machine that had
/// already met them.
pub fn profile_named(
    conn: &Connection,
    name: &str,
    display_name: Option<&str>,
) -> rusqlite::Result<Option<String>> {
    Ok(profile_named_with_creation(conn, name, display_name, false)?.map(|(id, _, _)| id))
}

/// Resolves an id and reports whether this call created its profile row, and
/// whether that profile is a kids one now (already so, or made as one).
pub(super) fn profile_named_with_creation(
    conn: &Connection,
    name: &str,
    display_name: Option<&str>,
    kids: bool,
) -> rusqlite::Result<Option<(String, bool, bool)>> {
    let Some(wanted) = normal_name(name) else {
        return Ok(None);
    };
    for profile in list(conn)? {
        if normal_name(&profile.name).as_deref() == Some(wanted.as_str()) {
            return Ok(Some((profile.id, false, profile.kids)));
        }
    }
    // Created from the spelling somebody typed, never from the normalised
    // identity — that would greet a viewer as "andré" on every new machine.
    Ok(create(conn, display_name.unwrap_or(name), kids)?.map(|p| (p.id, true, kids)))
}

/// A name with its edges trimmed and internal whitespace collapsed, or
/// `None` when there is nothing left.
pub(super) fn clean_name(name: &str) -> Option<String> {
    let collapsed = name.split_whitespace().collect::<Vec<_>>().join(" ");
    let clean: String = collapsed.chars().take(MAX_NAME).collect();
    (!clean.is_empty()).then_some(clean)
}

pub(crate) fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

#[cfg(test)]
#[path = "profiles_tests.rs"]
mod tests;
