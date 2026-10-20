/**
 * Own writes of households, and of a grown-up moving between them; the
 * decisions are `households-manage.ts`'s. Every field is dated like a PIN or
 * a limit (`profiles-write.ts`), so a household made or changed here reaches
 * the other devices and beats what it replaced there.
 */

import type { Database } from "bun:sqlite";
import { LAST_STAMP } from "./profiles-write";

/** A new household, its name and admin dated now. */
export function insertHousehold(db: Database, id: string, name: string, adminId: string): void {
  db.query(`INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at)
              VALUES (?1, ?2, ?3, ?4, ?3)`).run(id, name, Date.now(), adminId);
}

export function writeHouseholdName(db: Database, id: string, name: string): void {
  db.query(`UPDATE households SET name = ?2,
              name_updated_at = MAX(?3, MIN(name_updated_at, ?4) + 1) WHERE id = ?1`)
    .run(id, name, Date.now(), LAST_STAMP);
}

/** Only onto a household row that is there: one that lands nowhere throws, and the caller's transaction rolls back. */
export function writeHouseholdAdmin(db: Database, householdId: string, profileId: string): void {
  const written = db.query(`UPDATE households SET admin_id = ?2,
              admin_updated_at = MAX(?3, MIN(admin_updated_at, ?4) + 1) WHERE id = ?1`)
    .run(householdId, profileId, Date.now(), LAST_STAMP).changes;
  if (written === 0) throw new Error(`no household ${householdId} to give an admin`);
}

/** A grown-up and each kid it made, into `householdId`, every one dated. */
export function moveGrownUp(db: Database, profileId: string, householdId: string): void {
  db.query(`UPDATE profiles SET household_id = ?2,
              household_updated_at = MAX(?3, MIN(household_updated_at, ?4) + 1)
            WHERE id = ?1 OR (parent_id = ?1 AND kids = 1)`)
    .run(profileId, householdId, Date.now(), LAST_STAMP);
}

/** Final: removal is never cleared, here or by sync. */
export function removeHousehold(db: Database, id: string): void {
  db.query("UPDATE households SET removed = 1 WHERE id = ?1").run(id);
}
