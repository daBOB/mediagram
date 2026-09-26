/**
 * "Similar" on a title's page, ranked from what the catalog already knows:
 * the same franchise first, then the most genres in common, then the more
 * popular. Unwatched titles come before watched ones — a recommendation of
 * something already seen is a reminder, not a suggestion.
 *
 * Pure: the page passes the candidates and a watched test.
 */

import { genresOf } from "./genres.js";

const LIMIT = 12;

/**
 * @template {{ setId: string, genres?: string[]|null, popularity?: number|null, collectionId?: number|null }} T
 * @param {T} title the page's title (a film, or a show's first episode)
 * @param {T[]} candidates others of the same kind; `title` itself is skipped
 * @param {(item: T) => boolean} seen whether the viewer has watched it
 * @returns {T[]}
 */
export function similarTo(title, candidates, seen = () => false) {
  const own = new Set(genresOf(title));
  const franchise = title.collectionId ?? null;
  return candidates
    .filter((item) => item.setId !== title.setId)
    .map((item) => ({
      item,
      same: franchise !== null && item.collectionId === franchise ? 1 : 0,
      shared: genresOf(item).filter((name) => own.has(name)).length,
      seen: seen(item) ? 1 : 0,
    }))
    .filter((row) => row.same > 0 || row.shared > 0)
    .sort((a, b) =>
      b.same - a.same
      || a.seen - b.seen
      || b.shared - a.shared
      || (b.item.popularity ?? 0) - (a.item.popularity ?? 0))
    .slice(0, LIMIT)
    .map((row) => row.item);
}
