//! SQLite DDL for `library.db`. The uploader keeps this file locally as the
//! canonical index and pushes a snapshot to the channel as a pinned document.

#[path = "schema_versions.rs"]
mod schema_versions;
use schema_versions::{V1, V2, V3, V4, V5, V6};

pub const SCHEMA_VERSION: i64 = 12;

/// The oldest index a *reader* of someone else's snapshot still accepts.
///
/// A channel snapshot is written by whichever machine uploads, and that
/// machine is upgraded on its own schedule; refusing its snapshot until then
/// would stop every reader. v7 only added `shows.certification`, v8 only
/// `shows.popularity`, v9 only `shows.collection_id`/`collection_name`/
/// `series_type` plus the wholly new `credits` and `franchises` tables, v10
/// only the wholly new `artwork` table, v11 only `shows.original_language`
/// plus the wholly new `anime_overrides` table, and v12 only the wholly new
/// `categories` table — every one of which every reader treats as optional.
/// The web player's `OLDEST_READABLE_SCHEMA` (`web/src/catalog.ts`) is the
/// same number. The uploader's own index is still held to
/// [`SCHEMA_VERSION`]: that one it can migrate.
pub const OLDEST_READABLE_SCHEMA: i64 = 6;

/// Every layout a reader accepts: [`OLDEST_READABLE_SCHEMA`] through
/// [`SCHEMA_VERSION`], each one. Spelled out because a package pointer is
/// checked by membership — listing only the two ends once refused every
/// version between them. A test holds this to the range.
pub const READABLE_SCHEMAS: &[i64] = &[6, 7, 8, 9, 10, 11, 12];

/// The index's file name, wherever a copy of it sits: the uploader's data
/// directory, the snapshot pinned in the channel, and a metadata package all
/// carry it under this one name, which is how a reader finds it in each.
pub const INDEX_FILE: &str = "library.db";

/// Statements grouped by the version they produce: `GROUPS[0]` takes a
/// database from nothing to version 1, `GROUPS[1]` from 1 to 2, and so on.
///
/// Grouping by version rather than replaying every statement on each open is
/// what lets a migration do something other than `CREATE ... IF NOT EXISTS`.
/// SQLite has no `ADD COLUMN IF NOT EXISTS`, so an idempotent-by-wording list
/// could never gain a column.
pub const GROUPS: &[&[&str]] = &[V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12];

/// Every statement needed to reach `version` from an empty database. Used by
/// tests and by anyone reconstructing an older layout.
#[must_use]
pub fn migrations_up_to(version: i64) -> Vec<&'static str> {
    GROUPS
        .iter()
        .take(usize::try_from(version.max(0)).unwrap_or(usize::MAX))
        .flat_map(|group| group.iter().copied())
        .collect()
}

/// v6 → v7: the age rating a title carries in the library's country.
///
/// What decides whether a title may sit on the Kids shelf without anyone
/// having marked it. Text, as the provider writes it (`12`, `FSK 16` is never
/// the form), because some countries rate with letters.
const V7: &[&str] = &["ALTER TABLE shows ADD COLUMN certification TEXT"];

/// v7 → v8: how much attention a title draws at the provider.
///
/// What ranks a "trending" pick. TMDB's figure as of the cached payload, so
/// it ages; readers treat it as optional, like `certification`.
const V8: &[&str] = &["ALTER TABLE shows ADD COLUMN popularity REAL"];

/// v8 → v9: who is credited on a title, and the franchise a film belongs to.
///
/// `shows` gains a film's TMDB "collection" — the id and name eleven `Star
/// Trek` films share, say — and a series' TMDB `type` (`Scripted`,
/// `Miniseries`, …). Both wholly new tables are keyed the way `shows` is,
/// with one difference each: `credits` adds `ord`, because a title credits
/// more than one person and each needs its own row; `franchises` drops
/// `kind`, because a collection is a movie-only idea and does not need one.
///
/// `credits.profile` carries the TMDB `profile_path` a portrait is fetched
/// from — bare, like a poster path, not a full URL — so a device with no
/// TMDB cache of its own can still show a face: it reads this column out of
/// whatever snapshot reached it rather than asking the provider again.
const V9: &[&str] = &[
    "ALTER TABLE shows ADD COLUMN collection_id INTEGER",
    "ALTER TABLE shows ADD COLUMN collection_name TEXT",
    "ALTER TABLE shows ADD COLUMN series_type TEXT",
    "CREATE TABLE IF NOT EXISTS credits(
        source TEXT NOT NULL,
        kind TEXT NOT NULL,
        id INTEGER NOT NULL,
        ord INTEGER NOT NULL,
        person_id INTEGER NOT NULL,
        name TEXT NOT NULL,
        role TEXT,
        dept TEXT NOT NULL,
        profile TEXT,
        PRIMARY KEY(source, kind, id, ord)
    )",
    "CREATE TABLE IF NOT EXISTS franchises(
        source TEXT NOT NULL,
        id INTEGER NOT NULL,
        name TEXT NOT NULL,
        overview TEXT,
        PRIMARY KEY(source, id)
    )",
];

/// v9 → v10: custom poster and backdrop art the uploader supplies directly,
/// an additive group on top of v9 so nobody has to coordinate who publishes
/// that one first.
///
/// One wholly new table, keyed the way a poster key already is: the existing
/// `tmdb-…`/`tmdb-…-bg` key overrides TMDB's own art where both exist, and a
/// title with no provider id — a documentary, or a course used as a
/// tutorial's cover — gets `title-{slug}`/`title-{slug}-bg` instead, the slug
/// taken from its show or course name (`mlib_spec::package::title_art_key`,
/// the same derivation `add-course` uses for its default collection id). The
/// bytes ride the index push like everything else in `library.db`, so web
/// and Android both get them with no extra Telegram round trip, and a v9
/// reader simply never queries a table it does not know exists.
const V10: &[&str] = &["CREATE TABLE IF NOT EXISTS artwork(
        key TEXT PRIMARY KEY,
        mime TEXT NOT NULL,
        bytes BLOB NOT NULL
    )"];

/// v10 → v11: a title's original language, and a hand-set anime decision.
///
/// `shows.original_language` is TMDB's own code (`ja`, `en`, …) from the
/// cached details payload — the same source every other `shows` column
/// already reads from, so backfilling it costs no request. Every reader
/// treats it as optional, like `certification` or `popularity`.
///
/// `anime_overrides` sits apart from `shows` on purpose: `shows::upsert`
/// replaces a row whole on every `metadata` run, which would clobber a
/// person's decision, and `merge_shows` only fills `NULL`s, which cannot
/// carry a changed-my-mind or a cleared override between the two machines
/// that may publish to one channel. A `NULL` `anime` is kept as a row rather
/// than deleted — clearing an override has to reach the other machine on the
/// next merge, and a deleted row leaves nothing to merge. `set_at` (Unix
/// seconds) is what a merge compares to decide whose value is newer.
const V11: &[&str] = &[
    "ALTER TABLE shows ADD COLUMN original_language TEXT",
    "CREATE TABLE IF NOT EXISTS anime_overrides(
        source TEXT NOT NULL,
        kind TEXT NOT NULL,
        id INTEGER NOT NULL,
        anime INTEGER,
        set_at INTEGER NOT NULL,
        PRIMARY KEY(source, kind, id)
    )",
];

/// v11 → v12: a hand-set category per course, documentary collection or
/// standalone documentary — the same unit its custom artwork already keys
/// ([`crate::package::title_art_key`]), for the row each files into on its
/// department page.
///
/// Kept apart from `shows` and from a set's own row for the reason
/// `anime_overrides` is: it must survive both a `metadata` run and a
/// `rescan`, and neither writes it. `category` `NULL` is a kept row, not a
/// deleted one — clearing a category has to reach the other uploading
/// machine on the next merge, and a deleted row carries nothing to merge.
/// `set_at` (Unix seconds) is what a merge compares to decide whose value is
/// newer, `NULL` included, the same way `anime_overrides.set_at` does.
const V12: &[&str] = &["CREATE TABLE IF NOT EXISTS categories(
        department TEXT NOT NULL,
        item_key TEXT NOT NULL,
        category TEXT,
        set_at INTEGER NOT NULL,
        PRIMARY KEY(department, item_key)
    )"];

/// How `sets.status` and `parts.status` spell each state. Written once here,
/// beside the one SQL fragment that has to spell them inline; every other
/// query binds them as parameters. The player reads the same spellings.
pub const SET_PENDING: &str = "pending";
pub const SET_COMPLETE: &str = "complete";
pub const PART_PENDING: &str = "pending";
pub const PART_DONE: &str = "done";

/// Playable invariant, as SQL usable in a WHERE clause on `sets s`.
pub const PLAYABLE_SQL: &str = "s.status = 'complete'
    AND s.part_count = (SELECT COUNT(*) FROM parts p WHERE p.set_id = s.set_id AND p.status = 'done')
    AND s.total = (SELECT COALESCE(SUM(byte_length), 0) FROM parts p WHERE p.set_id = s.set_id)";

#[cfg(test)]
#[path = "schema_tests.rs"]
mod tests;
