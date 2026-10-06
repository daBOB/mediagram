/**
 * The hand-set category on a course, a documentary collection or a
 * standalone documentary — the same unit each department page draws as one
 * card, and the same unit whose custom artwork already lives under
 * `posterKeyFor(kind, null, show ?? title)`.
 *
 * `categoryKey` is the TS twin of `mlib_spec::category_key`, held to the
 * fixture the Rust side runs too (`web/test/fixtures/categories/keys.json`,
 * checked here by `category-keys.test.ts`). The row rule built from a
 * unit's category is a separate, plain-JS module (`categories.js`), held to
 * its own fixture (`rows.json`) — this module only says which category a
 * unit carries, not how a page groups by it.
 */

import type { Database } from "bun:sqlite";
import { hasTable } from "../catalog";
import { posterKeyFor } from "./posters";

/** Department for a course or one of its documents. */
export const TUTORIALS = "tutorials";
/** Department for a documentary collection or a standalone documentary. */
export const DOCUMENTARIES = "documentaries";

/**
 * The `(department, item_key)` a category on this unit is filed under, or
 * `null` when `kind` names no unit at all (a film or an episode) or
 * `show`/`title` slugs to nothing.
 */
export function categoryKey(kind: string, show: string | null, title: string | null): [string, string] | null {
  const department = kind === "tut" || kind === "doc" ? TUTORIALS : kind === "docu" ? DOCUMENTARIES : null;
  if (department === null) return null;
  const itemKey = posterKeyFor(kind, null, show ?? title);
  return itemKey === null ? null : [department, itemKey];
}

/** The map key `categoryNames` and `categoryOf` share for one unit. */
function mapKey(department: string, itemKey: string): string {
  return `${department}/${itemKey}`;
}

/**
 * Every hand-set category already filed, by `(department, item_key)`.
 *
 * `NULL` rows ("cleared") are dropped by the query itself: a map with no
 * entry for a unit already means "uncategorised", the same reading a
 * cleared row is for. Absent on an index written before v12.
 */
export function categoryNames(db: Database): Map<string, string> {
  const names = new Map<string, string>();
  if (!hasTable(db, "categories")) return names;
  const rows = db
    .query("SELECT department, item_key, category FROM categories WHERE category IS NOT NULL")
    .all() as { department: string; item_key: string; category: string }[];
  for (const row of rows) names.set(mapKey(row.department, row.item_key), row.category);
  return names;
}

/** The category on this set's unit, or `null` for a film, an episode, or an unfiled unit. */
export function categoryOf(
  names: Map<string, string>,
  kind: string,
  show: string | null,
  title: string | null,
): string | null {
  const key = categoryKey(kind, show, title);
  return key === null ? null : names.get(mapKey(...key)) ?? null;
}
