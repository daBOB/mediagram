/**
 * Watch together as this tab knows it, shared by Home's notice, the title
 * pages and the player. It is fed by `GET /api/together` and by the
 * `together` event on the tab's one event stream (`library-session.js`).
 * `offset` is how far the server's clock is from this one, so expected
 * positions are reckoned on the clock the session was written with.
 * The format is `docs/mlib-spec.md` §7b. Types are in `together-now.d.ts`.
 */

import { isLive } from "./together/follow.js";

const UNREACHABLE = "Telegram isn't reachable";

/**
 * This tab's screen id: random, and one per tab, as the session's `members`
 * want it. Not crypto.randomUUID: it exists only in secure contexts, and the
 * player is usually opened over plain http on a LAN address. getRandomValues
 * works there.
 */
export const screen = Array.from(crypto.getRandomValues(new Uint8Array(16)), (b) => b.toString(16).padStart(2, "0")).join("");

let known = { available: false, reason: UNREACHABLE, live: null, offset: 0 };
const listeners = new Set();
let opener = null;
let setOf = () => null;
let joinNext = null;

export const togetherNow = () => known;
export const serverNow = () => Date.now() + known.offset;
export const inTogether = () => known.live?.session.members.some((m) => m.screen === screen) ?? false;

export function onTogether(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function tell(heard) {
  for (const listener of listeners) listener(known, heard);
}

/** `session`, unless it is over: ended, or stale by the length of the title it plays. */
function stillLive(session, now) {
  return session && isLive(session, setOf(session.set)?.duration ?? null, now) ? session : null;
}

export async function refreshTogether() {
  try {
    const response = await fetch("/api/together");
    // No watch together on this server at all reads the same as one that can't reach Telegram.
    const body = response.ok ? await response.json() : { available: false, reason: UNREACHABLE, live: null, serverNow: Date.now() };
    const session = stillLive(body.live?.session ?? null, body.serverNow);
    known = { available: body.available, reason: body.reason, live: session && body.live, offset: body.serverNow - Date.now() };
    tell(null);
  } catch {
    // Offline for a moment: what was known stays, and the next event or open corrects it.
  }
}

/** A `together` event. The server only sends one while it reaches Telegram. */
export function heardTogether(payload) {
  const session = stillLive(payload.session, payload.serverNow);
  const live = session ? { id: payload.id, session } : known.live?.id === payload.id ? null : known.live;
  known = { available: true, reason: null, live, offset: payload.serverNow - Date.now() };
  tell({ id: payload.id, session });
}

/** A watch-together move; never throws. `keepalive`, so a leave sent as the page goes still arrives. */
export async function together(body) {
  try {
    const response = await fetch("/api/together", {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(body), keepalive: true,
    });
    const text = await response.text();
    return { ok: response.ok, status: response.status, body: text ? JSON.parse(text) : null };
  } catch {
    return { ok: false, status: 0, body: { reason: UNREACHABLE } };
  }
}

/**
 * How a title is opened, and found, by id. `app.js` registers both, because it
 * holds the library: what it finds is this profile's catalog, so a title a
 * kids profile may not see is not found.
 */
export function setTogetherOpener(open, find) {
  opener = open;
  setOf = find;
}

/** The set `setId` names in this profile's catalog, or `null`. */
export const setForTogether = (setId) => setOf(setId);

/** Opens `setId` through the app, joining session `join` as the player opens it; nothing for a title not here. */
export function openForTogether(setId, join = null) {
  const set = setOf(setId);
  if (!set || !opener) return;
  joinNext = join;
  opener(set);
}

export function takeJoin() {
  const id = joinNext;
  joinNext = null;
  return id;
}
