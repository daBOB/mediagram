/**
 * A profile's renames between the `profiles` row and the wire: when it took
 * its name and the names it had, and taking a merged person in by all of them.
 *
 * Rows are keyed by id and only the wire uses names, so a rename taken in is
 * the row's name updated in place, its history left where it is. Kept out of
 * `store.ts` for the reason `roles-exchange.ts` is; SQLite failures propagate,
 * so `importMerged` rolls the whole import back.
 */

import type { Database } from "bun:sqlite";
import type { MergedProfile } from "./merged";
import type { NameKeys } from "./name-record";
import type { ProfileRow } from "./profiles";
import { foldProfile } from "./profiles-fold";
import { findOrCreateProfile } from "./profiles-insert";
import { firstOfEachName } from "./roles-exchange";
import { normalName } from "./sync-record";

interface NameColumns {
  id: string;
  name: string;
  renamedAt: number;
  formerNames: string; // a JSON list, sorted by bytes, each once; '[]' for none
}

function nameColumns(db: Database): NameColumns[] {
  return db.query("SELECT id, name, renamed_at AS renamedAt, former_names AS formerNames FROM profiles")
    .all() as NameColumns[];
}

/** Each profile's name keys, by local id — what `exportRecord` spreads in. A profile nobody renamed says neither. */
export function exportNames(db: Database | null): Map<string, NameKeys> {
  if (!db) return new Map();
  return new Map(nameColumns(db).map((row): [string, NameKeys] => [row.id, {
    ...(row.renamedAt > 0 ? { renamedAt: row.renamedAt } : {}),
    ...(row.formerNames !== "[]" ? { formerNames: JSON.parse(row.formerNames) as string[] } : {}),
  }]));
}

/**
 * This device's row for a merged person, made if it has never met them, and
 * how many rows taking them in changed.
 *
 * The first local row of the merged name and of each former name are all this
 * person. The oldest is kept — the id this device has used longest — and every
 * other is folded into it. The kept row then takes the merged spelling when it
 * answers to another name or the merged rename is newer, and the merged former
 * names when they differ; a spelling of the same name with no newer rename
 * stays this device's own, as it always has.
 */
export function takeIn(db: Database, profile: MergedProfile): { id: string; kids: boolean; changed: number } | null {
  // Normalised again for a hand-built merge, as `findOrCreateProfile` does.
  const name = normalName(profile.name);
  if (name === null) return null;
  const local = firstOfEachName(db);
  const found = new Map<string, ProfileRow>();
  for (const one of [name, ...(profile.formerNames ?? [])]) {
    const row = local.get(one);
    if (row !== undefined) found.set(row.id, row);
  }
  const [oldest, ...others] = [...found.values()]
    .sort((a, b) => a.createdAt - b.createdAt || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));

  const kept = oldest === undefined
    ? findOrCreateProfile(db, profile.name, profile.displayName, profile.kids === true)
    : { id: oldest.id, kids: oldest.kids !== 0, created: false };
  if (kept === null) return null;
  for (const other of others) foldProfile(db, other.id, kept.id);
  const renamed = rename(db, kept.id, name, profile);
  return { id: kept.id, kids: kept.kids, changed: others.length + (kept.created || renamed ? 1 : 0) };
}

/** The merged name keys written to the kept row; whether anything was. */
function rename(db: Database, id: string, name: string, profile: MergedProfile): boolean {
  const row = db.query("SELECT name, renamed_at AS renamedAt, former_names AS formerNames FROM profiles WHERE id = ?1")
    .get(id) as Omit<NameColumns, "id">;
  const renamedAt = profile.renamedAt ?? 0;
  let wrote = false;
  if (normalName(row.name) !== name || renamedAt > row.renamedAt) {
    db.query("UPDATE profiles SET name = ?2, renamed_at = ?3 WHERE id = ?1").run(id, profile.displayName, renamedAt);
    wrote = true;
  }
  // Compared as stored: both sides are sorted by bytes, each name once (`sortedNames`).
  const former = JSON.stringify(profile.formerNames ?? []);
  if (former !== row.formerNames) {
    db.query("UPDATE profiles SET former_names = ?2 WHERE id = ?1").run(id, former);
    wrote = true;
  }
  return wrote;
}
