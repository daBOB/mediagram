/**
 * Who watches this library: the `profiles` rows, read and made.
 *
 * Kept out of `store.ts`, which carries every other read and write, and
 * because a profile is more than a name: a kid carries its own limit and the
 * grown-up who made it, one grown-up may be the household's admin, and a
 * grown-up may hold a PIN. The page, the sync import and the sync export all
 * read the same row, so it is read in one place and no two readers can
 * disagree about a column.
 *
 * Every function takes the raw `Database | null` that `WatchState` holds; a
 * null database reads as nobody and makes nobody.
 */

import type { Database } from "bun:sqlite";
import { normalName } from "./sync-record";

/** A profile as the page sees it. Says whether there is a PIN, never what. */
export interface Profile {
  id: string;
  name: string;
  createdAt: number;
  /** Sees only what its own limit allows, and manages nothing. */
  kids: boolean;
  /** 6 or 12 on a kid; `null` on a grown-up. */
  kidsAge: 6 | 12 | null;
  /** The grown-up who made this kid. `null`, or one not here, is the admin's. */
  parentId: string | null;
  admin: boolean;
  hasPin: boolean;
}

/** Everything stored about a profile, the PIN included. Never sent as is. */
export interface ProfileRow {
  id: string;
  name: string;
  createdAt: number;
  kids: number;
  kidsAge: number | null;
  kidsAgeUpdatedAt: number;
  parentId: string | null;
  adminClaimedAt: number | null;
  pinHash: string | null;
  pinSalt: string | null;
  pinUpdatedAt: number;
}

/** What a new profile is, beside its name. */
export interface NewProfile {
  kids?: boolean;
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
              kids_age_updated_at AS kidsAgeUpdatedAt, parent_id AS parentId,
              admin_claimed_at AS adminClaimedAt, pin_hash AS pinHash, pin_salt AS pinSalt,
              pin_updated_at AS pinUpdatedAt
         FROM profiles ORDER BY created_at`,
    )
    .all() as ProfileRow[];
}

export function toProfile(row: ProfileRow): Profile {
  const kids = row.kids !== 0;
  return {
    id: row.id,
    name: row.name,
    createdAt: row.createdAt,
    kids,
    // A kid always has a limit, whatever the column holds: 12 is what every
    // kid saw before there was a choice.
    kidsAge: kids ? (row.kidsAge === 6 ? 6 : 12) : null,
    parentId: row.parentId,
    // A kid manages nothing and opens freely. A grown-up another device
    // later calls a kid keeps its old claim and PIN in their columns; they
    // must not make it an admin, or a profile with a PIN, here.
    admin: !kids && row.adminClaimedAt !== null,
    hasPin: !kids && row.pinHash !== null,
  };
}

/** Who watches this library, oldest first. Empty until someone says. */
export function listProfiles(db: Database | null): Profile[] {
  return profileRows(db).map(toProfile);
}

/**
 * Makes a profile. A kid nobody chose a limit for starts at FSK 12 dated 0 —
 * older than any limit a parent chooses, so the first real choice, made here
 * or synced in, wins. A kid made by sync takes its merged limit right after.
 */
export function insertProfile(db: Database | null, name: unknown, role: NewProfile = {}): Profile | null {
  if (!db) return null;
  const clean = cleanName(name);
  if (clean === null) return null;
  const kids = role.kids === true;
  const row: ProfileRow = {
    id: crypto.randomUUID(),
    name: clean,
    createdAt: Date.now(),
    kids: kids ? 1 : 0,
    kidsAge: kids ? 12 : null,
    kidsAgeUpdatedAt: 0,
    parentId: null,
    adminClaimedAt: null,
    pinHash: null,
    pinSalt: null,
    pinUpdatedAt: 0,
  };
  db.query(
    `INSERT INTO profiles(id, name, created_at, kids, kids_age, kids_age_updated_at, parent_id,
                          admin_claimed_at, pin_hash, pin_salt, pin_updated_at)
       VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)`,
  ).run(row.id, row.name, row.createdAt, row.kids, row.kidsAge, row.kidsAgeUpdatedAt, row.parentId,
    row.adminClaimedAt, row.pinHash, row.pinSalt, row.pinUpdatedAt);
  return toProfile(row);
}

/** Takes everything that was theirs with it — every table cascades. */
export function deleteProfileById(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("DELETE FROM profiles WHERE id = ?1").run(id).changes > 0;
}

export function profileExists(db: Database | null, id: string): boolean {
  if (!db) return false;
  return db.query("SELECT 1 FROM profiles WHERE id = ?1").get(id) !== null;
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
