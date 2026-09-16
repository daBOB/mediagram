/**
 * Text that belongs to a set: a summary, and subtitles per language.
 *
 * These live in the index, put there by the uploader from the files sitting
 * beside each video, so reading them costs a local query rather than a
 * Telegram round trip.
 *
 * Every read tolerates the table being absent. The player refuses an index
 * older than it understands before it gets this far, but a hand-made or
 * half-migrated database should degrade to "nothing here" rather than take
 * the catalog down.
 */

import type { Database } from "bun:sqlite";

function tolerate<T>(read: () => T, fallback: T): T {
  try {
    return read();
  } catch {
    return fallback;
  }
}

/** A set's summary, or `null`. */
export function summary(db: Database, setId: string): string | null {
  return tolerate(() => {
    const row = db
      .query("SELECT body FROM assets WHERE set_id = ?1 AND kind = 'summary' AND lang = ''")
      .get(setId) as { body: string } | null;
    return row?.body ?? null;
  }, null);
}

/** One subtitle track, as WebVTT, or `null`. */
export function subtitle(db: Database, setId: string, lang: string): string | null {
  return tolerate(() => {
    const row = db
      .query("SELECT body FROM assets WHERE set_id = ?1 AND kind = 'subtitle' AND lang = ?2")
      .get(setId, lang) as { body: string } | null;
    return row?.body ?? null;
  }, null);
}

/** The languages a set has subtitles for, in a stable order. */
export function subtitleLanguages(db: Database, setId: string): string[] {
  return tolerate(() => {
    const rows = db
      .query("SELECT lang FROM assets WHERE set_id = ?1 AND kind = 'subtitle' ORDER BY lang")
      .all(setId) as { lang: string }[];
    return rows.map((row) => row.lang);
  }, []);
}
