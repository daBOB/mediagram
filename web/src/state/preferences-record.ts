/**
 * The subtitle choices that follow a viewer from device to device.
 *
 * Every other preference (audio, speed, framing) is about the screen and the
 * room it is in and stays on the device that made it; these four are about the
 * viewer, so a language picked on the tablet should be on when the same person
 * opens a film on the television.
 *
 * **No row is ever deleted.** A synced name carries no tombstone: a delete on
 * one device would come back from every other device that still holds the row.
 * Every writer stores a value (`off`, a track key, a size) instead, which is
 * also a perfectly good thing to sync. `setPreference`'s forget path is for
 * the names that never leave the device.
 *
 * Kept out of `store.ts` and `sync-record.ts` for the same reason
 * `lists-exchange.ts` is: one job — the wire row and the table — rather than
 * more methods on a class that already carries everything.
 */

import type { Database } from "bun:sqlite";

/** The names that travel, in every scope (`key:`, `show:`, `set:`, `profile`). */
const SYNCED = new Set(["subtitle", "cue-size", "cue-backing", "cue-offset"]);

/** Whether `name` travels between devices, and so may be changed but never forgotten. */
export const isSyncedPreference = (name: string): boolean => SYNCED.has(name);

/** The same cap `store.ts` puts on what a player writes; this caps what a
 * stranger's document is allowed to claim. */
const MAX_PREFERENCE = 200;

export interface PreferenceRow {
  scope: string;
  name: string;
  value: string;
  updatedAt: number;
}

/** A string, trimmed and capped, or `null` when nothing is left. */
function capped(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const clean = value.trim().slice(0, MAX_PREFERENCE);
  return clean === "" ? null : clean;
}

/** Hostile input: a bad row is dropped, never the document. */
export function parsePreferenceRows(value: unknown): PreferenceRow[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((entry): PreferenceRow[] => {
    if (entry === null || typeof entry !== "object") return [];
    const raw = entry as Record<string, unknown>;
    const scope = capped(raw.scope);
    const name = capped(raw.name);
    const held = capped(raw.value);
    const updatedAt = typeof raw.updatedAt === "object" ? Number.NaN : Number(raw.updatedAt);
    if (scope === null || name === null || held === null || !SYNCED.has(name)) return [];
    // Past 2^53 a local write's `stored + 1` stamp no longer advances, and the
    // peer's row would win every tie from then on.
    if (!Number.isSafeInteger(updatedAt) || updatedAt <= 0) return [];
    return [{ scope, name, value: held, updatedAt }];
  });
}

export function exportPreferences(db: Database | null, profileId: string): PreferenceRow[] {
  if (!db) return [];
  const rows = db
    .query("SELECT scope, name, value, updated_at AS updatedAt FROM preferences WHERE profile_id = ?1")
    .all(profileId) as PreferenceRow[];
  return rows.filter((row) => SYNCED.has(row.name));
}

/**
 * Takes in the merged rows that are newer than the local ones. The merge
 * already broke equal-time ties by device, so an equal time with a different
 * value is the winner and is applied; an identical row is not a change.
 */
export function importPreferences(db: Database, profileId: string, rows: PreferenceRow[]): number {
  let changed = 0;
  for (const row of rows) {
    if (!SYNCED.has(row.name)) continue;
    const standing = db
      .query("SELECT value, updated_at AS updatedAt FROM preferences WHERE profile_id = ?1 AND scope = ?2 AND name = ?3")
      .get(profileId, row.scope, row.name) as { value: string; updatedAt: number } | null;
    if (standing !== null && (standing.updatedAt > row.updatedAt ||
      (standing.updatedAt === row.updatedAt && standing.value === row.value))) continue;
    db.query(
      `INSERT INTO preferences(profile_id, scope, name, value, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(profile_id, scope, name)
           DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
    ).run(profileId, row.scope, row.name, row.value, row.updatedAt);
    changed += 1;
  }
  return changed;
}

/**
 * The time to stamp a write with: never earlier than the row already carries,
 * so a clock that stepped back (or a row imported from a device whose clock
 * runs ahead) cannot make a choice made now lose to an older one.
 */
export function preferenceStamp(db: Database, profileId: string, scope: string, name: string): number {
  const stored = db
    .query("SELECT updated_at AS updatedAt FROM preferences WHERE profile_id = ?1 AND scope = ?2 AND name = ?3")
    .get(profileId, scope, name) as { updatedAt: number } | null;
  return Math.max(Date.now(), (stored?.updatedAt ?? 0) + 1);
}
