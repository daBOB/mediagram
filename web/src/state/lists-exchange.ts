/**
 * Watchlist, Kids and collections on the sync record.
 *
 * Kept out of `store.ts`, which already carries every other read and write:
 * these three are the ones with a tombstone (`removed_at`) rather than a
 * plain delete, and turning that column into a row for the wire — and a row
 * off the wire back into that column — is one job, not a dozen methods on
 * `WatchState` that only ever look at the live half of these tables.
 *
 * Every function takes the raw `Database | null` that `WatchState` holds.
 * A null database yields empty reads or zero changes. SQLite failures
 * propagate so `importMerged` can roll back its complete transaction.
 */

import type { Database } from "bun:sqlite";
import type { CollectionRow, ListRow } from "./sync-record";

/** A row's LWW timestamp is whichever of adding/marking or removing it this
 * store last recorded — the later of the two, since only one is ever set. */
function toListRow(setId: string, addedOrMarkedAt: number, removedAt: number | null): ListRow {
  return removedAt !== null ? { setId, updatedAt: removedAt, removed: true } : { setId, updatedAt: addedOrMarkedAt };
}

/** The titles marked as a child's, tombstones included — everything the
 * wire needs to say. `kids()` on `WatchState` is the live-only half of this. */
export function exportKids(db: Database | null): ListRow[] {
  if (!db) return [];
  const rows = db
    .query("SELECT set_id AS setId, marked_at AS markedAt, removed_at AS removedAt FROM kids")
    .all() as { setId: string; markedAt: number; removedAt: number | null }[];
  return rows.map((row) => toListRow(row.setId, row.markedAt, row.removedAt));
}

export function exportWatchlist(db: Database | null, profileId: string): ListRow[] {
  if (!db) return [];
  const rows = db
    .query(
      "SELECT set_id AS setId, added_at AS addedAt, removed_at AS removedAt FROM watchlist WHERE profile_id = ?1",
    )
    .all(profileId) as { setId: string; addedAt: number; removedAt: number | null }[];
  return rows.map((row) => toListRow(row.setId, row.addedAt, row.removedAt));
}

export function exportCollections(db: Database | null, profileId: string): CollectionRow[] {
  if (!db) return [];
  const heads = db
    .query(
      "SELECT id, name, updated_at AS updatedAt, removed_at AS removedAt FROM collections WHERE profile_id = ?1",
    )
    .all(profileId) as { id: string; name: string; updatedAt: number; removedAt: number | null }[];
  return heads.map((row) => ({
    id: row.id,
    name: row.name,
    items: (
      db
        .query("SELECT set_id AS setId FROM collection_items WHERE collection_id = ?1 ORDER BY position")
        .all(row.id) as { setId: string }[]
    ).map((item) => item.setId),
    updatedAt: row.updatedAt,
    removed: row.removedAt !== null ? true : undefined,
  }));
}

/** Takes in the kept titles. Corrective, like everything `importMerged`
 * calls: newer local news is left alone, while equal-time differences apply
 * the winner already selected by the merge's device-id tie-break. */
export function importKids(db: Database | null, rows: ListRow[]): number {
  if (!db) return 0;
  let changed = 0;
  for (const row of rows) {
    const standing = db
      .query(
        "SELECT removed_at AS removedAt, CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END AS updatedAt FROM kids WHERE set_id = ?1",
      )
      .get(row.setId) as { updatedAt: number; removedAt: number | null } | null;
    if (standing !== null && (standing.updatedAt > row.updatedAt ||
      (standing.updatedAt === row.updatedAt && (standing.removedAt !== null) === !!row.removed))) continue;

    if (row.removed) {
      db.query(
        `INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, ?2)
           ON CONFLICT(set_id) DO UPDATE SET removed_at = excluded.removed_at`,
      ).run(row.setId, row.updatedAt);
    } else {
      db.query(
        `INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, NULL)
           ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL`,
      ).run(row.setId, row.updatedAt);
    }
    changed += 1;
  }
  return changed;
}

export function importWatchlist(db: Database | null, profileId: string, rows: ListRow[]): number {
  if (!db) return 0;
  let changed = 0;
  for (const row of rows) {
    const standing = db
      .query(
        "SELECT removed_at AS removedAt, CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE added_at END AS updatedAt " +
          "FROM watchlist WHERE profile_id = ?1 AND set_id = ?2",
      )
      .get(profileId, row.setId) as { updatedAt: number; removedAt: number | null } | null;
    if (standing !== null && (standing.updatedAt > row.updatedAt ||
      (standing.updatedAt === row.updatedAt && (standing.removedAt !== null) === !!row.removed))) continue;

    if (row.removed) {
      db.query(
        `INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, ?3)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET removed_at = excluded.removed_at`,
      ).run(profileId, row.setId, row.updatedAt);
    } else {
      db.query(
        `INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, NULL)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET added_at = excluded.added_at, removed_at = NULL`,
      ).run(profileId, row.setId, row.updatedAt);
    }
    changed += 1;
  }
  return changed;
}

/**
 * Whole-list LWW: a collection is one row on the wire, so a newer row wins
 * outright — name, membership and all — rather than merging item by item.
 * The id it arrives with is kept rather than re-minted, so a later, older
 * write for the same list does not read as a second one.
 */
export function importCollections(db: Database | null, profileId: string, rows: CollectionRow[]): number {
  if (!db) return 0;
  let changed = 0;
  for (const row of rows) {
    const standing = db
      .query("SELECT name, removed_at AS removedAt, updated_at AS updatedAt FROM collections WHERE id = ?1 AND profile_id = ?2")
      .get(row.id, profileId) as { name: string; updatedAt: number; removedAt: number | null } | null;
    if (standing !== null && standing.updatedAt > row.updatedAt) continue;
    const items = [...new Set(row.items)];
    if (standing !== null && standing.updatedAt === row.updatedAt && standing.name === row.name &&
      (standing.removedAt !== null) === !!row.removed) {
      const heldItems = db.query("SELECT set_id AS setId FROM collection_items WHERE collection_id = ?1 ORDER BY position")
        .all(row.id) as { setId: string }[];
      if (heldItems.length === items.length && heldItems.every((held, index) => held.setId === items[index])) continue;
    }

    const removedAt = row.removed ? row.updatedAt : null;
    if (standing === null) {
      db.query(
        `INSERT INTO collections(id, profile_id, name, created_at, updated_at, removed_at)
           VALUES (?1, ?2, ?3, ?4, ?4, ?5)`,
      ).run(row.id, profileId, row.name, row.updatedAt, removedAt);
    } else {
      db.query(
        "UPDATE collections SET name = ?3, updated_at = ?4, removed_at = ?5 WHERE id = ?1 AND profile_id = ?2",
      ).run(row.id, profileId, row.name, row.updatedAt, removedAt);
    }

    db.query("DELETE FROM collection_items WHERE collection_id = ?1").run(row.id);
    items.forEach((setId, index) => {
      db.query(
        "INSERT INTO collection_items(collection_id, set_id, position) VALUES (?1, ?2, ?3) " +
          "ON CONFLICT(collection_id, set_id) DO NOTHING",
      ).run(row.id, setId, index);
    });
    changed += 1;
  }
  return changed;
}
