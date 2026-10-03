/**
 * Viewing stats between the tables and the wire, and what the stats page
 * reads: the summary and the achievements.
 *
 * Kept out of `store.ts` for the reason `watched-exchange.ts` is: turning
 * rows into wire rows and back is one job. Every device's rows go out, not
 * only this one's — passed on, so a device that goes away keeps its minutes —
 * and nothing is ever deleted on the way back in.
 */

import type { Database } from "bun:sqlite";
import type { DayStatRow, StatsRows, TitleStatRow } from "./stats-record";
import { summarize, type StatsSummary } from "./stats-summary";
import { achievements, type AchievementLibrary, type Achievements } from "./achievements";
import { localDay, utcOffsetMinutes } from "./stats-recorder";

/** Every row this profile holds, every device's, in a stable order. */
function statsRows(db: Database | null, profileId: string): { titles: TitleStatRow[]; days: DayStatRow[] } {
  if (!db) return { titles: [], days: [] };
  const titles = (db
    .query(
      `SELECT set_id AS setId, device, started_at AS startedAt, last_watched_at AS lastWatchedAt,
              seconds, again_at AS againAt, updated_at AS updatedAt
         FROM stats_titles WHERE profile_id = ?1 ORDER BY set_id, device`,
    )
    .all(profileId) as (Omit<TitleStatRow, "againAt"> & { againAt: number | null })[])
    // Absent, never `null`: a reader drops a row whose `againAt` is not a number.
    .map(({ againAt, ...row }) => (againAt === null ? row : { ...row, againAt }));
  const days = db
    .query("SELECT day, device, seconds, updated_at AS updatedAt FROM stats_days WHERE profile_id = ?1 ORDER BY day, device")
    .all(profileId) as DayStatRow[];
  return { titles, days };
}

/** The two keys for the wire, each omitted when empty — so a profile with no
 * stats exports exactly what it did before stats existed. */
export function exportStats(db: Database | null, profileId: string): StatsRows {
  const { titles, days } = statsRows(db, profileId);
  return {
    ...(titles.length > 0 ? { titleStats: titles } : {}),
    ...(days.length > 0 ? { dayStats: days } : {}),
  };
}

/**
 * Takes in each merged row that is newer than the local one, or missing.
 * Never deletes. A row naming this device is taken too when newer: a
 * reinstall that kept its device id gets its minutes back.
 */
export function importStats(db: Database, profileId: string, rows: StatsRows): number {
  let changed = 0;
  for (const row of rows.titleStats ?? []) {
    changed += db.query(
      `INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
         ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
           started_at = excluded.started_at, last_watched_at = excluded.last_watched_at, seconds = excluded.seconds,
           again_at = excluded.again_at, updated_at = excluded.updated_at
         WHERE excluded.updated_at > stats_titles.updated_at`,
    ).run(profileId, row.setId, row.device, row.startedAt, row.lastWatchedAt, row.seconds, row.againAt ?? null, row.updatedAt)
      .changes;
  }
  for (const row of rows.dayStats ?? []) {
    changed += db.query(
      `INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(profile_id, day, device) DO UPDATE SET seconds = excluded.seconds, updated_at = excluded.updated_at
         WHERE excluded.updated_at > stats_days.updated_at`,
    ).run(profileId, row.day, row.device, row.seconds, row.updatedAt).changes;
  }
  return changed;
}

/** No catalog to count against: the achievements that need one count nothing. */
export const NO_LIBRARY: AchievementLibrary = { library: [], collections: [] };

/**
 * What the stats page shows for `profileId` at `nowMs`: the summary, as of
 * that day on this machine's calendar, and the achievements, counted against
 * `library` at this machine's offset from UTC at that moment.
 */
export function readStats(
  db: Database | null,
  profileId: string,
  nowMs: number,
  library: AchievementLibrary,
): StatsSummary & { achievements: Achievements } {
  const today = localDay(nowMs);
  const { titles, days } = statsRows(db, profileId);
  const watched = (db
    ?.query("SELECT set_id AS setId, finished_at AS finishedAt FROM watched WHERE profile_id = ?1 AND removed_at IS NULL")
    .all(profileId) ?? []) as { setId: string; finishedAt: number }[];
  const profile = db?.query("SELECT kids FROM profiles WHERE id = ?1").get(profileId) as { kids: number } | null | undefined;
  return {
    ...summarize({ today, titles, days, watched }),
    achievements: achievements({
      today,
      utcOffsetMinutes: utcOffsetMinutes(nowMs),
      kids: (profile?.kids ?? 0) !== 0,
      days,
      watched,
      ...library,
    }),
  };
}
