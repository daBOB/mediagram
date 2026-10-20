/**
 * The household routes: a device joining one, and the owner's actions.
 *
 * Each answers an outcome as `profile-api.js`'s calls do, and never throws:
 * `{ ok: true }`, or `{ ok: false, reason, retryAfter }` for `refusalText`.
 * The PIN travels in the body. The server decides every one of these.
 */

import { change } from "./profile-api.js";
import { joined } from "./household.js";

const at = (id, rest = "") => `/api/households/${encodeURIComponent(id)}${rest}`;

/**
 * This device shows household `id` from now on, given its admin's PIN or the
 * owner's. If that takes it away from the profile chosen here, the device lets
 * go of that profile, as it does of one removed.
 */
export const join = (id, pin) => change("POST", at(id, "/join"), { pin }, () => joined(id));

/** A household named `name`, run by `admin`: `{ adminId }` or `{ adminName }`, with `newPin` where it has none. */
export const create = (actorId, pin, name, admin) => change("POST", "/api/households", { actorId, pin, name, ...admin });
export const rename = (actorId, pin, id, name) => change("PUT", at(id, "/name"), { actorId, pin, name });
/** `newPin` only for an admin with no PIN yet; every admin has one. */
export const setAdmin = (actorId, pin, id, adminId, newPin) => change("PUT", at(id, "/admin"), { actorId, pin, adminId, newPin });
/** `newPin` only where the grown-up moved becomes the household's admin and has no PIN yet. */
export const move = (actorId, pin, id, profileId, newPin) => change("POST", at(id, "/move"), { actorId, pin, profileId, newPin });
/** Only an empty household other than home. */
export const remove = (actorId, pin, id) => change("DELETE", at(id), { actorId, pin });
