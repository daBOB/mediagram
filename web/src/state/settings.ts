/**
 * Values a viewer changed from the Settings page, kept in the player's own
 * database next to watch state.
 *
 * Tolerant like `WatchState` itself: a store with no database answers as if
 * nothing were set, and a row that fails validation on read is treated as
 * absent rather than handed to a caller that trusted its shape. The only
 * value here today is the cache budget; more can be added as more rows.
 */

import type { Database } from "bun:sqlite";

const CACHE_MAX_BYTES = "cache_max_bytes";

/** The floor a live cache budget may be set to. Below this, use 0 (env-only, off) instead. */
export const MIN_CACHE_BUDGET_BYTES = 512 * 1024 * 1024;
/** A ceiling past which a value is not a budget anyone meant to set. */
export const MAX_CACHE_BUDGET_BYTES = 16 * 1024 ** 4;

export class Settings {
  constructor(private readonly db: Database | null) {}

  /** The stored cache budget, or `null` when none is set or the row is unusable. */
  cacheMaxBytes(): number | null {
    if (!this.db) return null;
    const row = this.db
      .query("SELECT value FROM settings WHERE name = ?1")
      .get(CACHE_MAX_BYTES) as { value: string } | null;
    if (!row) return null;
    const n = Number(row.value);
    if (!Number.isSafeInteger(n) || n < MIN_CACHE_BUDGET_BYTES || n > MAX_CACHE_BUDGET_BYTES) return null;
    return n;
  }

  /** Records the budget a viewer chose. Silently does nothing without a database. */
  setCacheMaxBytes(bytes: number): void {
    if (!this.db) return;
    this.db
      .query(
        `INSERT INTO settings(name, value, updated_at) VALUES (?1, ?2, ?3)
           ON CONFLICT(name) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
      )
      .run(CACHE_MAX_BYTES, String(Math.trunc(bytes)), Date.now());
  }
}
