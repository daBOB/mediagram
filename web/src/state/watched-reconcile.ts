/**
 * Deciding, per title, whether a live `WatchedRow` or its `UnwatchedRow`
 * removal is the more recent fact, once `merge.ts`'s `keep` has already
 * picked the winner within each kind on its own.
 *
 * Split out of `merge.ts` to keep it under the line limit, and because this
 * is the one place the cross-kind rule lives: a tie goes to the removal, not
 * to a device-id tie-break. The two rows came from different devices making
 * unrelated claims about the same title, not two writes of the same kind
 * where a tie-break has to pick one consistently everywhere. Picking the
 * removal specifically, rather than either one arbitrarily-but-consistently,
 * is what makes an old device's unchanging live mark unable to outrun a
 * removal it will never understand — see `sync-record.ts`'s `UnwatchedRow`.
 */

import type { UnwatchedRow, WatchedRow } from "./sync-record";

export interface ReconciledWatched {
  watched: WatchedRow[];
  unwatched: UnwatchedRow[];
  /** Per title, the moment a `progress` row no newer than it is superseded —
   * a live mark's own time, or a removal's `lastFinishedAt`. */
  finishedAt: Map<string, number>;
}

export function reconcileWatched(
  watched: Map<string, WatchedRow>,
  unwatched: Map<string, UnwatchedRow>,
): ReconciledWatched {
  const outWatched: WatchedRow[] = [];
  const outUnwatched: UnwatchedRow[] = [];
  const finishedAt = new Map<string, number>();

  for (const setId of new Set([...watched.keys(), ...unwatched.keys()])) {
    const live = watched.get(setId);
    const removed = unwatched.get(setId);
    if (removed !== undefined && (live === undefined || removed.updatedAt >= live.updatedAt)) {
      outUnwatched.push(removed);
      finishedAt.set(setId, removed.lastFinishedAt);
    } else if (live !== undefined) {
      outWatched.push(live);
      finishedAt.set(setId, live.updatedAt);
    }
  }
  return { watched: outWatched, unwatched: outUnwatched, finishedAt };
}
