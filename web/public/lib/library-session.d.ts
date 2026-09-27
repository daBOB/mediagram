/**
 * Types for the session `library-session.js` builds.
 *
 * The module is plain JavaScript because the browser loads it directly; this
 * declares its shape so the tests get the contract without a second copy of
 * the logic — the same arrangement `library.d.ts` has.
 */

import type { CatalogSet, Library } from "./library.js";

/** The three stream events the server sends, none carrying a payload the
 *  session needs: what changed is asked for separately, over `fetchCatalog`. */
export interface LibraryPortListeners {
  catalog(): void;
  open(): void;
  state(): void;
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
  profile(): { kids?: boolean } | null;
  kids(): string[];
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
}): LibrarySession;
