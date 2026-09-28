/**
 * The catalog split into the shelves the nav bar actually offers.
 *
 * `groupLibrary` builds the three plain departments; this pulls anime and
 * documentaries out ahead of it, the way a magazine pulls a special section
 * out of its general listings before typesetting the rest — a title never
 * sits on two shelves at once. What each pulled-out section itself groups
 * into is `documentaries.js`'s job and this module's `groupAnime` below.
 */

import { byTitle, collections, groupLibrary } from "./library.js";
import { groupDocumentaries } from "./documentaries.js";

/**
 * @typedef {import("./library.js").CatalogSet} CatalogSet
 * @typedef {{ collections: import("./library.js").Collection[], singles: CatalogSet[] }} AnimeLibrary
 */

/**
 * Anime series (seasons kept, grouped by show) and anime films — kept as one
 * department rather than filed among the plain shows and films, so a viewer
 * who wants Japanese animation finds all of it in one place, and one who
 * does not finds none of it mixed into Movies or Series.
 * @param {CatalogSet[]} sets every set with `anime: true`
 * @returns {AnimeLibrary}
 */
export function groupAnime(sets) {
  const films = sets.filter((set) => set.kind === "movie");
  const episodes = sets.filter((set) => set.kind === "ep");
  return { collections: collections(episodes, "Unknown show"), singles: [...films].sort(byTitle) };
}

/** How many titles — shows and films alike — an anime library holds. */
export function countAnime({ collections: shows, singles }) {
  return shows.length + singles.length;
}

/**
 * Every title in the library, department views and all: what a lookup
 * (Similar, a person's page, the Genres index) searches across rather than
 * only what one department shelf shows.
 * @param {import("./library.js").Library} library
 */
export function everyFilm(library) {
  return [...library.movies, ...library.anime.singles];
}

/** Every show, plain and anime alike, by its first episode's facts. */
export function everyShow(library) {
  return [...library.series, ...library.anime.collections];
}

/**
 * Which section a show by this name actually lives on, for a caller that
 * only knows it as a plain series — every opener outside the Series and
 * Anime departments themselves still says `"series"`, since that was the
 * only section there was before Anime existed.
 *
 * A name already on the given section is left alone, so a live-action show
 * sharing a name with an anime one still opens the section it was asked for.
 * @param {import("./library.js").Library} library
 * @param {string} section
 * @param {string} name
 */
export function sectionForShow(library, section, name) {
  if (section !== "series") return section;
  if (library.series.some((show) => show.name === name)) return section;
  return library.anime.collections.some((show) => show.name === name) ? "anime" : section;
}

/**
 * Splits a catalog into every department the nav bar offers.
 *
 * Anime is pulled out first — same rule Documentaries already followed — so
 * `groupLibrary` never sees a title that belongs to either.
 * @param {CatalogSet[]} sets
 * @returns {import("./library.js").Library}
 */
export function groupDepartments(sets) {
  const anime = sets.filter((set) => set.anime);
  const rest = sets.filter((set) => !set.anime);
  const documentaries = groupDocumentaries(rest.filter((set) => set.kind === "docu"));
  return { ...groupLibrary(rest), documentaries, anime: groupAnime(anime) };
}
