/**
 * A device joining a household: one PIN, the household admin's or the owner's.
 *
 * Out of `households-manage.ts` to keep that file's size down. Nothing is
 * stored here: the device keeps the household it joined (the browser, or the
 * core's `state_meta`), so a join is no write and starts no sync round.
 */

import type { Database } from "bun:sqlite";
import { listHouseholds } from "./households";
import type { Outcome } from "./profiles-manage";
import { listProfiles, profileRows } from "./profiles";
import { pinMatches, validPin } from "./profiles-pin";
import type { PinWait } from "./profiles-wait";

/**
 * Whether `pin` opens household `id`: its admin's PIN or the owner's. Wrong
 * guesses wait per household, never charged to a profile, so a guesser at
 * the door cannot lock anybody out of their own profile.
 */
export function joinHousehold(db: Database | null, wait: PinWait, id: string, pin: unknown): Outcome {
  const profiles = listProfiles(db);
  const household = listHouseholds(db, profiles).find((one) => one.id === id);
  if (household === undefined) return { reason: "not-found" };
  // The owner `householdView` reads: the first of any claim holders a merge has not settled yet.
  const ids = [household.adminId, profiles.find((one) => one.owner)?.id];
  const doors = profileRows(db).filter((row) => ids.includes(row.id) && row.pinHash !== null && row.pinSalt !== null);
  if (doors.length === 0) return { reason: "no-pin" };
  const key = `household:${id}`;
  const left = wait.secondsLeft(key);
  if (left > 0) return { reason: "wait", retryAfter: left };
  if (!validPin(pin) || !doors.some((row) => pinMatches(row.pinHash!, row.pinSalt!, pin))) {
    wait.failed(key);
    return { reason: "wrong-pin" };
  }
  wait.succeeded(key);
  return null;
}
