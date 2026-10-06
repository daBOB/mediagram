/**
 * A set's summary.
 *
 * Lives in the index, put there by the uploader from the file sitting beside
 * the video, so reading it costs a local query rather than a Telegram round
 * trip. Subtitles are `subtitle-tracks.ts`'s: a bundle is a fetched file, not
 * text that rides every index push, and the inline fallback it also reads is
 * the same `assets` rows this file used to serve directly.
 *
 * Tolerates the table being absent. The player refuses an index older than
 * it understands before it gets this far, but a hand-made or half-migrated
 * database should degrade to "nothing here" rather than take the catalog down.
 */

import type { Database } from "bun:sqlite";
import { hasTable } from "../catalog";

/** A set's summary, or `null`. */
export function summary(db: Database, setId: string): string | null {
  if (!hasTable(db, "assets")) return null;
  const row = db
    .query("SELECT body FROM assets WHERE set_id = ?1 AND kind = 'summary' AND lang = ''")
    .get(setId) as { body: string } | null;
  return row?.body ?? null;
}
