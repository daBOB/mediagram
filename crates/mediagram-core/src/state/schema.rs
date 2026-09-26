//! `state.db`'s tables, in the shape `web/src/state/schema.ts` reached by
//! its v5.
//!
//! The web got there by four `ALTER`-shaped migrations, because its file
//! predates profiles and had rows to carry forward. This store had no such
//! history when it was created — every Android install starts empty — so
//! version 1 was already that final shape, created directly rather than
//! replayed through the steps that produced it. A real install may by now
//! hold rows of its own, though, so everything since v1 is a proper
//! migration like the web's: `ALTER`, never a rebuild that would need one.

/// Statements grouped by the version they produce, the same shape
/// `mlib_spec::schema::GROUPS` and `web/src/state/schema.ts`'s `GROUPS` use:
/// `GROUPS[0]` takes an empty file to version 1.
const GROUPS: &[&[&str]] = &[
    &[
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
    ],
    // v1 -> v2: a removal a merge can see.
    //
    // A watchlist row, a kids mark and a collection were each deleted
    // outright, so nothing about a removal survived to tell another device
    // it had happened — a sync would resurrect it from whichever machine had
    // not yet caught up. `removed_at` is that row kept instead of dropped: a
    // tombstone with its own timestamp, the same last-writer-wins rule
    // `merge.rs` already applies to a position. Nullable, so a row untouched
    // by this migration reads exactly as it did before it.
    //
    // `collections` alone also gains `updated_at`: a list is one row on the
    // wire, merged whole rather than item by item, so renaming it or
    // changing its membership has to move a timestamp `created_at` was never
    // meant to carry. Backfilled from `created_at` — the oldest fact this
    // file has about a list already made.
    &[
        "ALTER TABLE watchlist ADD COLUMN removed_at INTEGER",
        "ALTER TABLE kids ADD COLUMN removed_at INTEGER",
        "ALTER TABLE collections ADD COLUMN removed_at INTEGER",
        "ALTER TABLE collections ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
        "UPDATE collections SET updated_at = created_at WHERE updated_at = 0",
    ],
    // v2 -> v3: a kids profile sees only titles rated for children. One fact
    // about the profile, set when it is made; every existing profile is
    // ordinary.
    &["ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0"],
    // v3 -> v4: what a viewer chose, so they do not choose it again.
    //
    // Web's `preferences` table (`web/src/state/schema.ts`'s v5), verbatim:
    // `scope`/`name`/`value` as rows rather than a column each, because a
    // column per preference would be a migration per preference and these
    // arrive steadily. Scoped to a profile, like everything above but
    // `kids`: which language you watch a series in is a fact about you.
    &["CREATE TABLE IF NOT EXISTS preferences(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       scope TEXT NOT NULL,
       name TEXT NOT NULL,
       value TEXT NOT NULL,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, scope, name)
     )"],
    // v4 -> v5: the household's editor's choice, the title the magazine home
    // page leads its features with. Shaped exactly like `kids` — a mark on a
    // title, for everyone, with a tombstone — matching the web's v8
    // (`web/src/state/schema.ts`): it is the same kind of fact and syncs the
    // same way. Several rows may be live after a merge (two devices pinned
    // different titles); the newest mark is the pick.
    &["CREATE TABLE IF NOT EXISTS editors_choice(
       set_id TEXT PRIMARY KEY,
       marked_at INTEGER NOT NULL,
       removed_at INTEGER
     )"],
    // v5 -> v6: a removal a merge can see, for `watched` too. Un-marking a
    // title was a plain `DELETE`, so nothing survived to tell another device
    // it had happened — a sync would resurrect it from whichever machine had
    // not yet caught up. `removed_at` is that row kept instead of dropped,
    // the same tombstone `watchlist`, `kids` and `collections` already
    // carry. `finished_at` keeps its meaning: when the title was last
    // marked finished.
    &["ALTER TABLE watched ADD COLUMN removed_at INTEGER"],
];

pub const VERSION: i64 = GROUPS.len() as i64;

/// Every statement needed to reach `version` from nothing.
pub fn migrations_up_to(version: i64) -> Vec<&'static str> {
    let take = version.max(0) as usize;
    GROUPS
        .iter()
        .take(take)
        .flat_map(|group| group.iter().copied())
        .collect()
}
