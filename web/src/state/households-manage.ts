/**
 * Claiming the admin's role, per household: the install's first grown-up to
 * claim becomes the owner and runs the household they are in; after that, a
 * grown-up of a household nobody runs may take it, once, with their PIN.
 *
 * A subclass of `ProfileAccess` because `profiles-manage.ts` is at its line
 * limit. Refusals keep the order every management action has: a first PIN
 * that is missing, somebody not there, what no PIN could make allowed, then
 * the PIN.
 */

import type { Database } from "bun:sqlite";
import { householdAllowed } from "../../public/lib/household-rules.js";
import { listHouseholds } from "./households";
import { ProfileAccess } from "./profiles-access";
import type { Outcome } from "./profiles-manage";
import { listProfiles, profileRows, toProfile } from "./profiles";
import { validPin } from "./profiles-pin";
import { writePin } from "./profiles-write";

/** Same ceiling as the stamps in `profiles-write.ts`: a peer's clock cannot make this write lose. */
const LAST_STAMP = Number.MAX_SAFE_INTEGER - 1;

function writeHouseholdAdmin(db: Database, householdId: string, profileId: string): void {
  db.query(`UPDATE households SET admin_id = ?2,
              admin_updated_at = MAX(?3, MIN(admin_updated_at, ?4) + 1) WHERE id = ?1`)
    .run(householdId, profileId, Date.now(), LAST_STAMP);
}

export class HouseholdManager extends ProfileAccess {
  /**
   * Makes `id` the admin: the owner while this install has none, else its
   * household's admin while that has none. A grown-up with a PIN proves it;
   * one without takes the PIN given, which is why that one has to be a PIN.
   */
  claimAdmin(id: string, pin: unknown): Outcome {
    const rows = profileRows(this.db);
    const target = rows.find((row) => row.id === id);
    if (target !== undefined && target.kids === 0 && target.pinHash === null && !validPin(pin)) {
      return { reason: "invalid" };
    }
    if (target === undefined) return { reason: "not-found" };
    // A claim a kid's row still holds is no owner: see `toProfile`.
    const firstClaim = !rows.some((row) => toProfile(row).owner);
    const profiles = listProfiles(this.db);
    const mayClaim = target.kids === 0 && (firstClaim ||
      householdAllowed(profiles, listHouseholds(this.db, profiles).map((one) => one.id), id, "claim-household",
        profiles.find((one) => one.id === id)!.household, ""));
    if (!mayClaim) return { reason: "not-allowed" };
    if (target.pinHash !== null) {
      const refused = this.prove(target, pin);
      if (refused) return refused;
    }
    const db = this.db!;
    db.transaction(() => {
      if (target.pinHash === null && validPin(pin)) writePin(db, id, pin, false);
      if (firstClaim) db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1").run(id, Date.now());
      else writeHouseholdAdmin(db, profiles.find((one) => one.id === id)!.household, id);
    })();
    return null;
  }
}
