/**
 * One row per hand-set category on a department's units, "Other" last.
 *
 * A pure grouping rule rather than a rendering concern, so it can be held to
 * one fixture (`web/test/fixtures/categories/rows.json`) shared with whatever
 * the caller ends up drawing — a Tutorials strip and a Documentaries strip
 * build very different DOM from the same rows.
 */

import { byTitle } from "./library.js";

/** The row every uncategorised unit falls into. */
export const OTHER = "Other";

/**
 * @template T
 * @param {T[]} units every unit of the department, in department order
 * @param {(unit: T) => string | null} categoryOf a unit's category, or `null`
 * @returns {{ title: string, units: T[] }[]} one row per distinct category
 *   found, "Other" last; `[]` when no unit is categorised, so the page reads
 *   exactly as it did before any unit was ever filed
 */
export function categoryRows(units, categoryOf) {
  if (!units.some((unit) => categoryOf(unit) !== null)) return [];
  const rows = new Map();
  for (const unit of units) {
    const title = categoryOf(unit) ?? OTHER;
    if (!rows.has(title)) rows.set(title, []);
    rows.get(title).push(unit);
  }
  return [...rows]
    .map(([title, members]) => ({ title, units: members }))
    .sort((a, b) => Number(a.title === OTHER) - Number(b.title === OTHER) || byTitle(a, b));
}
