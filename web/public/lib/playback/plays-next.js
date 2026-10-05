/**
 * What plays after a title, what played before it, and what the server
 * fetches ahead of it.
 *
 * One answer for all three, so what is fetched ahead is always what would play
 * next and the step back walks the same run as the step on. A hand-built list
 * plays on in its own order; anything else plays on through the show, course
 * or documentary collection it belongs to.
 *
 * Only a series episode preloads, and only the next two: not lessons, not
 * lists — the rule the preload was built to — and documentaries were never
 * added to it. The server fetches in the background from a flood-limited
 * account, so widening this is its own decision, measured first;
 * `POST /api/preload` keeps at most two, and only episodes, anyway.
 */

import { flattenCollection, nextInQueue } from "../library.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 */

/**
 * The show, course or documentary collection `set` belongs to, or `null`.
 * @param {import("../library.js").Library} library
 * @param {CatalogSet} set
 */
export function collectionOf(library, set) {
  const every = [...library.series, ...library.anime.collections, ...library.tutorials, ...library.documentaries.collections];
  return every.find((entry) => entry.name === set.show) ?? null;
}

/**
 * What comes before `setId` in an ordered run, or `null` at its start — and
 * for an id the run does not hold, which has no neighbours to speak of.
 * @template {{setId: string}} T
 * @param {readonly T[]} sets
 * @param {string} setId
 * @returns {T|null}
 */
export function previousInQueue(sets, setId) {
  const at = sets.findIndex((set) => set.setId === setId);
  return at <= 0 ? null : sets[at - 1];
}

/**
 * `inRun` is whether there is a run at all. A film opened from a shelf has
 * none, and its player hides the step buttons rather than disabling them; the
 * first and last of a run disable one of the two instead.
 * @param {import("../library.js").Library} library
 * @param {CatalogSet} set the title being opened
 * @param {CatalogSet[]|null} queue the list it was played from, if any
 * @returns {{next: CatalogSet|null, previous: CatalogSet|null, inRun: boolean, preload: string[]}}
 */
export function playsNext(library, set, queue) {
  const collection = queue ? null : collectionOf(library, set);
  const run = queue ?? (collection ? flattenCollection(collection) : null);
  if (!run) return { next: null, previous: null, inRun: false, preload: [] };
  const next = nextInQueue(run, set.setId);
  const previous = previousInQueue(run, set.setId);
  if (queue || set.kind !== "ep" || !next) return { next, previous, inRun: true, preload: [] };
  const after = nextInQueue(run, next.setId);
  return { next, previous, inRun: true, preload: after ? [next.setId, after.setId] : [next.setId] };
}

/**
 * Asks the server to take `setIds` into its cache. Fire and forget: a player
 * with preload turned off answers 404, and either way the title plays the same.
 * @param {string[]} setIds
 */
export function requestPreload(setIds) {
  if (setIds.length === 0) return;
  fetch("/api/preload", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ setIds }),
  }).catch(() => {});
}
