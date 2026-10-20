/**
 * Households, managed: the owner's actions on them, a device joining one, and
 * claiming the admin's role per household. The writes are `households-write.ts`'s,
 * and joining is `households-join.ts`'s.
 *
 * Every owner action answers in one order, as a profile's does: input that
 * cannot be used; a name already taken; somebody, or a household, not there;
 * a kid acting; the actor's PIN; the household rule; and last an admin-to-be
 * with no PIN and none given, since every household's admin has one.
 *
 * A subclass of `ProfileAccess` because `profiles-manage.ts` is at its line limit.
 */

import type { Database } from "bun:sqlite";
import { householdAllowed } from "../../public/lib/household-rules.js";
import { listHouseholds, type Household } from "./households";
import { joinHousehold } from "./households-join";
import { insertHousehold, moveGrownUp, removeHousehold, writeHouseholdAdmin, writeHouseholdName } from "./households-write";
import { ProfileAccess } from "./profiles-access";
import type { Outcome, Refusal, Refused } from "./profiles-manage";
import { cleanName, listProfiles, profileRows, toProfile, type ProfileRow } from "./profiles";
import { insertProfile } from "./profiles-insert";
import { validPin } from "./profiles-pin";
import { nameTaken } from "./profiles-rules";
import { writePin } from "./profiles-write";

/** A new household's admin: a grown-up already here, or a new one made with it. */
export type NewAdmin = { id: string; newPin?: unknown } | { name: unknown; newPin?: unknown };

type OwnerAction = "create-household" | "rename-household" | "set-household-admin" | "move-to-household" | "delete-household";

/** What an owner action the rule allowed goes on with. */
interface Allowed {
  rows: ProfileRow[];
  households: Household[];
}

const refuse = (reason: Refusal): Refused => ({ reason });
/** A new PIN that came and is not one. None at all is `no-pin`'s to say, last. */
const unusablePin = (next: unknown): boolean => next != null && !validPin(next);
/** An admin-to-be (`undefined`: one not made yet) with no PIN, and none to give it. */
const pinless = (row: ProfileRow | undefined, next: unknown): boolean => (row?.pinHash ?? null) === null && !validPin(next);

/** A first PIN, where the admin-to-be has none; unproven, so one set elsewhere still wins (`roles-merge.ts`). */
function givePin(db: Database, id: string, row: ProfileRow | undefined, next: unknown): void {
  if ((row?.pinHash ?? null) === null && validPin(next)) writePin(db, id, next, false);
}

export class HouseholdManager extends ProfileAccess {
  /**
   * Makes `id` the admin: the owner while this install has none, else its
   * household's admin while that has none. A grown-up with a PIN proves it,
   * last; one without takes the PIN given, which is why that has to be a PIN.
   */
  claimAdmin(id: string, pin: unknown): Outcome {
    const rows = profileRows(this.db);
    const target = rows.find((row) => row.id === id);
    if (target !== undefined && target.kids === 0 && target.pinHash === null && !validPin(pin)) return refuse("invalid");
    if (target === undefined) return refuse("not-found");
    // A claim a kid's row still holds is no owner: see `toProfile`.
    const firstClaim = !rows.some((row) => toProfile(row).owner);
    const profiles = listProfiles(this.db);
    const household = profiles.find((one) => one.id === id)!.household;
    const mayClaim = target.kids === 0 && (firstClaim ||
      householdAllowed(profiles, listHouseholds(this.db, profiles).map((one) => one.id), id, "claim-household", household, ""));
    if (!mayClaim) return refuse("not-allowed");
    if (target.pinHash !== null) {
      const refused = this.prove(target, pin);
      if (refused) return refused;
    }
    const db = this.db!;
    db.transaction(() => {
      givePin(db, id, target, pin);
      if (firstClaim) db.query("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1").run(id, Date.now());
      else writeHouseholdAdmin(db, household, id);
    })();
    return null;
  }

  /** A household and its admin, who — an existing grown-up — moves in with their kids. */
  createHousehold(actorId: string, pin: unknown, name: unknown, admin: NewAdmin): Refused | Household {
    const clean = cleanName(name);
    const adminId = "id" in admin ? admin.id : null;
    const adminName = "name" in admin ? admin.name : undefined;
    if (clean === null || (adminId === null && cleanName(adminName) === null) || unusablePin(admin.newPin)) {
      return refuse("invalid");
    }
    if (this.householdNameTaken(name, null) ||
      (adminId === null && nameTaken(profileRows(this.db).map((row) => row.name), adminName))) return refuse("name-taken");
    const allowed = this.owned(actorId, pin, "create-household", "", adminId);
    if ("reason" in allowed) return allowed;
    const target = allowed.rows.find((row) => row.id === adminId);
    if (pinless(target, admin.newPin)) return refuse("no-pin");
    const db = this.db!;
    const id = crypto.randomUUID();
    db.transaction(() => {
      const runs = adminId ?? insertProfile(db, adminName, { household: id })!.id;
      if (adminId !== null) moveGrownUp(db, adminId, id);
      givePin(db, runs, target, admin.newPin);
      insertHousehold(db, id, clean, runs);
    })();
    return listHouseholds(db, listProfiles(db)).find((one) => one.id === id)!;
  }

  renameHousehold(actorId: string, pin: unknown, id: string, name: unknown): Outcome {
    const clean = cleanName(name);
    if (clean === null) return refuse("invalid");
    if (this.householdNameTaken(name, id)) return refuse("name-taken");
    const allowed = this.owned(actorId, pin, "rename-household", id, null);
    if ("reason" in allowed) return allowed;
    writeHouseholdName(this.db!, id, clean);
    return null;
  }

  /** Never where the owner is: the owner runs the household they are in (the rule says so). */
  setHouseholdAdmin(actorId: string, pin: unknown, id: string, adminId: string, next?: unknown): Outcome {
    if (unusablePin(next)) return refuse("invalid");
    const allowed = this.owned(actorId, pin, "set-household-admin", id, adminId);
    if ("reason" in allowed) return allowed;
    const target = allowed.rows.find((row) => row.id === adminId);
    if (pinless(target, next)) return refuse("no-pin");
    const db = this.db!;
    db.transaction(() => {
      writeHouseholdAdmin(db, id, adminId);
      givePin(db, adminId, target, next);
    })();
    return null;
  }

  /**
   * A grown-up and their kids. Into a household nobody runs, the grown-up
   * runs it, so needs a PIN: theirs, or `next` as a first one. Otherwise
   * `next` is not asked for, and changes nothing.
   */
  moveToHousehold(actorId: string, pin: unknown, id: string, profileId: string, next?: unknown): Outcome {
    if (unusablePin(next)) return refuse("invalid");
    const allowed = this.owned(actorId, pin, "move-to-household", id, profileId);
    if ("reason" in allowed) return allowed;
    const runs = allowed.households.find((one) => one.id === id)?.adminId === null;
    const row = allowed.rows.find((one) => one.id === profileId);
    if (runs && pinless(row, next)) return refuse("no-pin");
    const db = this.db!;
    db.transaction(() => {
      moveGrownUp(db, profileId, id);
      if (runs) {
        writeHouseholdAdmin(db, id, profileId);
        givePin(db, profileId, row, next);
      }
    })();
    return null;
  }

  /** An empty household other than Home, for good. */
  deleteHousehold(actorId: string, pin: unknown, id: string): Outcome {
    const allowed = this.owned(actorId, pin, "delete-household", id, null);
    if ("reason" in allowed) return allowed;
    removeHousehold(this.db!, id);
    return null;
  }

  /** Whether `pin` opens household `id` on a device: see `households-join.ts`. */
  join(id: string, pin: unknown): Outcome {
    return joinHousehold(this.db, this.wait, id, pin);
  }

  /** Whether a live household other than `except` already answers to `name`, compared as profile names are. */
  private householdNameTaken(name: unknown, except: string | null): boolean {
    // Names only: who runs each is not asked, so no profiles are read.
    return nameTaken(listHouseholds(this.db, []).filter((one) => one.id !== except).map((one) => one.name), name);
  }

  /**
   * Somebody there and the household too, a grown-up acting, its PIN, then
   * the household rule. `targetId` null is an action on no profile.
   */
  private owned(actorId: string, pin: unknown, action: OwnerAction, householdId: string, targetId: string | null): Refused | Allowed {
    const rows = profileRows(this.db);
    const profiles = listProfiles(this.db);
    const households = listHouseholds(this.db, profiles);
    const live = households.map((one) => one.id);
    const actor = rows.find((row) => row.id === actorId);
    if (actor === undefined || (targetId !== null && !rows.some((row) => row.id === targetId)) ||
      (action !== "create-household" && !live.includes(householdId))) return refuse("not-found");
    // A kid manages nothing, and has no PIN to prove otherwise with.
    if (actor.kids !== 0) return refuse("not-allowed");
    const refused = this.prove(actor, pin);
    if (refused) return refused;
    return householdAllowed(profiles, live, actorId, action, householdId, targetId ?? "") ? { rows, households } : refuse("not-allowed");
  }
}
