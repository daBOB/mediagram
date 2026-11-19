/**
 * Adding a grown-up or a kid. The owner may add into any household, or for
 * any grown-up (`create-grown-up` names a household, `create-kid` the parent;
 * "" or none is the maker's own, see `profiles-rules.ts`).
 *
 * A subclass only because `profiles-manage.ts` is at its line limit. A
 * household or parent that is named but not there, or not even a string, is
 * `not-found`, before the PIN is compared. A grown-up added to a household
 * nobody runs becomes its admin in the same transaction as the insert.
 */

import { listProfiles, type Profile } from "./profiles";
import { insertProfile } from "./profiles-insert";
import { listHouseholds } from "./households";
import { writeHouseholdAdmin } from "./households-write";
import { HOME } from "../../public/lib/household-rules.js";
import { ProfileManager, validAge, validMinutes, type Refused } from "./profiles-manage";
import { newPin, validPin } from "./profiles-pin";

const refuse = (reason: Refused["reason"]): Refused => ({ reason });

export class ProfileCreate extends ProfileManager {
  /** A grown-up, with its first PIN, in `household` or else its maker's. The admin's to add; the owner's to add anywhere. */
  createGrownUp(actorId: string, pin: unknown, name: unknown, next: unknown, household: unknown = null): Refused | Profile {
    if (!validPin(next)) return refuse("invalid");
    const named = household === null || household === "" ? null : household;
    if (named !== null && typeof named !== "string") return refuse("not-found");
    const live = listHouseholds(this.db, listProfiles(this.db)).find((one) => one.id === named);
    const refused = this.unusable(name)
      ?? (named !== null && live === undefined ? refuse("not-found") : null)
      ?? this.check(actorId, pin, "create-grown-up", named);
    if (refused) return refused;
    const db = this.db!;
    const placed = named === null ? this.householdOf(actorId) : (named === HOME ? {} : { household: named });
    let made: Profile | null = null;
    db.transaction(() => {
      made = insertProfile(db, name, { pin: newPin(next), ...placed });
      if (made && live?.adminId === null) writeHouseholdAdmin(db, live.id, made.id);
    })();
    if (!made) return refuse("invalid");
    return listProfiles(db).find((one) => one.id === (made as Profile).id) ?? refuse("invalid");
  }

  /** A kid, belonging to `parentId` or else whichever grown-up adds it, with its own limit; it lives in its parent's household. */
  createKid(actorId: string, pin: unknown, name: unknown, kidsAge: unknown, dailyLimit: unknown = null, parentId: unknown = null): Refused | Profile {
    if (!validAge(kidsAge) || !validMinutes(dailyLimit)) return refuse("invalid");
    const named = parentId === null || parentId === "" ? null : parentId;
    if (named !== null && typeof named !== "string") return refuse("not-found");
    const refused = this.unusable(name) ?? this.check(actorId, pin, "create-kid", named);
    if (refused) return refused;
    const parent = named ?? actorId;
    return insertProfile(this.db, name, { kids: true, kidsAge, parentId: parent, ...this.householdOf(parent), ...(dailyLimit === null ? {} : { dailyLimit }) }) ?? refuse("invalid");
  }
}
