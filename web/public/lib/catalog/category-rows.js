/**
 * Category rows for Tutorials and Documentaries: `categoryRows` (from
 * `categories.js`) applied to each department's own units, drawn as the
 * strips `department-pages.js` appends after Continue.
 *
 * Kept apart from `department-pages.js` because a course and a documentary
 * unit are shaped nothing alike — a course's category sits on its first
 * lesson, a documentary collection's the same way, but a standalone
 * documentary carries its own directly — and mixing that lookup into the
 * page function would bury the one thing this module is for.
 */

import { categoryRows } from "../categories.js";
import { firstItemOf } from "../library.js";
import { collectionGrid, movieGrid } from "./shelf-view.js";
import { GRID } from "./shelf-mode.js";
import { deptRow } from "./department-hero.js";

/**
 * @param {import("../library.js").Collection[]} shows every course, in department order
 * @param {(name: string) => void} open
 * @returns {HTMLElement[]} one `deptRow` per category, "Other" last
 */
export function courseCategoryRows(shows, open) {
  const rows = categoryRows(shows, (course) => firstItemOf(course.divisions)?.category ?? null);
  return rows.map(({ title, units }) => deptRow(title, collectionGrid("tutorials", units, open, { mode: GRID, strip: true })));
}

/** True for a documentary collection, as opposed to a standalone documentary set. */
function isCollection(unit) {
  return "divisions" in unit;
}

/**
 * @param {import("../library.js").Collection[]} groups documentary collections, in folder order
 * @param {import("../library.js").CatalogSet[]} singles standalone documentaries, in their own order
 * @param {(name: string) => void} openGroup
 * @param {(set: import("../library.js").CatalogSet) => void} play
 * @returns {HTMLElement[]} one `deptRow` per category, "Other" last; each strip
 *   holds that row's collection cards followed by its single cards
 */
export function documentaryCategoryRows(groups, singles, openGroup, play) {
  const categoryOf = (unit) => (isCollection(unit) ? firstItemOf(unit.divisions)?.category ?? null : unit.category ?? null);
  const rows = categoryRows([...groups, ...singles], categoryOf);
  return rows.map(({ title, units }) => {
    const strip = collectionGrid("documentaries", units.filter(isCollection), openGroup, { mode: GRID, strip: true });
    strip.append(...movieGrid(units.filter((unit) => !isCollection(unit)), play, { mode: GRID, strip: true }).childNodes);
    return deptRow(title, strip);
  });
}
