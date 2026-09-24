/**
 * Genres: the links on a title's page, and the shelf each one opens.
 *
 * Pure, so the rules — what counts as tagged, what a score says — are tested
 * without a browser; `film-page.js` and the series header only place what this
 * returns.
 *
 * A genre is the provider's word in the library's language (`Komödie`,
 * `Krimi`), carried on every catalog row as `genres`. It is matched exactly as
 * stored: the uploader writes one spelling per genre, so there is nothing to
 * normalise, and a looser match would put titles on a shelf they were never
 * tagged for.
 */

import { firstItemOf } from "../library.js";

/** Where a genre's shelf lives. */
export function genreHash(name) {
  return `#/genre/${encodeURIComponent(name)}`;
}

/** A title's genres, whether or not the catalog recorded any. */
export function genresOf(set) {
  return Array.isArray(set?.genres) ? set.genres : [];
}

/**
 * Everything tagged `name`: the films, then the series.
 *
 * A series is tagged by its show, which every episode carries, so its first
 * episode answers for the whole show — the same one its shelf card is drawn
 * from.
 */
export function genreShelf(library, name) {
  const films = library.movies.filter((set) => genresOf(set).includes(name));
  const series = library.series.filter((collection) =>
    genresOf(firstItemOf(collection.divisions)).includes(name),
  );
  return { films, series };
}

/**
 * The provider's user score, as the page prints it.
 *
 * Nought is what an unrated title scores, not a score, so it says nothing —
 * the rule the series header already follows.
 */
export function scoreLabel(rating) {
  return typeof rating === "number" && rating > 0 ? `★ ${rating.toFixed(1)}` : null;
}
