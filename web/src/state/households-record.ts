/**
 * The households on the sync record: a top-level `households` key, beside
 * `kids`, and each profile's `household` (`roles-record.ts`).
 *
 * New optional keys, not a `SYNC_FORMAT` bump, for the reason `sync-record.ts`
 * gives: an older reader drops them and merges everything else. Each field
 * carries its own stamp, so a rename on one device and a new admin on
 * another both stand (`households-merge.ts`). A field dated 0 is never
 * written, so an install nobody has changed says nothing an older build
 * would not.
 *
 * Read as hostile as the rest of the document: a bad field is dropped on its
 * own, and a row left saying nothing is dropped with it.
 */

import { HOME } from "../../public/lib/household-rules.js";
import { isStamp, numberFromScalar, objectRow, parseRows, text_ } from "./record-scalars";

/** One household as a device says it. */
export interface HouseholdEntry {
  id: string;
  name?: { text: string; updatedAt: number };
  /** Who runs it, by the name sync knows a viewer by, as `parent` is. */
  admin?: { name: string; updatedAt: number };
  /** Deleted, for good: no later write brings it back. Never on Home. */
  removed?: true;
}

/** `home`, a UUID or the core's ULID — and nothing a stranger could make longer. */
const ID = /^[A-Za-z0-9-]{1,64}$/;
const MAX_NAME = 120;

/** A household's id as the record may carry it, or `null`. */
export function householdIdOf(value: unknown): string | null {
  return typeof value === "string" && ID.test(value) ? value : null;
}

export function parseHouseholds(value: unknown): HouseholdEntry[] {
  return parseRows(value, householdEntry);
}

function householdEntry(value: unknown): HouseholdEntry | null {
  const raw = objectRow(value);
  const id = householdIdOf(raw?.id);
  if (raw === null || id === null) return null;
  const entry: HouseholdEntry = { id };

  const name = objectRow(raw.name);
  const text = text_(name?.text);
  const nameAt = numberFromScalar(name?.updatedAt);
  // Cut by code point, as the core's `chars()` does, so both keep the same text.
  if (text !== null && isStamp(nameAt)) entry.name = { text: Array.from(text).slice(0, MAX_NAME).join(""), updatedAt: nameAt };

  const admin = objectRow(raw.admin);
  const adminName = text_(admin?.name);
  const adminAt = numberFromScalar(admin?.updatedAt);
  if (adminName !== null && isStamp(adminAt)) entry.admin = { name: adminName, updatedAt: adminAt };

  // Only the literal true: a removal is final, so it is not guessed at.
  if (raw.removed === true && id !== HOME) entry.removed = true;
  return entry.name || entry.admin || entry.removed ? entry : null;
}
