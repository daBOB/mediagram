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

/**
 * One division of a collection: a season, a chapter, or a folder.
 *
 * `items` and `children` can both be non-empty: a chapter that holds lessons
 * beside a subfolder must show both, since the lessons are not in it.
 */
export interface Division {
  /** This folder's own name, not the path leading to it. */
  title: string;
  /** Present only when the division is numbered rather than named. */
  season: number | null;
  items: CatalogSet[];
  children: Division[];
}

export interface Collection {
  name: string;
  /** The top-level folders; the rest of the course hangs off them. */
  divisions: Division[];
  /** Folders that actually hold lessons, however deep they sit. */
  chapters: number;
  count: number;
}

export interface Library {
  movies: CatalogSet[];
  series: Collection[];
  tutorials: Collection[];
}

export function groupLibrary(sets: CatalogSet[]): Library;

/** The first set anywhere under these divisions, in display order. */
export function firstItemOf(divisions: Division[]): CatalogSet | null;
