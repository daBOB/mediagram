/**
 * Types for the shelves `library.js` derives.
 *
 * The module itself is plain JavaScript because the browser loads it
 * directly; this declares its shape so the tests and any future TypeScript
 * caller get the same contract without a second copy of the logic.
 */

export interface CatalogSet {
  setId: string;
  kind: string;
  title: string | null;
  show: string | null;
  chap: string | null;
  /** Folders within the collection, `/`-separated. See the caption spec. */
  path: string | null;
  season: number | null;
  episode: string | null;
  year: number | null;
  container: string;
  vcodec: string | null;
  acodec: string | null;
  duration: number | null;
  total: number;
  partCount: number;
}

/** One division of a collection: a season, a chapter, or a folder. */
export interface Division {
  key: string;
  title: string;
  /** Present only when the division is numbered rather than named. */
  season: number | null;
  items: CatalogSet[];
}

export interface Collection {
  name: string;
  seasons: Division[];
  count: number;
}

export interface Library {
  movies: CatalogSet[];
  series: Collection[];
  tutorials: Collection[];
}

export function groupLibrary(sets: CatalogSet[]): Library;
