/**
 * Managing profiles: the calls behind the picker's PIN prompt and the
 * Manage profiles panel, and what each grown-up may manage.
 *
 * Every call answers an outcome and never throws: `{ ok: true }`, or
 * `{ ok: false, reason, retryAfter }` — `reason` one of the server's
 * (`invalid`, `name-taken`, `not-found`, `wait`, `no-pin`, `wrong-pin`, `not-allowed`;
 * `retryAfter` in seconds with `wait`), or `null` when no answer named one:
 * the network failed, or the request was turned away before the rule saw it
 * (a cross-site write is a bare 403).
 *
 * Nothing here is a check — the server decides every one of these. A change
 * that goes through reads the profile list again, so the picker, the panel
 * and the header all see it; and if it took away the profile this device is
 * on (with its parent, perhaps), the device lets go of it rather than keep
 * writing to nobody — or, on a kid's screen, fall back to showing everything.
 */

import { loadProfiles, profile, profileId, useProfile } from "./watch-state.js";

const REASONS = new Set(["invalid", "name-taken", "not-found", "wait", "no-pin", "wrong-pin", "not-allowed"]);

/** @typedef {{ ok: boolean, reason?: string|null, retryAfter?: number }} Outcome */

/**
 * One request. The PIN travels in the body, never in the address, so no log or history keeps it.
 * @returns {Promise<Outcome>}
 */
async function call(method, path, body) {
  try {
    const response = await fetch(path, {
      method,
      headers: { "content-type": "application/json" },
      body: JSON.stringify(body),
    });
    if (response.ok) return { ok: true };
    const said = await response.json();
    const retryAfter = Number(said?.retryAfter) || undefined;
    return { ok: false, reason: REASONS.has(said?.reason) ? said.reason : null, ...(retryAfter ? { retryAfter } : {}) };
  } catch {
    return { ok: false, reason: null };
  }
}

/** A call that changes who there is: on success the list is read again. @returns {Promise<Outcome>} */
async function change(method, path, body) {
  const outcome = await call(method, path, body);
  if (outcome.ok) {
    await loadProfiles();
    if (profileId() !== null && profile() === null) await useProfile(null);
  }
  return outcome;
}

const at = (id, rest = "") => `/api/profiles/${encodeURIComponent(id)}${rest}`;

/** Checks a PIN for entering `id` from the picker. Changes nothing. */
export const unlock = (id, pin) => call("POST", at(id, "/unlock"), { pin });
/** Makes `id` the household's admin; one with no PIN yet takes `pin` as its PIN. */
export const claimAdmin = (id, pin) => change("POST", at(id, "/claim-admin"), { pin });
/** Gives `id` the PIN `newPin`. A grown-up with none yet sets its own with `pin` empty. */
export const setPin = (actorId, pin, id, newPin) => change("PUT", at(id, "/pin"), { actorId, pin, newPin });
/** A grown-up (`{ name, kids: false, newPin }`) or a kid (`{ name, kids: true, kidsAge }`). */
export const create = (actorId, pin, fields) => change("POST", "/api/profiles", { actorId, pin, ...fields });
/**
 * The first grown-up on a player that knows none: no actor and no current
 * PIN, and it runs the household. Refused once any grown-up exists here.
 */
export const createFirst = (name, newPin) => change("POST", "/api/profiles", { name, newPin });
/** Removes `id` and everything they watched; a grown-up's kids go with them. */
export const remove = (actorId, pin, id) => change("DELETE", at(id), { actorId, pin });
/** Sets the kid `id`'s limit to `age`, 6 or 12. */
export const setKidsAge = (actorId, pin, id, age) => change("PUT", at(id, "/kids-age"), { actorId, pin, age });

/**
 * A grown-up saying who they are: their PIN, or — for one from before PINs —
 * the one they choose now, which becomes theirs.
 */
export const prove = (entry, pin) => (entry.hasPin ? unlock(entry.id, pin) : setPin(entry.id, "", entry.id, pin));

/**
 * Whose kid this is: its parent while that is a grown-up here, otherwise the
 * admin's, otherwise nobody's. Only a grown-up is ever the admin, as the
 * server's rule has it.
 */
export function ownerOf(profiles, kid) {
  if (profiles.some((entry) => entry.id === kid.parentId && !entry.kids)) return kid.parentId;
  return profiles.find((entry) => entry.admin && !entry.kids)?.id ?? null;
}

/**
 * What `actorId` may manage, as Manage profiles draws it: every other
 * grown-up when the actor is the admin, and the kids that are the actor's own.
 * `actor` is `null` for a kid or for nobody, who manage nothing.
 */
export function manageable(profiles, actorId) {
  const actor = profiles.find((entry) => entry.id === actorId && !entry.kids) ?? null;
  return {
    actor,
    grownUps: actor?.admin ? profiles.filter((entry) => !entry.kids && entry.id !== actorId) : [],
    kids: actor ? profiles.filter((entry) => entry.kids && ownerOf(profiles, entry) === actorId) : [],
  };
}
