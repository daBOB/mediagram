/**
 * The households, reconciled across devices — field by field, not row by row.
 *
 * **A name and an admin: each the newest, ties by device id** (`keep`), on
 * its own, so a rename on one device and a new admin on another both stand.
 * The admin is merged under the name sync knows a viewer by (`normalName`).
 *
 * **A removal is sticky.** Once any device says a household is gone, no
 * document lacking the flag — however new — brings it back. Its other
 * fields are kept beside it, so the merge stays total.
 */

import type { HouseholdEntry } from "./households-record";
import { normalName, type SyncRecord } from "./sync-record";
import { keep, type Held } from "./tie-break";

type Name = NonNullable<HouseholdEntry["name"]>;
type Admin = NonNullable<HouseholdEntry["admin"]>;

/** Every household any device names, sorted by id. */
export function mergeHouseholds(records: SyncRecord[]): HouseholdEntry[] {
  const ids = new Set<string>();
  const names = new Map<string, Held<Name>>();
  const admins = new Map<string, Held<Admin>>();
  const removed = new Set<string>();

  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const entry of record?.households ?? []) {
      ids.add(entry.id);
      if (entry.name) keep(names, entry.id, entry.name, device);
      const admin = normalName(entry.admin?.name);
      if (entry.admin && admin !== null) keep(admins, entry.id, { name: admin, updatedAt: entry.admin.updatedAt }, device);
      if (entry.removed === true) removed.add(entry.id);
    }
  }

  return [...ids].sort().map((id) => {
    const name = names.get(id)?.row;
    const admin = admins.get(id)?.row;
    return {
      id,
      ...(name !== undefined ? { name } : {}),
      ...(admin !== undefined ? { admin } : {}),
      ...(removed.has(id) ? { removed: true as const } : {}),
    };
  });
}
