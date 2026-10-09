/**
 * Types for the session `library-session.js` builds.
 *
 * The module is plain JavaScript because the browser loads it directly; this
 * declares its shape so the tests get the contract without a second copy of
 * the logic — the same arrangement `library.d.ts` has.
 */

import type { CatalogSet, Library } from "./library.js";
import type { Session } from "./together/session.js";

/** What a `together` stream event carries; `session` is `null` once it is over. */
export interface TogetherPayload { id: number; session: Session | null; serverNow: number }

/** The stream events the server sends. Only `together` carries a payload the
 *  session needs; what else changed is asked for separately, over `fetchCatalog`. */
export interface LibraryPortListeners {
  catalog(): void;
  open(): void;
  state(): void;
  together(payload: TogetherPayload): void;
}

/** Watch together's share of the stream (`together-now.js`). */
export interface LibraryTogether {
  heard(payload: TogetherPayload): void;
  /** The stream opened, again or for the first time: read the session afresh. */
  reopened(): void;
  /** Whether this tab is in a session, which keeps its stream open while hidden. */
  inSession(): boolean;
  /** `listener` runs each time this tab is out of a session. */
  onLeave(listener: () => void): void;
}

/** Everything the session needs from the browser, so it can be faked. */
export interface LibraryPort {
  /** The catalog body, or a rejection with the same text a failed fetch has. */
  fetchCatalog(): Promise<string>;
  /** Opens the update stream; the returned function closes it. */
  listen(handlers: LibraryPortListeners): () => void;
  visible(): boolean;
  /** Calls `fn` on every visibility change, not only real transitions. */
  onVisibility(fn: () => void): void;
}

/** The slice of `watch-state.js` the session reads to filter for a profile. */
export interface LibrarySessionState {
  profile(): { kids?: boolean; kidsAge?: number | null } | null;
  /** Every Kids mark, set id -> the age it is for kids from (6 or 12). */
  kidsMarks(): Map<string, number>;
}

export interface LibrarySessionSnapshot {
  library: Library;
  byId: Map<string, CatalogSet>;
  /** The committed catalog after this profile's filter, kids or not. */
  visibleSets: CatalogSet[];
}

export interface LibrarySession {
  /** The first catalog read; rejects on failure. */
  start(): Promise<boolean>;
  current(): LibrarySessionSnapshot;
  reapply(): void;
  invalidate(): void;
  /** Every draw already covers whatever redraw was owed; clears it without
   *  notifying, so a hold releasing afterwards does not fire a second one. */
  drawn(): void;
  /** Immediate: counts, colophon; `catalog` when a live refresh replaced a
   *  catalog already showing — not `start()`'s own first load, a profile
   *  switch, or a watch-state change. Returns the unsubscribe. */
  onData(fn: (change: { catalog: boolean }) => void): () => void;
  /** Once per change, never while held. Returns the unsubscribe. */
  onRedraw(fn: () => void): () => void;
  /** Returns `release`; the last one delivers a redraw owed while held. */
  hold(): () => void;
}

export function createLibrarySession(deps: {
  port: LibraryPort;
  state: LibrarySessionState;
  remoteState: () => void;
  together: LibraryTogether;
}): LibrarySession;
