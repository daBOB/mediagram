/**
 * Who watches this library: the `profiles` rows, read (made in `profiles-insert.ts`).
 *
 * Kept out of `store.ts`, which carries every other read and write, and
 * because a profile is more than a name: a kid carries its own limit and the
 * grown-up who made it, one grown-up owns the install, each household has a
 * grown-up who runs it, and a grown-up may hold a PIN. The page, the sync
 * import and the sync export all read the same row, so it is read in one
 * place and no two readers can disagree about a column.
 *
 * Every function takes the raw `Database | null` that `WatchState` holds; a
 * null database reads as nobody.
 */

import type { Database } from "bun:sqlite";
import { householdView } from "../../public/lib/household-rules.js";
import { householdRows } from "./households";

/** A profile as the page sees it. Says whether there is a PIN, never what. */
export interface Profile {
  id: string;
  name: string;
  createdAt: number;
  /** Sees only what its own limit allows, and manages nothing. */
  kids: boolean;
  /** 6 or 12 on a kid; `null` on a grown-up. */
  kidsAge: 6 | 12 | null;
  /** A kid's daily watching limit in minutes; `null` is none, and on a grown-up. */
  dailyLimit: number | null;
  /** The grown-up who made this kid. `null`, or one not here, is the admin's. */
  parentId: string | null;
  /** Holds the install-wide claim: sets households up. Only ever a grown-up. */
  owner: boolean;
  /** The household it is in, resolved: a kid's parent's, and Home for one gone or unknown. */
  household: string;
  /** Runs its household: the owner there, else the grown-up its record names. */
  admin: boolean;
  hasPin: boolean;
  /** Section keys this profile does not see, sorted; any profile's, a grown-up's too. */
  hiddenSections: string[];
}

/** Everything stored about a profile, the PIN included. Never sent as is. */
export interface ProfileRow {
  id: string;
  name: string;
  createdAt: number;
  kids: number;
  kidsAge: number | null;
  kidsAgeUpdatedAt: number;
  dailyLimit: number | null;
  dailyLimitUpdatedAt: number;
  parentId: string | null;
  adminClaimedAt: number | null;
  pinHash: string | null;
  pinSalt: string | null;
  pinUpdatedAt: number;
  pinProven: number; // 1: set by someone who knew the PIN before it, or the admin's reset
  hiddenSections: string; // a JSON list of section keys, sorted, each once
  hiddenSectionsUpdatedAt: number;
  householdId: string; // as stored; `listProfiles` puts a kid with its parent, and a gone household's profile in Home
  householdUpdatedAt: number;
}

/** How long a name may be. Long enough for a sentence, short enough to show. */
const MAX_NAME = 120;

/** A name with its edges trimmed, or `null` when there is nothing left. */
export function cleanName(name: unknown): string | null {
  if (typeof name !== "string") return null;
  const clean = name.trim().replace(/\s+/g, " ").slice(0, MAX_NAME);
  return clean === "" ? null : clean;
}

/** Every profile's row, oldest first. */
export function profileRows(db: Database | null): ProfileRow[] {
  if (!db) return [];
  return db
    .query(
      `SELECT id, name, created_at AS createdAt, kids, kids_age AS kidsAge,
              kids_age_updated_at AS kidsAgeUpdatedAt,
              daily_limit AS dailyLimit, daily_limit_updated_at AS dailyLimitUpdatedAt, parent_id AS parentId,
              admin_claimed_at AS adminClaimedAt, pin_hash AS pinHash, pin_salt AS pinSalt,
              pin_updated_at AS pinUpdatedAt, pin_proven AS pinProven,
              hidden_sections AS hiddenSections, hidden_sections_updated_at AS hiddenSectionsUpdatedAt,
              household_id AS householdId, household_updated_at AS householdUpdatedAt
         FROM profiles ORDER BY created_at`,
    )
    .all() as ProfileRow[];
}

/** One row on its own: the stored household, and nobody running anything — only `listProfiles` sees enough to say. */
export function toProfile(row: ProfileRow): Profile {
  const kids = row.kids !== 0;
  return {
    id: row.id,
    name: row.name,
    createdAt: row.createdAt,
    kids,
    // A kid always has a limit, whatever the column holds: 12, as before choices.
    kidsAge: kids ? (row.kidsAge === 6 ? 6 : 12) : null,
    dailyLimit: kids ? row.dailyLimit : null,
    parentId: row.parentId,
    // A kid manages nothing and opens freely. A grown-up another device
    // later calls a kid keeps its old claim and PIN in their columns; they
    // must not make it the owner, or a profile with a PIN, here.
    owner: !kids && row.adminClaimedAt !== null,
    household: row.householdId,
    admin: false,
    hasPin: !kids && row.pinHash !== null,
    hiddenSections: JSON.parse(row.hiddenSections) as string[],
  };
}

/** Who watches this library, oldest first, each in its household and saying whether it runs it. Empty until someone says. */
export function listProfiles(db: Database | null): Profile[] {
  const profiles = profileRows(db).map(toProfile);
  const households = householdRows(db).map((row) => ({ ...row, removed: row.removed !== 0 }));
  const view = householdView(profiles, households).profiles;
  return profiles.map((one) => ({ ...one, ...view[one.id]! }));
}

/** Its own rows cascade; a grown-up's kids stay (`parent_id` is no foreign key) — `ProfileManager.remove` takes them. */
export function deleteProfileById(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("DELETE FROM profiles WHERE id = ?1").run(id).changes > 0;
}

export function profileExists(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("SELECT 1 FROM profiles WHERE id = ?1").get(id) !== null;
}
