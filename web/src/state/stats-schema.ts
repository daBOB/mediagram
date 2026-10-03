/**
 * v10 -> v11: viewing stats — how long each title was watched, and how long
 * each day — per profile and per counting device.
 *
 * Its own file only because `schema.ts` is at its line limit; `GROUPS` lists
 * it like every other group, so the version is still `GROUPS.length`.
 *
 * `device` is in both keys because a device writes only its own rows and
 * imports everyone else's unchanged: a total is a sum over devices, so a
 * merge that sees the same row twice cannot count it twice. Kept forever —
 * a day row is a few dozen bytes, and the history is the point.
 *
 * Cascades with the profile, like every other table that belongs to one.
 */
export const STATS_GROUP: readonly string[] = [
  `CREATE TABLE IF NOT EXISTS stats_titles(
     profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
     set_id TEXT NOT NULL,
     device TEXT NOT NULL,
     started_at INTEGER NOT NULL,
     last_watched_at INTEGER NOT NULL,
     seconds REAL NOT NULL,
     again_at INTEGER,
     updated_at INTEGER NOT NULL,
     PRIMARY KEY(profile_id, set_id, device)
   )`,
  `CREATE TABLE IF NOT EXISTS stats_days(
     profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
     day TEXT NOT NULL,
     device TEXT NOT NULL,
     seconds REAL NOT NULL,
     updated_at INTEGER NOT NULL,
     PRIMARY KEY(profile_id, day, device)
   )`,
];
