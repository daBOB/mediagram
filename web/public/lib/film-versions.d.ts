/**
 * Types for `film-versions.js`, which the browser loads directly; the tests
 * and any TypeScript caller read the same contract from here.
 */

import type { CatalogSet } from "./library.js";

/** A stored position, as `watch-state.js` holds one. */
export interface ProgressRow {
  setId?: string;
  at: number;
  duration: number | null;
  updatedAt: number;
}

type ProgressOf = (setId: string) => ProgressRow | null;
type WatchedAt = (setId: string) => number | null;
type FilmOf = (setId: string) => CatalogSet | null;

export function cutOf(set: CatalogSet): string | null;
export function compareVersions(a: CatalogSet, b: CatalogSet): number;
export function groupFilms(sets: CatalogSet[]): CatalogSet[];
export function versionsOf(film: CatalogSet): CatalogSet[];
export function filmIndex(films: CatalogSet[]): Map<string, CatalogSet>;
export function versionLabel(set: CatalogSet): string;
export function filmPlayed(film: CatalogSet, watchedAt: WatchedAt): boolean;
export function versionPosition(version: CatalogSet, film: CatalogSet, progressOf: ProgressOf, watchedAt: WatchedAt): ProgressRow | null;
export function filmPosition(film: CatalogSet, progressOf: ProgressOf, watchedAt: WatchedAt): ProgressRow | null;
export function continueRows<R extends ProgressRow & { setId: string }>(rows: R[], filmOf: FilmOf, watchedAt: WatchedAt): R[];
export function asFilms<T extends { setId: string }>(items: T[], filmOf: FilmOf): Array<T | CatalogSet>;
