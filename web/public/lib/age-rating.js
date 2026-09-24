/**
 * Age ratings, and what they decide about the Kids shelf.
 *
 * The rating is TMDB's for the library's country — an FSK in Germany — carried
 * on every catalog row as `fsk` (`"12"`), or `null` when the title has none.
 * The rules, as the household chose them:
 *
 *  - Rated at or below `KIDS_AGE_LIMIT`: on the Kids shelf by itself. Nobody
 *    has to mark it, and a mark cannot take it off — the rating decides.
 *  - Rated above it: never on the Kids shelf. Marking it is refused, and a
 *    mark made before ratings were recorded no longer counts.
 *  - Unrated: only what someone marked by hand, exactly as before ratings.
 *
 * Pure, so the rules are tested without a browser; the shelf and the player's
 * Kids button only ask.
 */

import { firstItemOf } from "./library.js";

/** The oldest rating that is still for kids. FSK 12 and younger. */
export const KIDS_AGE_LIMIT = 12;

/**
 * The rating as an age, or `null` when there is none that reads as one.
 * FSK is always a bare number; a letter rating from another country is not an
 * age this rule can compare, so it counts as unrated.
 */
export function ageOf(set) {
  const text = String(set?.fsk ?? "").trim();
  return /^\d{1,2}$/.test(text) ? Number(text) : null;
}

/** `"FSK 12"`, or `null` for an unrated title. */
export function ageLabel(set) {
  const age = ageOf(set);
  return age === null ? null : `FSK ${age}`;
}

/** `"safe"`, `"unsafe"`, or `"unrated"` — which of the three rules applies. */
export function kidsVerdict(set) {
  const age = ageOf(set);
  if (age === null) return "unrated";
  return age <= KIDS_AGE_LIMIT ? "safe" : "unsafe";
}

/**
 * What the Kids shelf holds.
 *
 * Films and series rated for kids, then whatever was marked by hand and is
 * not already there — minus anything rated too old, however it was marked. A
 * series is rated as a show, so its first episode answers for all of it, as
 * it does for its genres; a hand-marked episode of an unrated show is listed
 * on its own.
 */
export function kidsShelf(library, marked) {
  const films = library.movies.filter((set) => kidsVerdict(set) === "safe");
  const series = library.series.filter(
    (collection) => kidsVerdict(firstItemOf(collection.divisions)) === "safe",
  );
  const onShelf = new Set(films.map((set) => set.setId));
  const safeShows = new Set(series.map((collection) => collection.name));
  const byHand = marked.filter(
    (set) =>
      kidsVerdict(set) === "unrated" && !onShelf.has(set.setId) && !(set.show && safeShows.has(set.show)),
  );
  return { films, series, byHand };
}

/**
 * The catalog a kids profile sees: rated for kids, or unrated and marked by
 * hand. Applied once to the whole catalog, so every shelf, search and reel
 * built from it agrees. A rating decides on its own — a hand mark on a title
 * rated too old does not let it through, as on the Kids shelf.
 * @param {import("./library.js").CatalogSet[]} sets
 * @param {Set<string>} marked set ids marked for Kids by hand
 */
export function forKidsProfile(sets, marked) {
  return sets.filter((set) => {
    const verdict = kidsVerdict(set);
    return verdict === "safe" || (verdict === "unrated" && marked.has(set.setId));
  });
}
