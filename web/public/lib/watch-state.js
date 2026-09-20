/**
 * What the player remembers, held here and written through to the server.
 *
 * Server-side rather than in this browser, which is the whole point: the
 * position is not a property of the device that was watching, it is a
 * property of the title. A phone and a laptop pointed at the same player are
 * reading the same rows, so putting a film down on one and picking it up on
 * the other needs nothing beyond this.
 *
 * Every write fails silently. A player that cannot record a position is still
 * a player, and a viewer interrupted mid-film by an error about bookkeeping
 * has been served worse than one who simply loses their place.
 */

/** Where this device's answer to "who is watching" is kept. */
const CHOSEN = "mediagram.profile";

/** @type {{remembers: boolean, profiles: any[], profileId: string|null, progress: Map<string, {at: number, duration: number|null, updatedAt: number}>, watchlist: Set<string>, collections: any[]}} */
const held = {
  remembers: false,
  profiles: [],
  profileId: null,
  progress: new Map(),
  watchlist: new Set(),
  collections: [],
  /** Marked as a child's. Shared by everyone on this player, not held per
   *  profile — a mark is about the title, not about who is watching. */
  kids: new Set(),
};

/**
 * The profile chosen on this device.
 *
 * In `localStorage` rather than on the server, because it is a property of
 * the device and not of the library: the television stays on the television's
 * profile, and a laptop that two people share asks each time it is cleared.
 * Wrapped because a browser with storage disabled must still play films.
 */
function remembered() {
  try {
    return window.localStorage.getItem(CHOSEN);
  } catch {
    return null;
  }
}

function remember(id) {
  try {
    if (id === null) window.localStorage.removeItem(CHOSEN);
    else window.localStorage.setItem(CHOSEN, id);
  } catch {
    // The choice lasts this page load instead, which still works.
  }
}

/** Every state path is under the profile it belongs to. */
const under = (rest) => `/api/profiles/${encodeURIComponent(held.profileId)}${rest}`;

/** One write, with its failure swallowed. `null` means it did not happen. */
async function write(path, method, body) {
  try {
    const response = await fetch(path, {
      method,
      headers: body === undefined ? {} : { "content-type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return response.ok ? response : null;
  } catch {
    return null;
  }
}

/**
 * Who watches this library. Asked before anything else, because every other
 * question here is about one of them.
 */
export async function loadProfiles() {
  try {
    const response = await fetch("/api/profiles");
    if (!response.ok) return;
    const said = await response.json();
    held.remembers = said.remembers === true;
    held.profiles = said.profiles ?? [];
  } catch {
    // A player that cannot list profiles has none, and the page says so.
  }
}

export const profiles = () => held.profiles;
export const profileId = () => held.profileId;
export const profile = () => held.profiles.find((entry) => entry.id === held.profileId) ?? null;

/**
 * The profile this device last chose, if it is still one of them.
 *
 * Checked rather than trusted: a profile deleted on another device leaves
 * this one holding an id that no longer names anybody, and the page should
 * ask again rather than write into nothing.
 */
export function rememberedProfile() {
  const id = remembered();
  return id && held.profiles.some((entry) => entry.id === id) ? id : null;
}

export async function createProfile(name) {
  const response = await write("/api/profiles", "POST", { name });
  if (!response) return null;
  const made = await response.json();
  held.profiles.push(made);
  return made;
}

export async function renameProfile(id, name) {
  if (!(await write(`/api/profiles/${encodeURIComponent(id)}`, "PATCH", { name }))) return false;
  const found = held.profiles.find((entry) => entry.id === id);
  if (found) found.name = name;
  return true;
}

export async function deleteProfile(id) {
  if (!(await write(`/api/profiles/${encodeURIComponent(id)}`, "DELETE"))) return false;
  held.profiles = held.profiles.filter((entry) => entry.id !== id);
  if (held.profileId === id) held.profileId = null;
  return true;
}

/** Chooses a profile and loads what is theirs. */
export async function useProfile(id) {
  held.profileId = id;
  remember(id);
  held.progress = new Map();
  held.watchlist = new Set();
  held.collections = [];
  if (id === null) return;

  try {
    const response = await fetch(under("/state"));
    if (!response.ok) return;
    const said = await response.json();
    held.progress = new Map(
      (said.progress ?? []).map((row) => [
        row.setId,
        { at: Number(row.at) || 0, duration: row.duration ?? null, updatedAt: row.updatedAt },
      ]),
    );
    held.watchlist = new Set(said.watchlist ?? []);
    held.collections = said.collections ?? [];
  } catch {
    // A profile whose state cannot be read is one with none yet.
  }
}

/** Whether anything written here is being kept. */
export const remembers = () => held.remembers;

/** Where the viewer got to in `setId`, or `null`. */
export const progressOf = (setId) => held.progress.get(setId) ?? null;

/** Every title with a position, most recently watched first. */
export function inProgress() {
  return [...held.progress.entries()]
    .sort((a, b) => b[1].updatedAt - a[1].updatedAt)
    .map(([setId, at]) => ({ setId, ...at }));
}

/**
 * Records a position.
 *
 * Kept locally first so the shelf and the progress rules are right straight
 * away, rather than after a round trip that may not come back.
 */
export function setProgress(setId, at, duration) {
  held.progress.set(setId, { at, duration: duration ?? null, updatedAt: Date.now() });
  void write(under(`/progress/${encodeURIComponent(setId)}`), "PUT", { at, duration });
}

/**
 * The last position of a title being closed, sent so it survives the page.
 *
 * `sendBeacon` because a normal request made while the tab is going away is
 * cancelled with it — which is exactly the moment the position matters most.
 */
export function flushProgress(setId, at, duration) {
  held.progress.set(setId, { at, duration: duration ?? null, updatedAt: Date.now() });
  try {
    const body = new Blob([JSON.stringify({ at, duration })], { type: "application/json" });
    if (navigator.sendBeacon(under(`/progress/${encodeURIComponent(setId)}`), body)) return;
  } catch {
    // Falls through to the ordinary write, which may still make it.
  }
  void write(under(`/progress/${encodeURIComponent(setId)}`), "PUT", { at, duration });
}

/** Forgets a position: watched to the end, or started again. */
export function clearProgress(setId) {
  held.progress.delete(setId);
  void write(under(`/progress/${encodeURIComponent(setId)}`), "DELETE");
}

export const isWatchlisted = (setId) => held.watchlist.has(setId);
export const watchlist = () => [...held.watchlist];

export function setWatchlisted(setId, listed) {
  if (listed) held.watchlist.add(setId);
  else held.watchlist.delete(setId);
  void write(under(`/watchlist/${encodeURIComponent(setId)}`), listed ? "PUT" : "DELETE");
}

/**
 * The titles marked as a child's.
 *
 * Read once at startup and not per profile, because the mark belongs to the
 * library: switching to another profile must not change which films are a
 * child's. The path has no profile in it for the same reason.
 */
export async function loadKids() {
  try {
    const response = await fetch("/api/kids");
    if (!response.ok) return;
    const said = await response.json();
    held.kids = new Set(Array.isArray(said.kids) ? said.kids : []);
  } catch {
    // A player that cannot ask simply has an empty shelf, which is the same
    // thing it has before anything is marked.
  }
}

export const isKids = (setId) => held.kids.has(setId);
export const kids = () => [...held.kids];

export function setKids(setId, marked) {
  if (marked) held.kids.add(setId);
  else held.kids.delete(setId);
  void write(`/api/kids/${encodeURIComponent(setId)}`, marked ? "PUT" : "DELETE", marked ? {} : undefined);
}

export const collections = () => held.collections;

export async function createCollection(name) {
  const response = await write(under("/collections"), "POST", { name });
  if (!response) return null;
  const made = await response.json();
  held.collections.push(made);
  return made;
}

export async function renameCollection(id, name) {
  if (!(await write(under(`/collections/${encodeURIComponent(id)}`), "PATCH", { name }))) return false;
  const list = held.collections.find((entry) => entry.id === id);
  if (list) list.name = name;
  return true;
}

export async function deleteCollection(id) {
  if (!(await write(under(`/collections/${encodeURIComponent(id)}`), "DELETE"))) return false;
  held.collections = held.collections.filter((entry) => entry.id !== id);
  return true;
}

export function setInCollection(id, setId, member) {
  const list = held.collections.find((entry) => entry.id === id);
  if (!list) return;
  // Local first, and idempotent, so a title added twice appears once here as
  // it does in the store.
  if (member && !list.items.includes(setId)) list.items.push(setId);
  if (!member) list.items = list.items.filter((held) => held !== setId);
  void write(
    under(`/collections/${encodeURIComponent(id)}/items/${encodeURIComponent(setId)}`),
    member ? "PUT" : "DELETE",
    member ? {} : undefined,
  );
}
