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
  quality: string | null;
  hdr: string | null;
  /** File-track languages as JSON array strings, or null when unrecorded. */
  alang: string | null;
  slang: string | null;
  duration: number | null;
  total: number;
  partCount: number;
  /** When the uploader added this, in epoch milliseconds. Arrival, not release. */
  addedAt: number;
  /** Browser presentation fields added by the catalog/search projection. */
  poster: string | null;
  seasonPoster: string | null;
  showKey: string | null;
  genres: string[];
  fsk: string | null;
  offline: boolean;
  hasSummary: boolean;
  subtitles: string[];
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
  /** Lessons, not counting documents. */
  count: number;
  /** Documents anywhere in the collection. */
  documents: number;
}

export interface Library {
  movies: CatalogSet[];
  series: Collection[];
  tutorials: Collection[];
}

export function groupLibrary(sets: CatalogSet[]): Library;

/** The first *playable* set under these divisions, in display order. */
export function firstItemOf(divisions: Division[]): CatalogSet | null;

/** How many lessons sit under `division`, at whatever depth. */
export function lessonsUnder(division: Pick<Division, "items" | "children">): number;

/** How many documents sit under `division`, at whatever depth. */
export function documentsUnder(division: Pick<Division, "items" | "children">): number;

/** Both content counts under this division, including nested folders. */
export function countsUnder(division: Pick<Division, "items" | "children">): {
  lessons: number;
  documents: number;
};

/** True for a set that is a document rather than something to play. */
export function isDocument(set: CatalogSet): boolean;

/** One level's lessons and folders, in the order the course puts them. */
export type LevelEntry =
  | { kind: "lesson"; set: CatalogSet; order: number | null }
  | { kind: "document"; set: CatalogSet; order: number | null }
  | { kind: "folder"; division: Division; order: number | null };

export function levelEntries(level: Pick<Division, "items" | "children">): LevelEntry[];

/** Every playable set in a collection, in the order its pages walk them. */
export function flattenCollection(collection: Pick<Collection, "divisions">): CatalogSet[];

/** What follows `setId` in its collection, or `null` at the end of one. */
export function nextAfter(collection: Collection, setId: string): CatalogSet | null;

/** The set after `setId`, or null when the set is absent or last. */
export function nextInQueue<T extends { setId: string }>(sets: readonly T[], setId: string): T | null;

/** The named division, or the title-less root for an empty trail. */
export function divisionAt(
  divisions: Division[],
  names: string[],
): (Omit<Division, "title"> & { title: string | null }) | null;
