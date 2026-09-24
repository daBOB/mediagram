/**
 * What the player remembers, held here and written through to the server.
 *
 * Server-side rather than in this browser, which is the whole point: the
 * position is not a property of the device that was watching, it is a
 * property of the title. A phone and a laptop pointed at the same player are
 * reading the same rows, so putting a film down on one and picking it up on
 * the other needs nothing beyond this.
 *
 * Position, mark, membership and preference setters update local state
 * immediately and persist best-effort; their void return does not acknowledge
 * persistence, and failed writes do not roll back that local update.
 * Shelf-affecting setters notify subscribers immediately. Profile and
 * collection management waits for the server:
 * creation returns a record or null, and rename/delete returns a boolean.
 * Those callers report a failed acknowledgement so the viewer can retry.
 */

/** Where this device's answer to "who is watching" is kept. */
const CHOSEN = "mediagram.profile";

/** Distinguishes separate visits to the same profile while reads are in flight. */
let profileSelection = 0;
const changeListeners = new Set();

/** Observe shelf-affecting state changes; the application owns redraw timing. */
export function subscribeChanges(listener) {
  changeListeners.add(listener);
  return () => changeListeners.delete(listener);
}

function changed() {
  for (const listener of changeListeners) {
    try { listener(); }
    catch (error) { console.error("Could not update a watch-state view", error); }
  }
}

/**
 * @type {{remembers: boolean, profiles: any[], profileId: string|null,
 *   progress: Map<string, {at: number, duration: number|null, updatedAt: number}>,
 *   watchlist: Set<string>, collections: any[], watched: Map<string, number>,
 *   kids: Set<string>, preferences: Map<string, string>}}
 */
const held = {
  remembers: false,
  profiles: [],
  profileId: null,
  progress: new Map(),
  watchlist: new Set(),
  collections: [],
  /** Watched to the end, as set id -> when. Held because finishing clears
   *  the position, so this is the only thing that remembers a title was ever
   *  completed — and for a show watched to the end of an episode, the only
   *  thing that says when the show was last touched. */
  watched: new Map(),
  /** Marked as a child's. Shared by everyone on this player, not held per
   *  profile — a mark is about the title, not about who is watching. */
  kids: new Set(),
  /** What this viewer chose, as `scope\u0000name` -> value. Held whole because
   *  a title needs one the instant it opens, which is when there is no time
   *  to ask for it. */
  preferences: new Map(),
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

/** One write. `null` means persistence was not acknowledged, not that it cannot have happened. */
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

/** A creation also needs a readable response before it can be held locally. */
async function createRecord(path, name) {
  try {
    const response = await write(path, "POST", { name });
    return response ? await response.json() : null;
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

/** @returns {Promise<{id: string, name: string}|null>} The acknowledged profile, or null on failure. */
export async function createProfile(name) {
  const made = await createRecord("/api/profiles", name);
  if (!made) return null;
  held.profiles.push(made);
  return made;
}

/** @returns {Promise<boolean>} Whether the server acknowledged the rename. */
export async function renameProfile(id, name) {
  if (!(await write(`/api/profiles/${encodeURIComponent(id)}`, "PATCH", { name }))) return false;
  const found = held.profiles.find((entry) => entry.id === id);
  if (found) found.name = name;
  return true;
}

/** @returns {Promise<boolean>} Whether the server acknowledged deletion. */
export async function deleteProfile(id) {
  if (!(await write(`/api/profiles/${encodeURIComponent(id)}`, "DELETE"))) return false;
  held.profiles = held.profiles.filter((entry) => entry.id !== id);
  if (held.profileId === id) held.profileId = null;
  return true;
}

/** Chooses a profile and loads what is theirs. */
export async function useProfile(id) {
  const selection = ++profileSelection;
  held.profileId = id;
  remember(id);
  held.progress = new Map();
  held.watchlist = new Set();
  held.collections = [];
  held.watched = new Map();
  held.preferences = new Map();
  if (id === null) { changed(); return; }

  const said = await readState();
  if (selection !== profileSelection || held.profileId !== id) return;
  if (said) adopt(said);
  changed();
}

/**
 * Reads this profile's state again, for a page that has been open a while.
 *
 * The page reads it once, when a profile is chosen — so a tab left open while
 * the same viewer watched on the phone kept the position from before, and
 * resumed there. The server has the newer one; this fetches it.
 *
 * Positions are merged newest-wins rather than replaced: one this page wrote
 * a moment ago may still be on its way, and the server's older copy must not
 * put it back. Everything else is taken as the server has it.
 *
 * Gives up after `timeoutMs` and keeps what it had: this runs on the way to
 * playing a title, and a slow answer must not hold the title up. Resolves to
 * whether any position or completion changed.
 */
export async function refreshState(timeoutMs = 1500) {
  const asked = held.profileId;
  const selection = profileSelection;
  if (asked === null) return false;
  const said = await readState(AbortSignal.timeout(timeoutMs));
  // Another profile chosen while this was in flight: its state is not this.
  if (!said || selection !== profileSelection || held.profileId !== asked) return false;
  const before = progressSignature();
  const shelvesBefore = shelfSignature();
  const local = held.progress;
  adopt(said);
  for (const [setId, mine] of local) {
    const theirs = held.progress.get(setId);
    const finished = held.watched.get(setId) ?? 0;
    if ((theirs === undefined || mine.updatedAt > theirs.updatedAt) && mine.updatedAt > finished) {
      held.progress.set(setId, mine);
    }
  }
  if (shelfSignature() !== shelvesBefore) changed();
  return progressSignature() !== before;
}

function shelfSignature() {
  return JSON.stringify([[...held.progress], [...held.watched], [...held.watchlist], held.collections]);
}

/** Every position and completion as one string, to tell whether a refresh changed any. */
function progressSignature() {
  return JSON.stringify([[...held.progress], [...held.watched]]);
}

/** The profile's state as the server has it, or `null` if it cannot be read. */
async function readState(signal) {
  try {
    const response = await fetch(under("/state"), { signal });
    return response.ok ? await response.json() : null;
  } catch {
    // A profile whose state cannot be read is one with none yet.
    return null;
  }
}

/** Takes the server's answer as what this page holds. */
function adopt(said) {
  held.progress = new Map(
    (said.progress ?? []).map((row) => [
      row.setId,
      { at: Number(row.at) || 0, duration: row.duration ?? null, updatedAt: row.updatedAt },
    ]),
  );
  held.watchlist = new Set(said.watchlist ?? []);
  held.collections = said.collections ?? [];
  held.watched = new Map(
    (said.watched ?? []).map((row) => [row.setId, Number(row.finishedAt) || 0]),
  );
  held.preferences = new Map(
    (said.preferences ?? []).map((row) => [preferenceKey(row.scope, row.name), row.value]),
  );
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
 * @returns {void} Local update only; persistence is best-effort.
 */
export function setProgress(setId, at, duration) {
  held.progress.set(setId, { at, duration: duration ?? null, updatedAt: Date.now() });
  void write(under(`/progress/${encodeURIComponent(setId)}`), "PUT", { at, duration });
  changed();
}

/**
 * The last position of a title being closed, sent so it survives the page.
 *
 * `sendBeacon` because a normal request made while the tab is going away is
 * cancelled with it — which is exactly the moment the position matters most.
 * @returns {void} Local update only; a queued beacon is not a persistence acknowledgement.
 */
export function flushProgress(setId, at, duration) {
  held.progress.set(setId, { at, duration: duration ?? null, updatedAt: Date.now() });
  changed();
  try {
    const body = new Blob([JSON.stringify({ at, duration })], { type: "application/json" });
    if (navigator.sendBeacon(under(`/progress/${encodeURIComponent(setId)}`), body)) return;
  } catch {
    // Falls through to the ordinary write, which may still make it.
  }
  void write(under(`/progress/${encodeURIComponent(setId)}`), "PUT", { at, duration });
}

/** Forgets a position locally and persists best-effort. @returns {void} */
export function clearProgress(setId) {
  held.progress.delete(setId);
  void write(under(`/progress/${encodeURIComponent(setId)}`), "DELETE");
  changed();
}

export const isWatchlisted = (setId) => held.watchlist.has(setId);
export const watchlist = () => [...held.watchlist];

/** Updates the local watchlist and persists best-effort. @returns {void} */
export function setWatchlisted(setId, listed) {
  if (listed) held.watchlist.add(setId);
  else held.watchlist.delete(setId);
  void write(under(`/watchlist/${encodeURIComponent(setId)}`), listed ? "PUT" : "DELETE");
  changed();
}

export const isWatched = (setId) => held.watched.has(setId);

/**
 * When `setId` was finished, or `null`.
 *
 * The other half of `inProgress`'s `updatedAt`: between them they say when a
 * title was last touched, whether the viewer stopped in the middle of it or
 * reached the end. A show ranked without this one falls to the bottom of
 * the start page the moment its episode is finished.
 */
export const watchedAt = (setId) => held.watched.get(setId) ?? null;

/**
 * Records that a title reached its end.
 *
 * Called from the same branch that clears the position, because a finished
 * title has no resume point and this is the only thing left that remembers
 * it happened.
 * @returns {void} Local update only; persistence is best-effort.
 */
export function setWatched(setId, finished) {
  if (finished) held.watched.set(setId, Date.now());
  else held.watched.delete(setId);
  void write(under(`/watched/${encodeURIComponent(setId)}`), finished ? "PUT" : "DELETE", finished ? {} : undefined);
  changed();
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
    changed();
  } catch {
    // A player that cannot ask simply has an empty shelf, which is the same
    // thing it has before anything is marked.
  }
}

export const isKids = (setId) => held.kids.has(setId);
export const kids = () => [...held.kids];

/** Updates the shared local Kids mark and persists best-effort. @returns {void} */
export function setKids(setId, marked) {
  if (marked) held.kids.add(setId);
  else held.kids.delete(setId);
  void write(`/api/kids/${encodeURIComponent(setId)}`, marked ? "PUT" : "DELETE", marked ? {} : undefined);
  changed();
}

export const collections = () => held.collections;

/**
 * @returns {Promise<{id: string, name: string, items: string[]}|null>}
 * The acknowledged list, or null on failure or if the selected profile changed.
 */
export async function createCollection(name) {
  const selection = profileSelection;
  const asked = held.profileId;
  const made = await createRecord(under("/collections"), name);
  if (!made || selection !== profileSelection || held.profileId !== asked) return null;
  held.collections.push(made);
  changed();
  return made;
}

/** @returns {Promise<boolean>} Whether the server acknowledged the rename. */
export async function renameCollection(id, name) {
  if (!(await write(under(`/collections/${encodeURIComponent(id)}`), "PATCH", { name }))) return false;
  const list = held.collections.find((entry) => entry.id === id);
  if (list) list.name = name;
  changed();
  return true;
}

/** @returns {Promise<boolean>} Whether the server acknowledged deletion. */
export async function deleteCollection(id) {
  if (!(await write(under(`/collections/${encodeURIComponent(id)}`), "DELETE"))) return false;
  held.collections = held.collections.filter((entry) => entry.id !== id);
  changed();
  return true;
}

/** Updates local membership when the list exists and persists best-effort. @returns {void} */
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
  changed();
}

/**
 * What a viewer chose for this show, or `null` if they never did.
 *
 * **Always a hint, never an instruction.** A remembered audio track can point
 * at a stream a re-uploaded file no longer has, and a remembered subtitle
 * language at one that was dropped. Every caller checks the answer against
 * what the file actually offers, and a choice that no longer applies is
 * quietly ignored — a title that will not open because of a preference would
 * be far worse than a title that opens in the wrong language.
 */
export function preferenceOf(scope, name) {
  if (scope === null) return null;
  return held.preferences.get(preferenceKey(scope, name)) ?? null;
}

/** Remembers a choice locally and persists best-effort. An empty value forgets it. @returns {void} */
export function setPreference(scope, name, value) {
  if (scope === null || held.profileId === null) return;
  const held_ = value === null || value === undefined ? "" : String(value);
  if (held_ === "") held.preferences.delete(preferenceKey(scope, name));
  else held.preferences.set(preferenceKey(scope, name), held_);
  void write(under("/preferences"), "PUT", { scope, name, value: held_ });
}

/**
 * The two parts as one map key.
 *
 * Joined on a NUL, which is the one byte neither a show's name nor a name
 * this player chose can contain — `show:A` + `bc` and `show:Ab` + `c` would
 * otherwise be the same preference.
 */
function preferenceKey(scope, name) {
  return `${scope}\u0000${name}`;
}
