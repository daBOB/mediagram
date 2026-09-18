/**
 * The startup facts and the live ones, folded into one answer.
 *
 * Pure: everything that needs IO — scanning the cache directory, asking the
 * registry what is running — is done by the caller and handed in. That keeps
 * the shape of the answer testable without a player, which is the only
 * practical way to assert on it.
 */

import type { StartupFacts } from "./facts";

/** What is true only at the moment the question is asked. */
export interface LiveFacts {
  /** Bytes on disk now, or `null` when caching is off or unmeasured. */
  cacheHeldBytes: number | null;
  cacheHits: number;
  cacheMisses: number;
  cacheEvicted: number;
  /** Bytes that actually crossed the wire since startup. */
  fetchedBytes: number;
  transcodes: {
    running: number;
    capacity: number;
    sessions: Array<{
      setId: string;
      seekSeconds: number;
      maxrateBits: number;
      audioTrack: number;
      watchers: number;
    }>;
  };
  /** Bytes the conversions have written and not yet had reaped. */
  transcodeBytes: number | null;
  /** `null` when the client cannot say, which is not the same as "no". */
  telegramConnected: boolean | null;
  /** Reads that ended in an error rather than in bytes. */
  failedReads: number;
  /** Resident set size, in bytes. */
  memoryBytes: number;
  now: number;
}

/**
 * The reading a viewer is shown.
 *
 * Session ids are left out on purpose. They name a directory this server
 * writes to and a URL that serves it, and neither is a thing the page needs
 * in order to say that two conversions are running.
 */
export function buildSnapshot(facts: StartupFacts, live: LiveFacts) {
  return {
    catalog: facts.catalog,
    encoder: facts.encoder,
    cache:
      facts.cache === null
        ? null
        : {
            ...facts.cache,
            heldBytes: live.cacheHeldBytes,
            hits: live.cacheHits,
            misses: live.cacheMisses,
            evicted: live.cacheEvicted,
            fetchedBytes: live.fetchedBytes,
            // Stated rather than left to be divided: a hit rate of zero and
            // no reads at all are different things, and only one of them is
            // worth worrying about.
            hitRate: hitRate(live.cacheHits, live.cacheMisses),
          },
    transcodes: {
      ...live.transcodes,
      dir: facts.transcodeDir,
      heldBytes: live.transcodeBytes,
    },
    telegram: { connected: live.telegramConnected, failedReads: live.failedReads },
    state: facts.state,
    memoryBytes: live.memoryBytes,
    uptimeSeconds: Math.max(0, Math.round((live.now - facts.startedAt) / 1000)),
    // Two readings and the seconds between them are all a caller needs to
    // work out a rate, so the rate is not computed here: the panel polls at a
    // cadence this module does not know, and an average since startup is not
    // the number anyone watching a stall wants.
    fetchedBytes: live.fetchedBytes,
  };
}

/** Hits over reads, or `null` before anything has been read. */
function hitRate(hits: number, misses: number): number | null {
  const reads = hits + misses;
  return reads === 0 ? null : hits / reads;
}
