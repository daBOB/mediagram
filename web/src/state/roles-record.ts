/**
 * A profile's role on the sync record: the admin claim, a kid's limit and
 * parent, a grown-up's PIN, the sections any profile does not see — and
 * `kids`, which came first.
 *
 * New optional keys rather than a `SYNC_FORMAT` bump, for the reason
 * `sync-record.ts` gives for `unwatched`: a reader that predates them drops
 * what it does not know and goes on merging everything else. An older
 * build's kid therefore arrives as `kids: true` and nothing more, which the
 * merge reads as FSK 12 at time 0 (`roles-merge.ts`).
 *
 * Read as hostile as the rest of the document: each key is checked on its
 * own and a bad one is dropped on its own, never the profile, and an absent
 * key is "nothing said". Times go through the same coercion and the same
 * 2^53 − 1 bound every synced row's do.
 */

import { householdIdOf } from "./households-record";
import { isStamp, numberFromScalar, objectRow, text_ } from "./record-scalars";

/** The keys of a profile's entry that say who it is, not what it watched. */
export interface RoleKeys {
  /** The household's one admin, and when it was claimed — the earliest
   * claim anywhere wins. */
  admin?: { claimedAt: number };
  /**
   * Present only on a kids profile. Written only when true, so an ordinary
   * profile's entry reads exactly as it did before the flag existed.
   */
  kids?: true;
  /** A kid's own limit and when it last changed. `updatedAt` 0 is a limit
   * nobody chose — the FSK 12 every kid had before there was a choice. */
  kidsAge?: { age: 6 | 12; updatedAt: number };
  /** A kid's daily watching limit in minutes, `null` for none, and when it
   * last changed. `null` is said, not absent: it must beat an older limit. */
  dailyLimit?: { minutes: number | null; updatedAt: number };
  /** The grown-up who made this kid, by the name sync knows a viewer by. */
  parent?: string;
  /** A grown-up's PIN, salted and hashed. The sync record is the only place
   * the hash leaves the store. `proven`: set by someone who knew the PIN
   * before it, or the admin's reset; without it the PIN is a first one, set
   * where its grown-up had none, which never replaces one set earlier
   * elsewhere (`roles-merge.ts`). Written only when true. */
  pin?: { hash: string; salt: string; updatedAt: number; proven?: true };
  /** The sections of the library this profile does not see, and when the
   * list last changed — whole, so the newest list wins. Any profile's, a
   * grown-up's too; an empty list is said, not absent, so it beats an older
   * one. Keys this build does not know are kept for a build that does. */
  hiddenSections?: { sections: string[]; updatedAt: number };
  /** The household this profile is in, as its record says, and when it was
   * last moved — any profile's. Never written undated: a profile nobody
   * moved says nothing, and is in Home. */
  household?: { id: string; updatedAt: number };
}

const HASH = /^[0-9a-f]{64}$/;
const SALT = /^[0-9a-f]{32}$/;
/** A section's key: today's five, and room for any a newer build adds. */
const SECTION = /^[a-z]{1,24}$/;
/** More than any build has sections, and a bound on what a document makes a device store. */
const MAX_SECTIONS = 16;

/**
 * A list of hidden sections, sorted and each key once — or `null` for
 * anything else: not an array, more than 16 entries as written, or any entry
 * that is not a section's key. The record and the page's request read it alike.
 */
export function hiddenSectionsOf(value: unknown): string[] | null {
  if (!Array.isArray(value) || value.length > MAX_SECTIONS) return null;
  if (!value.every((key) => typeof key === "string" && SECTION.test(key))) return null;
  return [...new Set(value as string[])].sort();
}

export function parseRoleKeys(row: Record<string, unknown>): RoleKeys {
  const keys: RoleKeys = {};

  const claimedAt = numberFromScalar(objectRow(row.admin)?.claimedAt);
  if (isStamp(claimedAt)) keys.admin = { claimedAt };

  // Only a literal `true`: a flag that restricts what a child sees must not
  // be switched on by a string that merely looks truthy.
  if (row.kids === true) keys.kids = true;

  // The age is a literal too, for the same reason: a limit is not guessed at.
  const limit = objectRow(row.kidsAge);
  const age = limit?.age;
  const limitAt = numberFromScalar(limit?.updatedAt);
  if ((age === 6 || age === 12) && (limitAt === 0 || isStamp(limitAt))) keys.kidsAge = { age, updatedAt: limitAt };

  const daily = objectRow(row.dailyLimit);
  const minutes = daily?.minutes;
  const dailyAt = numberFromScalar(daily?.updatedAt);
  if ((minutes === null || (typeof minutes === "number" && Number.isInteger(minutes) && minutes >= 1 && minutes <= 1440)) &&
      (dailyAt === 0 || isStamp(dailyAt))) keys.dailyLimit = { minutes, updatedAt: dailyAt };

  const parent = text_(row.parent);
  if (parent !== null) keys.parent = parent;

  const pin = objectRow(row.pin);
  const hash = pin?.hash;
  const salt = pin?.salt;
  const pinAt = numberFromScalar(pin?.updatedAt);
  if (typeof hash === "string" && HASH.test(hash) && typeof salt === "string" && SALT.test(salt) && isStamp(pinAt)) {
    // Only the literal true: a PIN that wins over others is not guessed at.
    keys.pin = { hash, salt, updatedAt: pinAt, ...(pin?.proven === true ? { proven: true as const } : {}) };
  }

  const hidden = objectRow(row.hiddenSections);
  const sections = hiddenSectionsOf(hidden?.sections);
  const hiddenAt = numberFromScalar(hidden?.updatedAt);
  if (sections !== null && (hiddenAt === 0 || isStamp(hiddenAt))) keys.hiddenSections = { sections, updatedAt: hiddenAt };

  const household = objectRow(row.household);
  const householdId = householdIdOf(household?.id);
  const householdAt = numberFromScalar(household?.updatedAt);
  if (householdId !== null && isStamp(householdAt)) keys.household = { id: householdId, updatedAt: householdAt };
  return keys;
}
