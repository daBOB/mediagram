/**
 * A marked scene on the sync record: a moment in a title, kept apart from the
 * viewing position. A new optional key on a profile, not a format bump, for
 * the reason `sync-record.ts` gives.
 */

import { isStamp, numberFromScalar, objectRow, text_ } from "./record-scalars";

/**
 * A marked scene: a moment in a title, kept apart from the viewing position.
 * Rows are keyed by `id`, not `setId` — one title holds many marks — and
 * marks are never edited, so `updatedAt` is the marking time or the removal.
 */
export interface SceneRow {
  id: string;
  setId: string;
  /** Film seconds, to the millisecond. */
  at: number;
  updatedAt: number;
  removed?: true;
}

export function sceneRow(value: unknown): SceneRow | null {
  const raw = objectRow(value);
  if (raw === null) return null;
  const id = text_(raw.id);
  const setId = text_(raw.setId);
  const at = numberFromScalar(raw.at);
  const updatedAt = numberFromScalar(raw.updatedAt);
  if (id === null || setId === null || !Number.isFinite(at) || at < 0 || !isStamp(updatedAt)) return null;
  return raw.removed === true ? { id, setId, at, updatedAt, removed: true } : { id, setId, at, updatedAt };
}

