/**
 * The tie-break every kept row goes through. Split out of `merge.ts` to
 * keep it under the line limit; `mergeStates` is the only caller. The same
 * split `crates/mediagram-core/src/state/merge/tie_break.rs` makes.
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
 * disagreement back and forth for ever.
 */
export function keep<T extends { updatedAt: number }>(
  into: Map<string, Held<T>>,
  key: string,
  row: T,
  device: string,
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
  if (row.updatedAt === standing.row.updatedAt && device > standing.device) {
    into.set(key, { row, device });
  }
}
