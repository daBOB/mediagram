/**
 * A profile's role between the `profiles` row and the wire: the admin
 * claim, a kid's limit and parent, a grown-up's PIN.
 *
 * Kept out of `store.ts` for the reason `lists-exchange.ts` is. Corrective,
 * like everything `importMerged` calls: newer local news is kept, and an
 * equal time with a different value takes the winner the merge already
 * chose by device id. SQLite failures propagate, so `importMerged` rolls the
 * whole import back.
 */

import type { Database } from "bun:sqlite";
import type { MergedProfile } from "./merged";
import { profileRows, type ProfileRow } from "./profiles";
import type { RoleKeys } from "./roles-record";
import { normalName } from "./sync-record";

/** Each profile's role keys, by local id — what `exportRecord` spreads in. */
export function exportRoles(db: Database | null): Map<string, RoleKeys> {
  const rows = profileRows(db);
  const names = new Map(rows.map((row) => [row.id, row.name]));
  return new Map(rows.map((row): [string, RoleKeys] => {
    const keys: RoleKeys = {};
    if (row.adminClaimedAt !== null) keys.admin = { claimedAt: row.adminClaimedAt };
    if (row.kids !== 0) {
      // `kids` is still written beside the limit: an older build that knows
      // nothing of limits then goes on treating this viewer as a kid.
      keys.kids = true;
      keys.kidsAge = { age: row.kidsAge === 6 ? 6 : 12, updatedAt: row.kidsAgeUpdatedAt };
    }
    // By name, because a name is what sync knows a viewer by; a parent
    // removed here is simply not said.
    const parent = row.parentId === null ? undefined : names.get(row.parentId);
    if (parent !== undefined) keys.parent = parent;
    if (row.pinHash !== null && row.pinSalt !== null) {
      keys.pin = { hash: row.pinHash, salt: row.pinSalt, updatedAt: row.pinUpdatedAt };
    }
    return [row.id, keys];
  }));
}

/**
 * Takes in the merged role keys. Run after every merged profile has a local
 * row, because a kid's parent may be a viewer this same import made.
 */
export function importRoles(db: Database, merged: MergedProfile[]): number {
  // The first profile of each name, the one `findOrCreateProfile` matches.
  const local = new Map<string, ProfileRow>();
  for (const row of profileRows(db)) {
    const name = normalName(row.name);
    if (name !== null && !local.has(name)) local.set(name, row);
  }

  let changed = 0;
  for (const profile of merged) {
    const row = local.get(profile.name);
    if (row === undefined) continue;
    const limit = profile.kidsAge;
    if (limit && newer(limit.updatedAt, row.kidsAgeUpdatedAt, limit.age !== row.kidsAge)) {
      db.query("UPDATE profiles SET kids_age = ?2, kids_age_updated_at = ?3 WHERE id = ?1")
        .run(row.id, limit.age, limit.updatedAt);
      changed += 1;
    }
    const pin = profile.pin;
    if (pin && newer(pin.updatedAt, row.pinUpdatedAt, pin.hash !== row.pinHash || pin.salt !== row.pinSalt)) {
      db.query("UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = ?4 WHERE id = ?1")
        .run(row.id, pin.hash, pin.salt, pin.updatedAt);
      changed += 1;
    }
    // Set once and never moved: two documents disagreeing about a kid's
    // parent is a bug to notice, not a change to follow.
    const parent = profile.parent === undefined ? undefined : local.get(profile.parent);
    if (row.parentId === null && parent !== undefined && parent.id !== row.id) {
      db.query("UPDATE profiles SET parent_id = ?2 WHERE id = ?1").run(row.id, parent.id);
      changed += 1;
    }
  }
  return changed + importAdmin(db, merged, local);
}

/** Merged news beats local when it is newer, or as new and different. */
function newer(merged: number, local: number, different: boolean): boolean {
  return merged > local || (merged === local && different);
}

/**
 * The merge's admin becomes the only one here. A merge that names nobody
 * says nothing — it does not say "no admin" — so it changes nothing.
 */
function importAdmin(db: Database, merged: MergedProfile[], local: Map<string, ProfileRow>): number {
  const named = merged.find((profile) => profile.admin !== undefined);
  const row = named === undefined ? undefined : local.get(named.name);
  if (named?.admin === undefined || row === undefined) return 0;
  return db.query("UPDATE profiles SET admin_claimed_at = NULL WHERE id <> ?1 AND admin_claimed_at IS NOT NULL")
    .run(row.id).changes +
    db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1 AND admin_claimed_at IS NOT ?2")
      .run(row.id, named.admin.claimedAt).changes;
}
