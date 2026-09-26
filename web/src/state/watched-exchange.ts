/**
 * `watched` on the sync record: exporting both a live mark and its removal,
 * and taking either back in.
 *
 * Kept out of `store.ts` for the same reason `lists-exchange.ts` is: the
 * shape here is the one with a tombstone, and turning the `removed_at`
 * column into a row for the wire — and a row off the wire back into that
 * column — is one job, not a pair of `importMerged` branches.
 *
 * A live mark and its removal are exclusive by the time they reach here:
 * `watched-reconcile.ts` has already decided, per title, which one a merge
 * agreed on. `importWatched` therefore stays a single-kind writer, the same
 * shape `import_progress` already is — no branch inside the loop deciding
 * which fact a row represents.
 */

import type { Database } from "bun:sqlite";
import type { UnwatchedRow, WatchedRow } from "./sync-record";

interface StandingWatched {
  updatedAt: number;
  removedAt: number | null;
}

function standingFor(db: Database, profileId: string, setId: string): StandingWatched | null {
  return db
    .query(
      "SELECT removed_at AS removedAt, CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE finished_at END AS updatedAt " +
        "FROM watched WHERE profile_id = ?1 AND set_id = ?2",
    )
    .get(profileId, setId) as StandingWatched | null;
}

/** Every `watched` row this profile has, live and removed, for the wire. */
export function exportWatched(
  db: Database | null,
  profileId: string,
): { watched: WatchedRow[]; unwatched: UnwatchedRow[] } {
  if (!db) return { watched: [], unwatched: [] };
  const rows = db
    .query(
      "SELECT set_id AS setId, finished_at AS finishedAt, removed_at AS removedAt FROM watched WHERE profile_id = ?1",
    )
    .all(profileId) as { setId: string; finishedAt: number; removedAt: number | null }[];

  const watched: WatchedRow[] = [];
  const unwatched: UnwatchedRow[] = [];
  for (const row of rows) {
    if (row.removedAt !== null) {
      unwatched.push({ setId: row.setId, updatedAt: row.removedAt, lastFinishedAt: row.finishedAt });
    } else {
      watched.push({ setId: row.setId, updatedAt: row.finishedAt });
    }
  }
  return { watched, unwatched };
}

/**
 * A completion supersedes a stale local position, whether or not it is
 * news: a device that already knew of it may still hold a position another
 * device has only now reported. Applied unconditionally, before the
 * standing check below decides whether the mark itself is written — the
 * position's fate and the mark's are not the same question.
 */
export function importWatched(db: Database | null, profileId: string, rows: WatchedRow[]): number {
  if (!db) return 0;
  let changed = 0;
  for (const row of rows) {
    changed += db
      .query("DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3")
      .run(profileId, row.setId, row.updatedAt).changes;

    const standing = standingFor(db, profileId, row.setId);
    if (standing !== null && standing.updatedAt >= row.updatedAt) continue;
    db.query(
      `INSERT INTO watched(profile_id, set_id, finished_at, removed_at) VALUES (?1, ?2, ?3, NULL)
         ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at, removed_at = NULL`,
    ).run(profileId, row.setId, row.updatedAt);
    changed += 1;
  }
  return changed;
}

/**
 * `lastFinishedAt` is the completion this removal took the mark from, so it
 * supersedes a stale local position the same way a live completion's own
 * time would — `merge.ts` already decided a rewatch made since survives.
 */
export function importUnwatched(db: Database | null, profileId: string, rows: UnwatchedRow[]): number {
  if (!db) return 0;
  let changed = 0;
  for (const row of rows) {
    changed += db
      .query("DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2 AND updated_at <= ?3")
      .run(profileId, row.setId, row.lastFinishedAt).changes;

    const standing = standingFor(db, profileId, row.setId);
    // `watched-reconcile.ts` gives an exact tie to the removal, not a
    // device-id tie-break, whenever the standing fact is a *live* mark — so
    // import must skip one only when that live mark is strictly newer.
    // Standing already a removal is the ordinary same-kind case, where a
    // tie changes nothing either way.
    const outrun = standing !== null &&
      (standing.removedAt !== null ? standing.updatedAt >= row.updatedAt : standing.updatedAt > row.updatedAt);
    if (outrun) continue;
    db.query(
      `INSERT INTO watched(profile_id, set_id, finished_at, removed_at) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(profile_id, set_id) DO UPDATE SET finished_at = excluded.finished_at, removed_at = excluded.removed_at`,
    ).run(profileId, row.setId, row.lastFinishedAt, row.updatedAt);
    changed += 1;
  }
  return changed;
}
