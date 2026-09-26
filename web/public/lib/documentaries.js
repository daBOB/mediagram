/**
 * Documentaries split the way `tutorials` groups, and then split further.
 *
 * A folder of documentaries — "Terra X" — groups by `show`/`cid` exactly as a
 * course does, so `collections` from `library.js` builds it unchanged. A
 * documentary uploaded on its own has no collection, and giving it one with a
 * shared fallback name would file every single documentary in the library
 * under one fake collection nobody asked for; it stays a plain set instead,
 * for the department page to show in its own row.
 */

import { byTitle, collections } from "./library.js";

/**
 * @param {import("./library.js").CatalogSet[]} sets every `docu` set
 * @returns {import("./documentaries.js").DocumentaryLibrary}
 */
export function groupDocumentaries(sets) {
  const grouped = sets.filter((set) => set.show);
  const singles = sets.filter((set) => !set.show);
  return { collections: collections(grouped, ""), singles: [...singles].sort(byTitle) };
}

/** How many documentaries this library holds, singles and collected alike. */
export function countDocumentaries({ collections: found, singles }) {
  return found.reduce((n, collection) => n + collection.count, 0) + singles.length;
}
