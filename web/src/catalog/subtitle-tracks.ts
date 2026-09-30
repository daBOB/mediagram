/**
 * A set's subtitle tracks: the v13 `subtitle_tracks` rows, or — for an index
 * an uploader has not re-published since — the legacy `assets` rows every
 * reader already knew how to serve.
 *
 * Both layouts are read until phase 09 removes the older one: a v13 row wins
 * outright for a set that has one, an inline set is numbered `0..n` by
 * `ORDER BY lang` the way it always was, and every read tolerates the table
 * itself being absent, the same way `catalog/assets.ts` already does for
 * `summary`.
 */

import type { Database } from "bun:sqlite";
import { languageLabel } from "../../public/lib/language-label.js";

/** One subtitle track the catalog offers for a set, API-shaped. */
export interface SubtitleTrack {
  track: number;
  lang: string;
  forced: boolean;
  sdh: boolean;
  label: string;
}

/** Where a set's bundle lives, as `subtitle_files` records it. */
export interface BundleRef {
  messageId: number;
  bytes: number;
  sha256: string;
}

function tolerate<T>(table: string, read: () => T, fallback: T): T {
  try {
    return read();
  } catch (error) {
    if (error instanceof Error && error.message === `no such table: ${table}`) return fallback;
    throw error;
  }
}

/**
 * Every set's tracks, built once per router.
 *
 * v13 rows for a set that has them; the legacy `assets` rows, numbered by
 * `ORDER BY lang`, for one that does not. A set is never a mix of both: a
 * bundle replaces its set's inline rows the moment the uploader writes one
 * (`merge_subtitles.rs`), so the two tables agree on which sets each covers.
 */
export function subtitleTracksBySet(db: Database): Map<string, SubtitleTrack[]> {
  const bySet = new Map<string, SubtitleTrack[]>();

  tolerate("subtitle_tracks", () => {
    const rows = db
      .query(
        `SELECT set_id AS setId, track, lang, forced, sdh, label
           FROM subtitle_tracks ORDER BY set_id, track`,
      )
      .all() as { setId: string; track: number; lang: string; forced: number; sdh: number; label: string }[];
    for (const row of rows) {
      const list = bySet.get(row.setId) ?? [];
      list.push({ track: row.track, lang: row.lang, forced: row.forced !== 0, sdh: row.sdh !== 0, label: row.label });
      bySet.set(row.setId, list);
    }
  }, undefined);

  // A bundled set's inline rows are stale leftovers `merge_copy.rs` refuses
  // to refill, but a hand-made or half-migrated database is not promised
  // that, so the bundle still wins here rather than doubling up — checked
  // against the sets v13 covered *before* this loop started, since the loop
  // itself is filling `bySet` too.
  const bundled = new Set(bySet.keys());
  tolerate("assets", () => {
    const rows = db
      .query("SELECT set_id AS setId, lang FROM assets WHERE kind = 'subtitle' ORDER BY set_id, lang")
      .all() as { setId: string; lang: string }[];
    for (const row of rows) {
      if (bundled.has(row.setId)) continue;
      const list = bySet.get(row.setId) ?? [];
      list.push({ track: list.length, lang: row.lang, forced: false, sdh: false, label: languageLabel(row.lang, row.lang) });
      bySet.set(row.setId, list);
    }
  }, undefined);

  return bySet;
}

/** Where `setId`'s bundle lives, or `null` for a set with no v13 bundle. */
export function bundleRef(db: Database, setId: string): BundleRef | null {
  return tolerate("subtitle_files", () => {
    const row = db
      .query("SELECT message_id AS messageId, bytes, sha256 FROM subtitle_files WHERE set_id = ?1")
      .get(setId) as BundleRef | null;
    return row;
  }, null);
}

/** One legacy inline track's body, by its position in `ORDER BY lang`. */
export function legacyBody(db: Database, setId: string, track: number): string | null {
  return tolerate("assets", () => {
    const rows = db
      .query("SELECT body FROM assets WHERE set_id = ?1 AND kind = 'subtitle' ORDER BY lang")
      .all(setId) as { body: string }[];
    return rows[track]?.body ?? null;
  }, null);
}
