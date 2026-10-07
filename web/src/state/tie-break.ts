/**
 * The tie-break every kept row goes through; `mergeStates`, `mergeStats` and
 * `mergeRoles` are the callers. `crates/mediagram-core/src/state/merge/tie_break.rs`
 * is its Rust twin.
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

/**
 * Keeps whichever of two PINs should win — the core's `keep_pin`. A proven
 * PIN (set by someone who knew the one before, or the admin's reset) beats
 * any first PIN; between proven ones the newest wins, as `keep` would have
 * it; between first ones the *oldest* does, so a first PIN set later on a
 * copy that never heard of an earlier one — a kid's offline tablet — cannot
 * replace it. Ties by device id, as `keep`'s do.
 */
export function keepPin<T extends Ranked>(into: Map<string, Held<T>>, key: string, row: T, device: string): void {
  const standing = into.get(key);
  if (standing === undefined || pinBeats(row, device, standing)) into.set(key, { row, device });
}

/** What `pinBeats` reads of a PIN. */
export interface Ranked {
  updatedAt: number;
  proven?: true;
}

/** The order `keepPin` keeps, and an import takes a merged PIN by. */
export function pinBeats(row: Ranked, device: string, standing: Held<Ranked>): boolean {
  const proven = row.proven === true;
  if (proven !== (standing.row.proven === true)) return proven;
  if (row.updatedAt !== standing.row.updatedAt) return (row.updatedAt > standing.row.updatedAt) === proven;
  return device > standing.device;
}
