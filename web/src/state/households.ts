/**
 * The households one install is shared by: the `households` rows, read.
 *
 * Who is in which and who runs each is `householdView`'s to say
 * (`public/lib/household-rules.js`); `profiles.ts` reads these rows to ask it,
 * so this file never imports that one.
 */

import type { Database } from "bun:sqlite";
import { HOME } from "../../public/lib/household-rules.js";
import { normalName } from "./sync-record";

/** Everything stored about a household, a removed one too. */
export interface HouseholdRow {
  id: string;
  name: string;
  nameUpdatedAt: number;
  adminId: string | null;
  adminUpdatedAt: number;
  removed: number; // 1: deleted, for good
}

/** A live household as the page sees it: `adminId` is who runs it, resolved, never just the record's word. */
export interface Household {
  id: string;
  name: string;
  adminId: string | null;
}

/** Every household's row, removed ones included, by id. */
export function householdRows(db: Database | null): HouseholdRow[] {
  if (!db) return [];
  return db
    .query(
      `SELECT id, name, name_updated_at AS nameUpdatedAt, admin_id AS adminId,
              admin_updated_at AS adminUpdatedAt, removed
         FROM households ORDER BY id`,
    )
    .all() as HouseholdRow[];
}

/**
 * The live households, each with the profile that runs it, over `profiles`
 * as `listProfiles` placed them. By normalised name in code-point order — the
 * UTF-8 bytes', which is the core's `String` order too — not `localeCompare`,
 * whose collator the core does not share. Equal names stay in id order.
 */
export function listHouseholds(
  db: Database | null,
  profiles: readonly { id: string; household: string; admin: boolean }[],
): Household[] {
  const key = (name: string) => Buffer.from(normalName(name) ?? "");
  return householdRows(db)
    .filter((row) => !row.removed || row.id === HOME)
    .sort((a, b) => Buffer.compare(key(a.name), key(b.name)))
    .map(({ id, name }) => ({ id, name, adminId: profiles.find((one) => one.admin && one.household === id)?.id ?? null }));
}
