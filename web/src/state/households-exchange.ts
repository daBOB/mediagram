/**
 * The households between the `households` table and the wire.
 *
 * Kept out of `store.ts` for the reason `roles-exchange.ts` is, and
 * corrective like it: a local field the merge does not beat is kept, by the
 * same `newer` rule. SQLite failures propagate, so `importMerged` rolls the
 * whole import back.
 */

import type { Database } from "bun:sqlite";
import { HOME } from "../../public/lib/household-rules.js";
import type { HouseholdEntry } from "./households-record";
import { householdRows } from "./households";
import { profileRows } from "./profiles";
import { firstOfEachName, newer } from "./roles-exchange";

/**
 * What this device says about households: a row only once something in it
 * is dated, or it is removed — so a Home nobody touched says nothing, and an
 * untouched install writes no key an older build has not seen. An admin
 * whose profile is not here is not said: there is no name to say.
 */
export function exportHouseholds(db: Database | null): HouseholdEntry[] {
  const names = new Map(profileRows(db).map((row) => [row.id, row.name]));
  return householdRows(db).flatMap((row) => {
    const entry: HouseholdEntry = { id: row.id };
    if (row.nameUpdatedAt > 0) entry.name = { text: row.name, updatedAt: row.nameUpdatedAt };
    const admin = row.adminId === null ? undefined : names.get(row.adminId);
    if (row.adminUpdatedAt > 0 && admin !== undefined) entry.admin = { name: admin, updatedAt: row.adminUpdatedAt };
    if (row.removed && row.id !== HOME) entry.removed = true;
    return entry.name || entry.admin || entry.removed ? [entry] : [];
  });
}

/**
 * Takes in the merged households. Run after `importRoles`, once every merged
 * profile has a local row: an admin may be a viewer this same import made.
 */
export function importHouseholds(db: Database, entries: HouseholdEntry[]): number {
  const local = firstOfEachName(db);
  const stored = new Map(householdRows(db).map((row) => [row.id, row]));
  let changed = 0;
  for (const entry of entries) {
    let row = stored.get(entry.id);
    if (row === undefined) {
      // Made only from a name: a household is never listed without one.
      if (entry.name === undefined) continue;
      db.query("INSERT INTO households(id, name, name_updated_at) VALUES (?1, ?2, ?3)")
        .run(entry.id, entry.name.text, entry.name.updatedAt);
      row = { id: entry.id, name: entry.name.text, nameUpdatedAt: entry.name.updatedAt, adminId: null, adminUpdatedAt: 0, removed: 0 };
      changed += 1;
    } else if (entry.name && newer(entry.name.updatedAt, row.nameUpdatedAt, entry.name.text !== row.name)) {
      db.query("UPDATE households SET name = ?2, name_updated_at = ?3 WHERE id = ?1")
        .run(row.id, entry.name.text, entry.name.updatedAt);
      changed += 1;
    }
    // Onto the first local profile of that name; one not here yet waits for the round that brings it.
    const admin = entry.admin === undefined ? undefined : local.get(entry.admin.name);
    if (entry.admin && admin && newer(entry.admin.updatedAt, row.adminUpdatedAt, admin.id !== row.adminId)) {
      db.query("UPDATE households SET admin_id = ?2, admin_updated_at = ?3 WHERE id = ?1")
        .run(row.id, admin.id, entry.admin.updatedAt);
      changed += 1;
    }
    // Set, never cleared: deletion is final.
    if (entry.removed && !row.removed && row.id !== HOME) {
      db.query("UPDATE households SET removed = 1 WHERE id = ?1").run(row.id);
      changed += 1;
    }
  }
  return changed;
}
