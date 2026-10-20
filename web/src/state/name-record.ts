/**
 * A renamed profile on the sync record: when it took the name it has now, and
 * the names it had before.
 *
 * Sync knows a viewer by its name, so a rename would otherwise make a new
 * viewer on every device. These two keys let the merge (`renames.ts`) treat a
 * name and its former names as one person. New optional keys rather than a
 * `SYNC_FORMAT` bump, for the reason `sync-record.ts` gives: an older reader
 * drops them and reads a profile under its new name.
 *
 * Read as hostile as the rest of the document: each key is checked on its own
 * and a bad one is dropped on its own, never the profile.
 */

import { isStamp, numberFromScalar } from "./record-scalars";
import { normalName } from "./sync-record";

/** The keys of a profile's entry that say what it was called. */
export interface NameKeys {
  /** When this profile took the name it has now; absent when nobody renamed it. */
  renamedAt?: number;
  /** Every name it had before, normalised, sorted by bytes, each once, never
   * its own; absent when empty. */
  formerNames?: string[];
}

/** A bound on what a document makes a device store; a longer list is dropped whole. */
export const MAX_FORMER_NAMES = 32;
/** Three times what a stored name may be (`profiles.ts`), in code points as the core counts them:
 * NFC and lower-casing can lengthen a name a device was allowed to store ("İ" becomes "i̇"). */
const MAX_NAME = 3 * 120;

/** Each name once, by UTF-8 bytes: the core's `String` order, not a collator's. */
export function sortedNames(names: Iterable<string>): string[] {
  return [...new Set(names)].sort((a, b) => Buffer.compare(Buffer.from(a), Buffer.from(b)));
}

export function parseNameKeys(row: Record<string, unknown>, own: string): NameKeys {
  const keys: NameKeys = {};

  const renamedAt = numberFromScalar(row.renamedAt);
  if (isStamp(renamedAt)) keys.renamedAt = renamedAt;

  const listed = row.formerNames;
  if (Array.isArray(listed) && listed.length <= MAX_FORMER_NAMES) {
    const self = normalName(own);
    const names = listed.flatMap((entry) => {
      // `cleanName` without its cut: an over-long name is dropped, not shortened into another.
      const clean = typeof entry === "string" ? entry.trim().replace(/\s+/g, " ") : "";
      const name = clean !== "" && Array.from(clean).length <= MAX_NAME ? normalName(clean) : null;
      return name === null || name === self ? [] : [name];
    });
    if (names.length > 0) keys.formerNames = sortedNames(names);
  }
  return keys;
}
