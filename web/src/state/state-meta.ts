/**
 * `state_meta`: this player's own bookkeeping — its device id, the schema
 * version, whether it has heard its household. Never exported, never synced.
 */

import type { Database } from "bun:sqlite";

export function readMeta(db: Database | null, key: string): string | null {
  const row = db?.query("SELECT value FROM state_meta WHERE key = ?1").get(key) as { value?: string } | null;
  return typeof row?.value === "string" ? row.value : null;
}

export function writeMeta(db: Database | null, key: string, value: string): void {
  db?.query("INSERT OR REPLACE INTO state_meta(key, value) VALUES (?1, ?2)").run(key, value);
}
