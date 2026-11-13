/**
 * Marked scenes: a moment in a title, many per title, so keyed by their own
 * id. A removal is a tombstone, like the watchlist's, so a merge can see it.
 *
 * Its own file because `schema.ts` is at its line limit; `GROUPS` lists it
 * like every other group.
 */

export const SCENES_GROUP: string[] = [
  `CREATE TABLE IF NOT EXISTS scenes(
     id TEXT PRIMARY KEY,
     profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
     set_id TEXT NOT NULL,
     at_seconds REAL NOT NULL,
     marked_at INTEGER NOT NULL,
     removed_at INTEGER
   )`,
];
