/**
 * Types for the split `documentaries.js` derives.
 *
 * The module itself is plain JavaScript because the browser loads it
 * directly; this declares its shape the way `library.d.ts` does for
 * `library.js`.
 */

import type { CatalogSet, Collection } from "./library.js";

export interface DocumentaryLibrary {
  /** Folders such as "Terra X", grouped the way a course's chapters are. */
  collections: Collection[];
  /** A documentary uploaded on its own, with no collection to join. */
  singles: CatalogSet[];
}

export function groupDocumentaries(sets: CatalogSet[]): DocumentaryLibrary;

/** How many documentaries this library holds, singles and collected alike. */
export function countDocumentaries(library: DocumentaryLibrary): number;
