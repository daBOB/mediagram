/**
 * Who may manage whom. Pure, and the one place the rule is written; the
 * Android core runs the same `profile-rules.json` against its port.
 *
 * A kid manages nothing. The admin adds and removes grown-ups and may set any
 * grown-up's PIN, but is never removed — by anyone, itself included. A kid
 * belongs to exactly one grown-up, the one who made it, and only that one
 * removes it or sets its limit; a kid whose parent is not a grown-up here —
 * none recorded, removed, or never met — is the admin's.
 */

import { cleanName } from "./profiles";
import { normalName } from "./sync-record";

/**
 * Whether a profile here already answers to `name`. Sync knows a viewer by
 * its normalised name and only ever turns `kids` on, so a second profile of
 * the same name would merge with the first everywhere — a kid called after
 * the admin would make the admin a kid. Compared as stored names are kept:
 * edges trimmed, runs of space collapsed, then normalised.
 */
export function nameTaken(existing: string[], name: unknown): boolean {
  const wanted = normalName(cleanName(name));
  return wanted !== null && existing.some((one) => normalName(cleanName(one)) === wanted);
}

/** What the rule needs to know about a profile, and nothing else. */
export interface RoleView {
  id: string;
  kids: boolean;
  admin: boolean;
  parentId: string | null;
}

export type Action = "create-grown-up" | "create-kid" | "remove" | "set-pin" | "set-kids-age";

/**
 * Only a grown-up is ever the admin. A view can still say a kid is — a
 * grown-up that held the claim and that another device later called a kid —
 * and a kid read as admin would own every kid nobody else does.
 */
const isAdmin = (one: RoleView): boolean => one.admin && !one.kids;

/** Who manages `kid`: its parent while that is a grown-up here, else the admin, else nobody. */
export function ownerOf(profiles: RoleView[], kid: RoleView): string | null {
  const parent = profiles.find((one) => one.id === kid.parentId && !one.kids);
  return parent?.id ?? profiles.find(isAdmin)?.id ?? null;
}

/** Whether `actorId` may do `action` to `targetId`. Never throws. */
export function allowed(profiles: RoleView[], actorId: string, action: Action, targetId: string): boolean {
  const actor = profiles.find((one) => one.id === actorId);
  if (actor === undefined || actor.kids) return false;
  if (action === "create-grown-up") return actor.admin;
  if (action === "create-kid") return true;

  const target = profiles.find((one) => one.id === targetId);
  if (target === undefined) return false;
  switch (action) {
    case "remove":
      if (isAdmin(target)) return false;
      return target.kids ? ownerOf(profiles, target) === actor.id : target.id !== actor.id && actor.admin;
    case "set-pin":
      return !target.kids && (target.id === actor.id || actor.admin);
    case "set-kids-age":
      return target.kids && ownerOf(profiles, target) === actor.id;
    default:
      return false;
  }
}
