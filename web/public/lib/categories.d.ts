/**
 * Types for the row rule `categories.js` derives.
 *
 * The module is plain JavaScript because the browser loads it directly; this
 * declares its shape so the tests get the contract without a second copy of
 * the logic — the same arrangement `library.d.ts` has.
 */

export const OTHER: string;

export interface CategoryRow<T> {
  title: string;
  units: T[];
}

export function categoryRows<T>(units: T[], categoryOf: (unit: T) => string | null): CategoryRow<T>[];
