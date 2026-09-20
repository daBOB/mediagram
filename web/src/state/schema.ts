/**
 * The player's own database.
 *
 * Deliberately not the index. That one belongs to the uploader, is opened
 * read-only, and is replaced wholesale when a package refresh lands — a watch
 * position written there would be destroyed by the next catalog update and
 * would have had no business being there in the first place.
 *
 * So the player gets one file it owns, and this is the only schema it may
 * migrate. `mlib_spec::schema::SCHEMA_VERSION` is a different number about a
 * different database and the two must never be confused.
 */

export const STATE_SCHEMA = 5;

/**
 * Statements grouped by the version they produce, the same shape the index's
 * migrations use: `GROUPS[0]` takes an empty file to version 1.
 */
export const GROUPS: readonly (readonly string[])[] = [
  [
    // `at_seconds`, not a percentage. A set's runtime can be null in the
    // index, and a percentage recorded against an unknown length cannot be
    // turned back into a position to seek to.
    `CREATE TABLE IF NOT EXISTS progress(
       set_id TEXT PRIMARY KEY,
       at_seconds REAL NOT NULL,
       duration REAL,
       updated_at INTEGER NOT NULL
     )`,
    `CREATE INDEX IF NOT EXISTS progress_recent ON progress(updated_at DESC)`,
    `CREATE TABLE IF NOT EXISTS watchlist(
       set_id TEXT PRIMARY KEY,
       added_at INTEGER NOT NULL
     )`,
    `CREATE TABLE IF NOT EXISTS collections(
       id TEXT PRIMARY KEY,
       name TEXT NOT NULL,
       created_at INTEGER NOT NULL
     )`,
    // `position` keeps the order a list was built in. Renumbered on insert,
    // which is cheap for lists this size and means a read is never a sort
    // over something that might have gone inconsistent.
    `CREATE TABLE IF NOT EXISTS collection_items(
       collection_id TEXT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       position INTEGER NOT NULL,
       PRIMARY KEY(collection_id, set_id)
     )`,
    `CREATE TABLE IF NOT EXISTS state_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)`,
  ],

  // v1 -> v2: everything belongs to a profile.
  //
  // A rebuild rather than an `ALTER`, because `profile_id` joins the primary
  // key of three of these and SQLite cannot add a column to one. Run with
  // foreign keys off — see `open()` — which is what makes dropping a table
  // another one references safe for the length of the migration.
  [
    `CREATE TABLE IF NOT EXISTS profiles(
       id TEXT PRIMARY KEY,
       name TEXT NOT NULL,
       created_at INTEGER NOT NULL
     )`,
    // Whatever was recorded before there were profiles belonged to whoever
    // was watching, so it becomes theirs rather than being dropped on the
    // floor. Created only if there is anything to adopt: a player that had
    // recorded nothing starts with no profiles and asks who is watching.
    `INSERT OR IGNORE INTO profiles(id, name, created_at)
       SELECT 'everyone', 'Everyone', CAST(strftime('%s','now') AS INTEGER) * 1000
        WHERE EXISTS(SELECT 1 FROM progress)
           OR EXISTS(SELECT 1 FROM watchlist)
           OR EXISTS(SELECT 1 FROM collections)`,

    `CREATE TABLE progress_next(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       at_seconds REAL NOT NULL,
       duration REAL,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )`,
    `INSERT INTO progress_next(profile_id, set_id, at_seconds, duration, updated_at)
       SELECT 'everyone', set_id, at_seconds, duration, updated_at FROM progress`,
    `DROP TABLE progress`,
    `ALTER TABLE progress_next RENAME TO progress`,
    `CREATE INDEX IF NOT EXISTS progress_recent ON progress(profile_id, updated_at DESC)`,

    `CREATE TABLE watchlist_next(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       added_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )`,
    `INSERT INTO watchlist_next(profile_id, set_id, added_at)
       SELECT 'everyone', set_id, added_at FROM watchlist`,
    `DROP TABLE watchlist`,
    `ALTER TABLE watchlist_next RENAME TO watchlist`,

    // `id` stays the key, so `collection_items` keeps pointing at the same
    // rows and needs no rebuild of its own.
    `CREATE TABLE collections_next(
       id TEXT PRIMARY KEY,
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       name TEXT NOT NULL,
       created_at INTEGER NOT NULL
     )`,
    `INSERT INTO collections_next(id, profile_id, name, created_at)
       SELECT id, 'everyone', name, created_at FROM collections`,
    `DROP TABLE collections`,
    `ALTER TABLE collections_next RENAME TO collections`,
    `CREATE INDEX IF NOT EXISTS collections_of ON collections(profile_id, created_at)`,
  ],

  // v2 -> v3: which titles are for children.
  //
  // The one table here with no `profile_id`, and deliberately. Everything
  // else was given one because a watch position and a list belong to a
  // person; this belongs to the title. Marking a film as a child's film is
  // not a statement about who is watching, and a mark that had to be made
  // again on every profile would be wrong on the second television in the
  // house as well as tedious on the first.
  [
    `CREATE TABLE IF NOT EXISTS kids(
       set_id TEXT PRIMARY KEY,
       marked_at INTEGER NOT NULL
     )`,
  ],

  // v3 -> v4: what has been watched to the end.
  //
  // Recorded because finishing a title *erases* it otherwise: the position is
  // cleared on completion, which is right for the Continue shelf and leaves
  // "watched it all" and "never opened it" identical everywhere else.
  //
  // Scoped to a profile, unlike `kids` and for the opposite reason. A
  // children's film is a fact about the title; having watched something is a
  // fact about the viewer, and two people sharing a television must not tick
  // each other's episodes off.
  [
    `CREATE TABLE IF NOT EXISTS watched(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       finished_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id)
     )`,
  ],

  // v4 -> v5: what a viewer chose, so they do not choose it again.
  //
  // A German/English series is twenty-two episodes, and picking English was
  // picking it twenty-two times — each one costing a conversion. The same is
  // true of a subtitle language, and of a playback speed on a lecture course.
  //
  // `scope` is a show, not a set: `preference-scope.js` decides what that
  // means and files by the same thing the shelves group by, so a preference
  // never belongs to a group no shelf draws.
  //
  // `name`/`value` as rows rather than a column each. A column per preference
  // is a migration per preference, and these arrive steadily — three land with
  // this table and four more with the subtitle panel. The cost is that nothing
  // constrains a value, so the reader that turns one back into a choice is
  // where validation lives and is written as if the row were hostile.
  //
  // Scoped to a profile, like `watched` and unlike `kids`: which language you
  // watch a series in is a fact about you, and two people sharing a television
  // should not overwrite each other's.
  [
    `CREATE TABLE IF NOT EXISTS preferences(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       scope TEXT NOT NULL,
       name TEXT NOT NULL,
       value TEXT NOT NULL,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, scope, name)
     )`,
  ],
];

/** Every statement needed to reach `version` from nothing. */
export function migrationsUpTo(version: number): string[] {
  return GROUPS.slice(0, Math.max(0, version)).flatMap((group) => [...group]);
}
