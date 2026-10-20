/**
 * Making profiles: by hand on the profiles page, and by sync for a viewer
 * another device knows. Out of `profiles.ts`, which reads them, so each stays
 * short; what is made is answered as `listProfiles` lists it.
 */

import type { Database } from "bun:sqlite";
import { HOME } from "../../public/lib/household-rules.js";
import { cleanName, listProfiles, type Profile, type ProfileRow } from "./profiles";
import { normalName } from "./sync-record";

/** What a new profile is, beside its name. */
export interface NewProfile {
  kids?: boolean;
  /** A kid's limit, chosen now. Absent is FSK 12, dated 0. */
  kidsAge?: 6 | 12;
  /** A kid's daily limit in minutes, chosen now. Absent is none, dated 0. */
  dailyLimit?: number;
  /** The grown-up adding this kid. */
  parentId?: string;
  /** A grown-up's first PIN, already salted and hashed. */
  pin?: { hash: string; salt: string };
  /** The first grown-up on a player, which holds the owner's claim from the start. */
  admin?: boolean;
  /** The household it is made in, chosen now. Absent is Home, dated 0. */
  household?: string;
}

/**
 * Makes a profile. A kid nobody chose a limit for starts at FSK 12 dated 0 —
 * older than any limit a parent chooses, so the first real choice, made here
 * or synced in, wins; a chosen limit, a chosen household, a first PIN and a
 * first admin's claim are dated now. A kid made by sync takes its merged limit
 * right after.
 */
export function insertProfile(db: Database | null, name: unknown, role: NewProfile = {}): Profile | null {
  if (!db) return null;
  const clean = cleanName(name);
  if (clean === null) return null;
  const kids = role.kids === true;
  const createdAt = Date.now();
  const row: ProfileRow = {
    id: crypto.randomUUID(),
    name: clean,
    createdAt,
    kids: kids ? 1 : 0,
    kidsAge: kids ? role.kidsAge ?? 12 : null,
    kidsAgeUpdatedAt: kids && role.kidsAge !== undefined ? createdAt : 0,
    dailyLimit: kids ? role.dailyLimit ?? null : null,
    dailyLimitUpdatedAt: kids && role.dailyLimit !== undefined ? createdAt : 0,
    parentId: kids ? role.parentId ?? null : null,
    adminClaimedAt: !kids && role.admin === true ? createdAt : null,
    pinHash: kids ? null : role.pin?.hash ?? null,
    pinSalt: kids ? null : role.pin?.salt ?? null,
    pinUpdatedAt: !kids && role.pin !== undefined ? createdAt : 0,
    pinProven: 0,
    hiddenSections: "[]", // the column's own default: hides nothing, undated
    hiddenSectionsUpdatedAt: 0,
    householdId: role.household ?? HOME,
    householdUpdatedAt: role.household !== undefined ? createdAt : 0,
  };
  db.query(
    `INSERT INTO profiles(id, name, created_at, kids, kids_age, kids_age_updated_at, parent_id,
                          admin_claimed_at, pin_hash, pin_salt, pin_updated_at,
                          daily_limit, daily_limit_updated_at, household_id, household_updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)`,
  ).run(row.id, row.name, row.createdAt, row.kids, row.kidsAge, row.kidsAgeUpdatedAt, row.parentId,
    row.adminClaimedAt, row.pinHash, row.pinSalt, row.pinUpdatedAt, row.dailyLimit, row.dailyLimitUpdatedAt,
    row.householdId, row.householdUpdatedAt);
  // Whether it runs its household depends on everyone else there.
  return listProfiles(db).find((one) => one.id === row.id) ?? null;
}

/**
 * This player's id for a viewer, made if it has never seen them.
 *
 * Made rather than skipped, because the first thing a second machine knows
 * about a viewer is a document written by the first — refusing to create
 * one would mean the sync could only ever flow towards a machine that had
 * already met them.
 */
export function findOrCreateProfile(
  db: Database | null,
  name: string,
  displayName?: string,
  kids = false,
): { id: string; created: boolean; kids: boolean } | null {
  const wanted = normalName(name);
  if (wanted === null) return null;
  const found = listProfiles(db).find((profile) => normalName(profile.name) === wanted);
  // Created from the spelling somebody typed, never from the normalised
  // identity — that would greet a viewer as "andré" on every new machine.
  if (found) return { id: found.id, created: false, kids: found.kids };
  const created = insertProfile(db, displayName ?? name, { kids });
  return created ? { id: created.id, created: true, kids } : null;
}
