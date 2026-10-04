/**
 * A viewer's role, reconciled across devices: a kid's limit, a grown-up's
 * PIN, who made a kid, and which one viewer is the household's admin. Split
 * out of `merge.ts`, which `mergeStates` calls this from.
 *
 * **A limit and a PIN: the newest `updatedAt` wins.** Not sticky the way
 * `kids` is — a parent lowers a limit as often as it raises one, and an
 * admin resets a PIN. Ties break by device id through `keep`, as every
 * row's do.
 *
 * **A kid nobody gave a limit is FSK 12 at time 0.** That is what a document
 * from before limits says by saying only `kids: true`, and 0 is older than
 * any real change, so the first limit a parent sets wins wherever it lands.
 *
 * **A parent is set once, and only a kid has one.** Two documents naming
 * different parents is a bug, not a case, but it is still settled the way
 * `displayName` is — by device id — so the answer never depends on the
 * order documents arrive in.
 *
 * **One admin, household-wide, and a grown-up.** The earliest claim wins,
 * ties by the smaller name, and only that viewer carries the key: however
 * devices wake up, the merge never names two. A claim on a viewer any
 * document calls a kid is ignored — a kid manages nothing.
 *
 * "A kid" is the sticky test `mergeStates` applies to `kids`: once any
 * device's document says so, no document lacking the flag undoes it.
 */

import { normalName, type ListRow, type SyncRecord } from "./sync-record";
import type { RoleKeys } from "./roles-record";
import { keep, type Held } from "./tie-break";

/** The role keys a merged profile carries beside `kids`. */
export type MergedRoles = Omit<RoleKeys, "kids">;

/**
 * How a Kids mark ranks against another at the same `updatedAt`, before the
 * device id is asked: "from 6" first. A build from before ages drops `age`
 * from every mark it takes in and writes the mark back at the same time, so
 * a tie between a mark from 6 and one without is that echo, not a second
 * choice — changing a mark's age always moves its clock (`setKids`). Left to
 * the device id, the echo would strip the age everywhere whenever the older
 * build's id sorted higher: the trap `sync-record.ts` describes for
 * `watched`, met here by a rank rather than a key of its own.
 */
export const kidsMarkRank = (row: ListRow): number => (row.age === 6 && !row.removed ? 1 : 0);

type KidsAge = NonNullable<RoleKeys["kidsAge"]>;
type Pin = NonNullable<RoleKeys["pin"]>;

const FROM_TWELVE: KidsAge = { age: 12, updatedAt: 0 };

/** Each viewer's merged role keys by normalised name; a key is omitted when it does not apply. */
export function mergeRoles(records: SyncRecord[]): Map<string, MergedRoles> {
  const viewers = new Set<string>();
  const kids = new Set<string>();
  const limits = new Map<string, Held<KidsAge>>();
  const pins = new Map<string, Held<Pin>>();
  const parents = new Map<string, { name: string; from: string }>();
  const claims = new Map<string, number>();

  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;
      viewers.add(name);
      if (profile.kids === true) kids.add(name);
      if (profile.kidsAge) keep(limits, name, profile.kidsAge, device);
      if (profile.pin) keep(pins, name, profile.pin, device);
      const parent = normalName(profile.parent);
      const standing = parents.get(name);
      if (parent !== null && (standing === undefined || device > standing.from)) parents.set(name, { name: parent, from: device });
      const claimed = profile.admin?.claimedAt;
      if (claimed !== undefined && claimed < (claims.get(name) ?? Infinity)) claims.set(name, claimed);
    }
  }

  const admin = earliest(claims, kids);
  const merged = new Map<string, MergedRoles>();
  for (const name of viewers) {
    const kid = kids.has(name);
    const parent = parents.get(name)?.name;
    const pin = pins.get(name)?.row;
    merged.set(name, {
      ...(admin === name ? { admin: { claimedAt: claims.get(name)! } } : {}),
      ...(kid ? { kidsAge: limits.get(name)?.row ?? FROM_TWELVE } : {}),
      ...(kid && parent !== undefined ? { parent } : {}),
      ...(!kid && pin !== undefined ? { pin } : {}),
    });
  }
  return merged;
}

/** The grown-up with the earliest claim; a tie goes to the smaller name. */
function earliest(claims: Map<string, number>, kids: Set<string>): string | null {
  let best: { name: string; at: number } | null = null;
  for (const [name, at] of claims) {
    if (kids.has(name)) continue;
    if (best === null || at < best.at || (at === best.at && name < best.name)) best = { name, at };
  }
  return best?.name ?? null;
}
