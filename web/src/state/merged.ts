/**
 * What `mergeStates` answers: every viewer the devices know of, reconciled.
 *
 * The shapes only, kept apart from `merge.ts` so its rules have the room.
 */

import type { CollectionRow, ListRow, ProgressRow, UnwatchedRow, WatchedRow } from "./sync-record";
import type { PreferenceRow } from "./preferences-record";
import type { StatsRows } from "./stats-record";
import type { MergedRoles } from "./roles-merge";

/** Everything the devices agree on, once they have been reconciled. Its role
 * keys — `admin`, `kidsAge`, `parent`, `pin` — are `roles-merge.ts`'s. */
export interface MergedProfile extends StatsRows, MergedRoles {
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
  /** A kids profile if any device's document says so; absent otherwise. */
  kids?: true;
  progress: ProgressRow[];
  watched: WatchedRow[];
  /** `mergeStates` always fills these; optional only so a hand-built
   * `MergedState` — a test, or `importMerged`'s caller — need not repeat an
   * empty array for every kind it has nothing to say about. */
  unwatched?: UnwatchedRow[];
  watchlist?: ListRow[];
  collections?: CollectionRow[];
  preferences?: PreferenceRow[];
}

export interface MergedState {
  profiles: MergedProfile[];
  /** Not scoped to a profile — see `schema.ts` on why `kids` alone has none. */
  kids?: ListRow[];
  /** Household-wide too, and kept per title the same way; see `schema.ts` v8. */
  editorsChoice?: ListRow[];
}
