/**
 * A profile's role on the sync record: the admin claim, a kid's limit and
 * parent, a grown-up's PIN — and `kids`, which came first.
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
  /** The grown-up who made this kid, by the name sync knows a viewer by. */
  parent?: string;
  /** A grown-up's PIN, salted and hashed. The sync record is the only place
   * the hash leaves the store. `proven`: set by someone who knew the PIN
   * before it, or the admin's reset; without it the PIN is a first one, set
   * where its grown-up had none, which never replaces one set earlier
   * elsewhere (`roles-merge.ts`). Written only when true. */
  pin?: { hash: string; salt: string; updatedAt: number; proven?: true };
}

const HASH = /^[0-9a-f]{64}$/;
const SALT = /^[0-9a-f]{32}$/;

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
  return keys;
}
