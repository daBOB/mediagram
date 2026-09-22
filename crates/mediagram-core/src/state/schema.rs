//! `state.db`'s tables, in the shape `web/src/state/schema.ts` reached by
//! its v4 (its v5, `preferences`, is out of scope here — see phase 06).
//!
//! The web got there by four `ALTER`-shaped migrations, because its file
//! predates profiles and had rows to carry forward. This store has no such
//! history — every Android install starts empty — so version 1 here is
//! already that final shape, created directly rather than replayed through
//! the steps that produced it.

/// Statements grouped by the version they produce, the same shape
/// `mlib_spec::schema::GROUPS` and `web/src/state/schema.ts`'s `GROUPS` use:
/// `GROUPS[0]` takes an empty file to version 1.
const GROUPS: &[&[&str]] = &[&[
    "CREATE TABLE IF NOT EXISTS profiles(
       id TEXT PRIMARY KEY,
       name TEXT NOT NULL,
       created_at INTEGER NOT NULL
     )",
    // `at_seconds`, not a percentage: a set's runtime can be unknown, and a
    // percentage recorded against an unknown length cannot be turned back
    // into a position to seek to.
    "CREATE TABLE IF NOT EXISTS progress(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       at_seconds REAL NOT NULL,
       duration REAL,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )",
    "CREATE INDEX IF NOT EXISTS progress_recent ON progress(profile_id, updated_at DESC)",
    "CREATE TABLE IF NOT EXISTS watchlist(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       added_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )",
    "CREATE TABLE IF NOT EXISTS collections(
       id TEXT PRIMARY KEY,
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       name TEXT NOT NULL,
       created_at INTEGER NOT NULL
     )",
    "CREATE INDEX IF NOT EXISTS collections_of ON collections(profile_id, created_at)",
    // `position` keeps the order a list was built in, renumbered on insert
    // rather than sorted on read.
    "CREATE TABLE IF NOT EXISTS collection_items(
       collection_id TEXT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       position INTEGER NOT NULL,
       PRIMARY KEY(collection_id, set_id)
     )",
    // Not scoped to a profile, unlike everything above: marking a title as
    // a child's is a fact about the title, not about who is watching, and a
    // mark that had to be made again on every profile would be wrong on the
    // second television in the house as well as tedious on the first.
    "CREATE TABLE IF NOT EXISTS kids(
       set_id TEXT PRIMARY KEY,
       marked_at INTEGER NOT NULL
     )",
    // Recorded because finishing a title clears its position — the only
    // record left that it was watched at all, and what the start page ranks
    // a show by.
    "CREATE TABLE IF NOT EXISTS watched(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       finished_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )",
    "CREATE TABLE IF NOT EXISTS state_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)",
]];

pub const VERSION: i64 = GROUPS.len() as i64;

/// Every statement needed to reach `version` from nothing.
pub fn migrations_up_to(version: i64) -> Vec<&'static str> {
    let take = version.max(0) as usize;
    GROUPS.iter().take(take).flat_map(|group| group.iter().copied()).collect()
}
