/**
 * Types for the rows `home-shelves.js` derives.
 *
 * The module is plain JavaScript because the browser loads it directly; this
 * declares its shape so the tests get the contract without a second copy of
 * the logic — the same arrangement `library.d.ts` has.
 */

import type { CatalogSet, Collection, Library } from "../library.js";

/** One show or course, and the one episode of it to offer. */
export interface NextUpEntry {
  /** What a card plays: the episode in progress, or the one after. */
  set: CatalogSet;
  collection: Collection;
  /** True when `set` is part-watched, false when it is the one that follows. */
  resume: boolean;
  /** When this show was last watched, by position or by completion. */
  touchedAt: number;
}

export interface HomeShelves {
  /** Half-watched titles that `nextUp` is not already showing. */
  continues: CatalogSet[];
  nextUp: NextUpEntry[];
  latestMovies: CatalogSet[];
  latestSeries: Collection[];
  latestCourses: Collection[];
  /** How much is behind each row, for its heading: the whole shelf, not the six. */
  totals: {
    continues: number;
    nextUp: number;
    latestMovies: number;
    latestSeries: number;
    latestCourses: number;
  };
}

/** One recorded position, as the watch store holds it. */
export interface ProgressRow {
  setId: string;
  at: number;
  duration: number | null;
  updatedAt: number;
}

export const SHELF_LIMIT: number;

export function homeShelves(from: {
  library: Library;
  byId: Map<string, CatalogSet>;
  /** The store's rows, newest first. */
  progress?: ProgressRow[];
  /** When a set was finished, or `null`. */
  watchedAt?: (setId: string) => number | null;
  limit?: number;
}): HomeShelves;
