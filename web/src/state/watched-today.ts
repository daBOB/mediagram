/**
 * What a kid's daily limit is measured against: the seconds watched today on
 * every device, summed from the day rows the stats already keep.
 *
 * The server is the counter. The page never adds up time of its own; it asks
 * (`GET /api/profiles/{id}/today`) or reads the answer to a position write.
 */

import type { Database } from "bun:sqlite";
import { localDay } from "./stats-recorder";

/** The limit in minutes (`null`: none) and today's watching in seconds. */
export interface Today {
  limitMinutes: number | null;
  watchedSeconds: number;
}

/** `null` when there is no such profile. A grown-up has no limit, so none shows. */
export function readToday(db: Database | null, profileId: string, nowMs: number): Today | null {
  const row = db?.query("SELECT kids, daily_limit AS limitMinutes FROM profiles WHERE id = ?1")
    .get(profileId) as { kids: number; limitMinutes: number | null } | null | undefined;
  if (!db || !row) return null;
  const sum = db.query("SELECT COALESCE(SUM(seconds), 0) AS seconds FROM stats_days WHERE profile_id = ?1 AND day = ?2")
    .get(profileId, localDay(nowMs)) as { seconds: number };
  return { limitMinutes: row.kids !== 0 ? row.limitMinutes : null, watchedSeconds: sum.seconds };
}
