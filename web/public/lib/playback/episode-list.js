/**
 * What the episode sidebar lists: the open title's run, grouped the way its
 * own page groups it.
 *
 * A show is its seasons. A course or a documentary collection is its folders
 * that hold lessons, in the order the course page walks them — or one list,
 * when it has no folders. Built from the same collection `playsNext` walks, so
 * the sidebar and ⏮/⏭ cannot disagree about what is in the run.
 */

import { levelEntries } from "../library.js";

/**
 * @typedef {import("../library.js").CatalogSet} CatalogSet
 * @typedef {import("../library.js").Division} Division
 * @typedef {{title: string, season: number|null, items: CatalogSet[]}} EpisodeGroup
 */

/** Every folder under `level` that holds something playable, each before the ones below it. */
function groupsUnder(level, into) {
  const entries = levelEntries(level);
  const items = entries.filter((entry) => entry.kind === "lesson").map((entry) => entry.set);
  if (items.length > 0) into.push({ title: level.title, season: level.season, items });
  for (const entry of entries) {
    if (entry.kind === "folder") groupsUnder(entry.division, into);
  }
  return into;
}

/**
 * An episode the index could not place in a season. Its group goes after
 * every season, where a viewer looks for what does not fit, rather than first,
 * where sorting by name would put "Episodes".
 * @param {EpisodeGroup} group
 */
function unplaced(group) {
  return group.season == null && group.items.some((set) => set.kind === "ep");
}

/**
 * @param {{name: string, divisions: Division[]}|null} collection the run, or `null` for a title with none
 * @param {string} currentId the title playing
 * @returns {{groups: EpisodeGroup[], at: number}|null} `at` is the group holding
 *   `currentId`, or the first when the run does not hold it.
 */
export function episodeGroups(collection, currentId) {
  if (!collection) return null;
  const found = groupsUnder({ title: collection.name, season: null, items: [], children: collection.divisions }, []);
  const groups = [...found.filter((group) => !unplaced(group)), ...found.filter(unplaced)];
  if (groups.length === 0) return null;
  const at = groups.findIndex((group) => group.items.some((set) => set.setId === currentId));
  return { groups, at: Math.max(0, at) };
}
