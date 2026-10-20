/**
 * Two rows of one person made one: what an older build leaves when it took a
 * renamed viewer in under the new name beside the row it had under the old.
 */

import type { Database } from "bun:sqlite";

/** Every table whose rows belong to one profile. */
const OWN_ROWS = ["progress", "watched", "watchlist", "collections", "preferences", "stats_titles", "stats_days"];

/**
 * Moves `fromId`'s rows to `intoId`, points whatever named it at `intoId`, and
 * deletes it.
 *
 * A key `intoId` already has stays its own: this device's export was part of
 * the merge, so the rows import that follows writes the newer of the two.
 * Collections move whole, ids and all, so the merge's copy of one finds it
 * under `intoId` rather than clashing with the id. Role columns are not
 * copied: the role import that follows applies whichever value the merge chose.
 */
export function foldProfile(db: Database, fromId: string, intoId: string): void {
  for (const table of OWN_ROWS) {
    db.query(`UPDATE OR IGNORE ${table} SET profile_id = ?2 WHERE profile_id = ?1`).run(fromId, intoId);
  }
  db.query("UPDATE profiles SET parent_id = ?2 WHERE parent_id = ?1").run(fromId, intoId);
  db.query("UPDATE households SET admin_id = ?2 WHERE admin_id = ?1").run(fromId, intoId);
  // Its leftovers cascade: each one a key `intoId` already has.
  db.query("DELETE FROM profiles WHERE id = ?1").run(fromId);
}
