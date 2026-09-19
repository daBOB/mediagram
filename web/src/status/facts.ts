/**
 * What the player worked out at startup, kept rather than only printed.
 *
 * `index.ts` decides all of this once — which catalog opened, whether the
 * refresh worked, which encoder actually initialises, what the cache is
 * allowed to hold — writes it to a terminal nobody is looking at, and throws
 * it away. This is the value it fills instead, so the same facts can be asked
 * for later by someone sitting in front of the screen.
 *
 * Deliberately data and nothing else: no handles, no paths that are not meant
 * to be shown, no way back to the objects these were read from.
 */

export interface CatalogFacts {
  origin: "package" | "local";
  /** When the package was built, in milliseconds. `null` for a local index. */
  publishedAt: number | null;
  /** This run's refresh verdict, or `null` when no package is configured. */
  refresh: "updated" | "unchanged" | "kept" | null;
  /** Why a refresh was refused, when it was. */
  reason: string | null;
  /** The index schema the catalog is at. */
  schema: number;
  /** Playable sets, counted once: the catalog cannot change while we run. */
  sets: number;
  posters: number;
}

export interface EncoderFacts {
  name: string;
  kind: string;
  /** The DRM render node, for a VAAPI encoder. */
  device: string | null;
}

export interface CacheFacts {
  dir: string;
  /** What it is allowed to hold, in bytes. */
  budget: number;
  /** Chunks fetched ahead of the read, from `MEDIAGRAM_CACHE_READAHEAD`. */
  readahead: number;
}

export interface StartupFacts {
  catalog: CatalogFacts;
  encoder: EncoderFacts;
  /** Where conversions are written. Cleared at startup, reaped while running. */
  transcodeDir: string;
  /** `null` when the budget is zero, which turns caching off entirely. */
  cache: CacheFacts | null;
  /** Whether watch positions are kept at all, and where. */
  state: { remembered: boolean; path: string | null };
  /** `Date.now()` as the process finished starting. */
  startedAt: number;
}
