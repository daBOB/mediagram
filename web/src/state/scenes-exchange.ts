/**
 * Marked scenes on the sync record: the rows a profile's `scenes` table
 * holds, as the wire says them and back.
 *
 * Like the watchlist a removal is a tombstone (`removed_at`), but a row is
 * keyed by its own `id` — one title holds many marks — and carries the film
 * second `at`, which keeps its milliseconds.
 */

import type { Database } from "bun:sqlite";
import { errorCode } from "../failure-message";
import type { SceneRow } from "./scenes-record";

export interface Scene {
  id: string;
  setId: string;
  at: number;
  markedAt: number;
}

/** A profile's live marks, newest first; the rowid breaks a tie within one millisecond. */
export function readScenes(db: Database | null, profileId: string): Scene[] {
  if (!db) return [];
  return db
    .query(
      `SELECT id, set_id AS setId, at_seconds AS at, marked_at AS markedAt
         FROM scenes WHERE profile_id = ?1 AND removed_at IS NULL ORDER BY marked_at DESC, rowid DESC`,
    )
    .all(profileId) as Scene[];
}

/** The new mark's id, or null for an `at` that is no film second or a profile that is gone. */
export function markScene(db: Database, profileId: string, setId: string, at: number): string | null {
  if (!Number.isFinite(at) || at < 0) return null;
  const id = crypto.randomUUID().replaceAll("-", "").slice(0, 16);
  try {
    db.query("INSERT INTO scenes(id, profile_id, set_id, at_seconds, marked_at) VALUES (?1, ?2, ?3, ?4, ?5)")
      .run(id, profileId, setId, at, Date.now());
    return id;
  } catch (error) {
    // A profile deleted on another device: the mark has nowhere to go.
    if (errorCode(error) === "SQLITE_CONSTRAINT_FOREIGNKEY") return null;
    throw error;
  }
}

/** Keeps the mark as a tombstone; false when no live mark has that id. */
export function removeScene(db: Database, profileId: string, id: string): boolean {
  return db
    .query("UPDATE scenes SET removed_at = ?3 WHERE id = ?2 AND profile_id = ?1 AND removed_at IS NULL")
    .run(profileId, id, Date.now()).changes > 0;
}

export function exportScenes(db: Database | null, profileId: string): SceneRow[] {
  if (!db) return [];
  const rows = db
    .query("SELECT id, set_id AS setId, at_seconds AS at, marked_at AS markedAt, removed_at AS removedAt FROM scenes WHERE profile_id = ?1")
    .all(profileId) as { id: string; setId: string; at: number; markedAt: number; removedAt: number | null }[];
  return rows.map((row) =>
    row.removedAt !== null
      ? { id: row.id, setId: row.setId, at: row.at, updatedAt: row.removedAt, removed: true }
      : { id: row.id, setId: row.setId, at: row.at, updatedAt: row.markedAt },
  );
}

/** Corrective, like every import: newer local news is left alone. An id
 * another profile already holds is skipped rather than moved — ids are random,
 * so that is a document lying, not a collision. */
export function importScenes(db: Database, profileId: string, rows: SceneRow[]): number {
  let changed = 0;
  for (const row of rows) {
    const standing = db
      .query(
        "SELECT profile_id AS profileId, removed_at AS removedAt, CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END AS updatedAt FROM scenes WHERE id = ?1",
      )
      .get(row.id) as { profileId: string; updatedAt: number; removedAt: number | null } | null;
    if (standing !== null && (standing.profileId !== profileId || standing.updatedAt > row.updatedAt ||
      (standing.updatedAt === row.updatedAt && (standing.removedAt !== null) === !!row.removed))) continue;

    db.query(
      `INSERT INTO scenes(id, profile_id, set_id, at_seconds, marked_at, removed_at) VALUES (?1, ?2, ?3, ?4, ?5, ?6)
         ON CONFLICT(id) DO UPDATE SET set_id = excluded.set_id, at_seconds = excluded.at_seconds,
           marked_at = CASE WHEN excluded.removed_at IS NULL THEN excluded.marked_at ELSE marked_at END,
           removed_at = excluded.removed_at`,
    ).run(row.id, profileId, row.setId, row.at, row.updatedAt, row.removed ? row.updatedAt : null);
    changed += 1;
  }
  return changed;
}
