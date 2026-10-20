/**
 * Who is one person, before anything is merged.
 *
 * Every merge keys a viewer by normalised name, so a renamed profile would
 * otherwise split in two: the new name from current documents, the old one
 * from an older build's or a stale document. This pre-pass joins a name and
 * every former name any entry lists into one person (a union-find over names,
 * so a chain A→B→C joins even from partial documents), picks the name the
 * newest rename gave it, and rewrites every name in every document to that
 * spelling. `mergeStates`, `mergeStats`, `mergeRoles` and `mergeHouseholds`
 * then run on the rewritten records exactly as they do without renames.
 *
 * The name shown is the entry with the highest `renamedAt` (absent is 0), ties
 * by the greater device id as `keep`'s are, so with nobody renamed the
 * spelling is chosen exactly as `mergeStates` always chose it.
 */

import { MAX_FORMER_NAMES, sortedNames, type NameKeys } from "./name-record";
import { normalName, type SyncRecord } from "./sync-record";

export function resolveRenames(records: SyncRecord[]): { records: SyncRecord[]; people: Map<string, NameKeys> } {
  /** Each normalised name to its parent in the union-find; a person's root points at itself. */
  const root = new Map<string, string>();
  const find = (name: string): string => {
    let top = name;
    while (root.get(top) !== top) top = root.get(top)!;
    // Every name on the way points at the root now, so a long chain is walked once.
    for (let at = name; at !== top;) {
      const next = root.get(at)!;
      root.set(at, top);
      at = next;
    }
    return top;
  };
  const meet = (name: string): string => {
    if (!root.has(name)) root.set(name, name);
    return find(name);
  };

  for (const record of records) {
    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;
      meet(name);
      for (const former of profile.formerNames ?? []) {
        const other = normalName(former);
        if (other !== null) root.set(meet(other), find(name));
      }
    }
  }

  const winners = new Map<string, { name: string; renamedAt: number; device: string }>();
  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;
      const renamedAt = profile.renamedAt ?? 0;
      const standing = winners.get(find(name));
      // Newer stamp, then greater device; a full tie keeps the entry met first.
      if (standing === undefined || renamedAt > standing.renamedAt ||
          (renamedAt === standing.renamedAt && device > standing.device)) {
        winners.set(find(name), { name: profile.name.trim(), renamedAt, device });
      }
    }
  }

  /** The person's spelling of a name, or the name as it is when it belongs to nobody. */
  const spelled = (name: string): string => {
    const known = normalName(name);
    return known !== null && root.has(known) ? winners.get(find(known))?.name ?? name : name;
  };

  const members = new Map<string, string[]>();
  for (const name of root.keys()) {
    const top = find(name);
    const group = members.get(top);
    if (group === undefined) members.set(top, [name]);
    else group.push(name);
  }
  const people = new Map<string, NameKeys>();
  for (const [top, winner] of winners) {
    const own = normalName(winner.name)!;
    const formerNames = sortedNames(members.get(top)!.filter((name) => name !== own)).slice(0, MAX_FORMER_NAMES);
    people.set(own, {
      ...(winner.renamedAt > 0 ? { renamedAt: winner.renamedAt } : {}),
      ...(formerNames.length > 0 ? { formerNames } : {}),
    });
  }

  const rewritten = records.map((record) => {
    if (!record) return record;
    return {
      ...record,
      profiles: (record.profiles ?? []).map((profile) => ({
        ...profile,
        name: spelled(profile.name),
        ...(profile.parent !== undefined ? { parent: spelled(profile.parent) } : {}),
      })),
      ...(record.households !== undefined
        ? {
          households: record.households.map((one) =>
            one.admin ? { ...one, admin: { ...one.admin, name: spelled(one.admin.name) } } : one),
        }
        : {}),
    };
  });
  return { records: rewritten, people };
}
