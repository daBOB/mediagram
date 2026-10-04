/**
 * The tie-break every kept row goes through. Split out of `merge.ts` to
 * keep it under the line limit; `mergeStates`, `mergeStats` and `mergeRoles`
 * are the callers. The same split `crates/mediagram-core/src/state/merge/tie_break.rs` makes.
 */

export interface Held<T> {
  row: T;
  device: string;
}

/**
 * Keeps whichever of two rows should win.
 *
 * A tie breaks on the device id — arbitrary, but *consistently* arbitrary,
 * which is the property that matters. Two machines merging the same pair of
 * documents have to reach the same answer, or they will push their
 * disagreement back and forth for ever. `rank`, when given, is asked first:
 * the higher rank wins a tie outright, so the order stays total.
 */
export function keep<T extends { updatedAt: number }>(
  into: Map<string, Held<T>>,
  key: string,
  row: T,
  device: string,
  rank: (row: T) => number = () => 0,
): void {
  const standing = into.get(key);
  if (standing === undefined) {
    into.set(key, { row, device });
    return;
  }
  if (row.updatedAt > standing.row.updatedAt) {
    into.set(key, { row, device });
    return;
  }
  if (row.updatedAt !== standing.row.updatedAt) return;
  const ahead = rank(row) - rank(standing.row);
  if (ahead > 0 || (ahead === 0 && device > standing.device)) into.set(key, { row, device });
}
