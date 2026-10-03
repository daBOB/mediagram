/**
 * A position write, and the watching it adds up to — in one transaction.
 *
 * `WatchState.setProgress` calls this rather than writing the position
 * itself, so a position and the minutes it implies can never land apart.
 * Only this device's own writes come through here: positions merged in from
 * other devices (`importMerged`) are never counted, or every sync round would
 * count another device's evening again.
 *
 * The last write per title is held in memory by the caller and never stored
 * or synced: a restarted server starts empty, and its first write per title
 * counts nothing. See `stats-step.ts` for what one step counts.
 */

import type { Database } from "bun:sqlite";
import { againNow, stepSeconds, type LastTick } from "./stats-step";

/** Each title's last position write, keyed by `tickKey`. */
export type Ticks = Map<string, LastTick>;

/** The key a title's last write is held under: JSON, as ids may hold any text. */
export const tickKey = (profileId: string, setId: string) => JSON.stringify([profileId, setId]);

/** `YYYY-MM-DD` on this machine's own calendar — the household server's. */
export function localDay(ms: number): string {
  const date = new Date(ms);
  const two = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())}`;
}

export const UPSERT_PROGRESS = `INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?5)
  ON CONFLICT(profile_id, set_id) DO UPDATE SET
    at_seconds = excluded.at_seconds,
    duration = excluded.duration,
    updated_at = excluded.updated_at`;

// `started_at` is written once, by the insert; `again_at` only ever moves
// forward to a later restart, never back to null. `updated_at` never moves
// back either: a clock that stepped back must not give this device's newer
// row an older stamp than a copy other devices hold, or the next import would
// put the older seconds back over it.
const UPSERT_TITLE = `INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?6, ?4)
  ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
    seconds = seconds + excluded.seconds,
    last_watched_at = excluded.last_watched_at,
    again_at = COALESCE(excluded.again_at, again_at),
    updated_at = MAX(excluded.updated_at, updated_at + 1)`;

const UPSERT_DAY = `INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
  VALUES (?1, ?2, ?3, ?4, ?5)
  ON CONFLICT(profile_id, day, device) DO UPDATE SET
    seconds = seconds + excluded.seconds,
    updated_at = MAX(excluded.updated_at, updated_at + 1)`;

/**
 * Writes where `profileId` is in `setId`, and counts the step since the
 * title's last write into its title row and today's day row, all or none.
 * A refusal (a profile another device deleted) rolls everything back and
 * leaves `ticks` as it was.
 */
export function writeProgress(
  db: Database,
  ticks: Ticks,
  device: string,
  profileId: string,
  setId: string,
  rawAt: number,
  duration: number | null,
): void {
  const at = Math.max(0, rawAt);
  const now = Date.now();
  const key = tickKey(profileId, setId);
  const step = stepSeconds(ticks.get(key), at, now);
  db.transaction(() => {
    const hadProgress = db.query("SELECT 1 FROM progress WHERE profile_id = ?1 AND set_id = ?2")
      .get(profileId, setId) !== null;
    const watchedLive = db.query("SELECT 1 FROM watched WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL")
      .get(profileId, setId) !== null;
    db.query(UPSERT_PROGRESS).run(profileId, setId, at, duration, now);
    db.query(UPSERT_TITLE).run(profileId, setId, device, now, step, againNow(hadProgress, watchedLive) ? now : null);
    // A step that spans midnight counts on the day of the write that ends it.
    if (step > 0) db.query(UPSERT_DAY).run(profileId, localDay(now), device, step, now);
  })();
  ticks.set(key, { at, wallMs: now });
}
