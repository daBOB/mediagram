/**
 * Reconciling what several devices say about the same viewing.
 *
 * Every mistake here is silent. A merge that picks the older of two positions
 * loses an evening's watching and reports nothing; one that resurrects a
 * finished title puts it back on the Continue shelf for ever. So this is pure,
 * it is tested before anything is ever sent, and the rules are written down.
 *
 * **Last writer wins, per row.** Not per device and not per document: the
 * record for *one title* with the highest `updatedAt` is the one that counts.
 * Watching S1E4 on the phone and S1E9 on the laptop leaves both correct.
 *
 * **`watched` is the tombstone for `progress`.** Finishing a title deletes its
 * position and writes a completion at the same moment — `clearProgress` and
 * `setWatched` are called together, and the v4 migration exists because
 * finishing would otherwise erase every trace. So a device that has never
 * heard of the completion still holds a position, and merging naively would
 * hand it back and put a finished film on the Continue shelf. A completion at
 * least as new as a position therefore beats it. This is what lets removals
 * work at all without tombstone rows in the schema.
 */

import {
  normalName,
  type CollectionRow,
  type ListRow,
  type ProgressRow,
  type SyncRecord,
  type WatchedRow,
} from "./sync-record";

/** Everything the devices agree on, once they have been reconciled. */
export interface MergedProfile {
  /** The normalised name, which is what identifies a viewer across machines. */
  name: string;
  /**
   * The name as somebody actually typed it.
   *
   * Carried separately because the identity is normalised and a name is not:
   * a machine meeting a viewer for the first time creates a local profile,
   * and creating it from the identity would greet them as "andré". The first
   * spelling seen wins, which is arbitrary between "André" and "ANDRÉ" and
   * right in the only case that matters — there being just one.
   */
  displayName: string;
  progress: ProgressRow[];
  watched: WatchedRow[];
  /** `mergeStates` always fills these; optional only so a hand-built
   * `MergedState` — a test, or `importMerged`'s caller — need not repeat an
   * empty array for every kind it has nothing to say about. */
  watchlist?: ListRow[];
  collections?: CollectionRow[];
}

export interface MergedState {
  profiles: MergedProfile[];
  /** Not scoped to a profile — see `schema.ts` on why `kids` alone has none. */
  kids?: ListRow[];
}

/**
 * Merges every device's document into one answer.
 *
 * Order-independent by construction: merging A then B gives what merging B
 * then A gives, which matters because devices see each other's documents in
 * whatever order Telegram hands them over.
 */
export function mergeStates(records: SyncRecord[]): MergedState {
  /** Per viewer, the best row seen so far for each title. */
  const byViewer = new Map<
    string,
    {
      displayName: string;
      /** The device `displayName` was taken from, for the tie-break below. */
      nameFrom: string;
      progress: Map<string, Held<ProgressRow>>;
      watched: Map<string, Held<WatchedRow>>;
      watchlist: Map<string, Held<ListRow>>;
      collections: Map<string, Held<CollectionRow>>;
    }
  >();
  // Kids sits at the top level, not per viewer: marking a title as a
  // child's is a fact about the title, the same reason it has no profile in
  // `schema.ts`.
  const kids = new Map<string, Held<ListRow>>();

  for (const record of records) {
    const device = typeof record?.device === "string" ? record.device : "";
    for (const row of record?.kids ?? []) keep(kids, row.setId, row, device);

    for (const profile of record?.profiles ?? []) {
      const name = normalName(profile.name);
      if (name === null) continue;

      let held = byViewer.get(name);
      if (held === undefined) {
        held = {
          displayName: profile.name.trim(),
          nameFrom: device,
          progress: new Map(),
          watched: new Map(),
          watchlist: new Map(),
          collections: new Map(),
        };
        byViewer.set(name, held);
      } else if (device > held.nameFrom) {
        // One viewer typed two ways on two devices. The spelling shown is
        // decided by device id, as a tie between rows is, so it does not
        // depend on which document Telegram happened to hand over first.
        held.displayName = profile.name.trim();
        held.nameFrom = device;
      }

      for (const row of profile.progress ?? []) keep(held.progress, row.setId, row, device);
      for (const row of profile.watched ?? []) keep(held.watched, row.setId, row, device);
      for (const row of profile.watchlist ?? []) keep(held.watchlist, row.setId, row, device);
      // A collection is one row on the wire — the whole list, `removed`
      // included — so it is kept by its id rather than reconciled item by
      // item: two devices editing the same list within a merge round have
      // the later edit win outright, name and membership together.
      for (const row of profile.collections ?? []) keep(held.collections, row.id, row, device);
    }
  }

  const profiles: MergedProfile[] = [];
  for (const [name, held] of byViewer) {
    const watched = [...held.watched.values()].map((one) => one.row);
    const finishedAt = new Map(watched.map((row) => [row.setId, row.updatedAt]));

    const progress = [...held.progress.values()]
      .map((one) => one.row)
      // The tombstone rule. `>=` rather than `>`: the two writes happen in one
      // moment and can carry the same millisecond, and in a tie the completion
      // is the later intention — a viewer who finished a title did not also
      // mean to leave a position in it.
      .filter((row) => (finishedAt.get(row.setId) ?? -1) < row.updatedAt);

    profiles.push({
      name,
      displayName: held.displayName,
      progress,
      watched,
      watchlist: [...held.watchlist.values()].map((one) => one.row),
      collections: [...held.collections.values()].map((one) => one.row),
    });
  }
  return { profiles, kids: [...kids.values()].map((one) => one.row) };
}

interface Held<T> {
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
function keep<T extends { updatedAt: number }>(
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
