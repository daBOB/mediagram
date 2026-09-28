/**
 * Types for the department split `departments.js` derives.
 *
 * The module itself is plain JavaScript because the browser loads it
 * directly; this declares its shape the way `library.d.ts` does for
 * `library.js`.
 */

import type { CatalogSet, Collection, Library } from "./library.js";

export interface AnimeLibrary {
  collections: Collection[];
  singles: CatalogSet[];
}

/** Anime series (seasons kept) and anime films, split out of a flat catalog. */
export function groupAnime(sets: CatalogSet[]): AnimeLibrary;

/** How many titles — shows and films alike — an anime library holds. */
export function countAnime(anime: AnimeLibrary): number;

/** Every film, plain and anime alike. */
export function everyFilm(library: Library): CatalogSet[];

/** Every show, plain and anime alike. */
export function everyShow(library: Library): Collection[];

/** Which section a show by this name actually lives on. */
export function sectionForShow(library: Library, section: string, name: string): string;

/** Splits a catalog into every department the nav bar offers. */
export function groupDepartments(sets: CatalogSet[]): Library;
