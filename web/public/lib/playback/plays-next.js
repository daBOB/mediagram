/**
 * What plays after a title, and what the server fetches ahead of it.
 *
 * One answer for both, so what is fetched ahead is always what would play
 * next. A hand-built list plays on in its own order; anything else plays on
 * through the show, course or documentary collection it belongs to.
 *
 * Only a series episode preloads, and only the next two: not lessons, not
 * lists — the rule the preload was built to — and documentaries were never
 * added to it. The server fetches in the background from a flood-limited
 * account, so widening this is its own decision, measured first;
 * `POST /api/preload` keeps at most two, and only episodes, anyway.
 */

import { nextAfter, nextInQueue } from "../library.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 */

/**
 * @param {import("../library.js").Library} library
 * @param {CatalogSet} set the title being opened
 * @param {CatalogSet[]|null} queue the list it was played from, if any
 * @returns {{next: CatalogSet|null, preload: string[]}}
 */
export function playsNext(library, set, queue) {
  if (queue) return { next: nextInQueue(queue, set.setId), preload: [] };
  const collection = [...library.series, ...library.anime.collections, ...library.tutorials, ...library.documentaries.collections].find(
    (entry) => entry.name === set.show,
  );
  if (!collection) return { next: null, preload: [] };
  const next = nextAfter(collection, set.setId);
  if (set.kind !== "ep" || !next) return { next, preload: [] };
  const after = nextAfter(collection, next.setId);
  return { next, preload: after ? [next.setId, after.setId] : [next.setId] };
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
