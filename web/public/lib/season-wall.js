/**
 * What a show's page opens on: one plate per season.
 *
 * A show of several seasons is a wall of them, each with its own artwork,
 * and opening one lists its episodes — the way a course's folders are doors.
 * A show of one season goes straight to its episodes: a wall of one plate is
 * a click that says nothing.
 *
 * No DOM here; the plate itself is `shelf-view.js`'s card, so a season looks
 * like everything else on a wall.
 */

import { countOf } from "./format.js";

/** Whether a show's page is a wall of seasons rather than its episodes. */
export function hasSeasonWall(collection) {
  return collection.divisions.length > 1;
}

/**
 * The plate for one season.
 *
 * The season's own artwork when TMDB has one, else the show's — every
 * episode carries both, so the first is enough to ask. A season with neither
 * falls to initials, as any plate does.
 */
export function seasonPlate(division) {
  const first = division.items[0] ?? null;
  return {
    name: division.title,
    meta: countOf(division.items.length, "episode"),
    poster: first?.seasonPoster ?? first?.poster ?? null,
  };
}

/** The season a page's trail names, by the title its plate carried. */
export function seasonNamed(collection, title) {
  return collection.divisions.find((division) => division.title === title) ?? null;
}
