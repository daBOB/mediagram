/**
 * The startup facts and the live ones, folded into one answer.
 *
 * Pure: everything that needs IO — scanning the cache directory, asking the
 * registry what is running — is done by the caller and handed in. That keeps
 * the shape of the answer testable without a player, which is the only
 * practical way to assert on it.
 */

import type { StartupFacts } from "./facts";
import type { LoopLagReading } from "./loop-lag";
import type { DiskFree } from "./disk-free";
import type { LinkSnapshot } from "../telegram/link-stats";
import type { TranscodeMode } from "../transcode/registry";

/** One running conversion, as the panel shows it. */
export interface TranscodeSession {
  setId: string;
  seekSeconds: number;
  maxrateBits: number;
  audioTrack: number;
  watchers: number;
  mode: TranscodeMode;
  /** As a multiple of realtime, or `null` for ffmpeg's own `N/A`. */
  speed: number | null;
  fps: number | null;
  /** How far into the output ffmpeg has written, in seconds. */
  outSeconds: number | null;
  /** Since the previous reading; `null` off Linux or before there is one. */
  cpuPercent: number | null;
  segments: number;
}

/** The process and machine figures read fresh on every request. */
export interface HostLiveFacts {
  /** Resident set size, in bytes. */
  rssBytes: number;
  /** `heapUsed` only: Bun's `heapTotal` can read lower than `heapUsed`. */
  heapBytes: number;
  /** The last complete 10s window, or `null` before one has finished. */
  loopLagMs: LoopLagReading | null;
  disks: DiskFree[];
}

/** The host group as the snapshot reports it: the live figures plus the runtime version. */
export interface HostFacts extends HostLiveFacts {
  bun: string;
}

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
    /** Sessions started since this process began, by mode. Joining one already running does not count. */
    started: { encode: number; copy: number; hevcCopy: number };
    sessions: TranscodeSession[];
  };
  /** Bytes the conversions have written and not yet had reaped. */
  transcodeBytes: number | null;
  /** `null` when the client cannot say, which is not the same as "no". */
  telegramConnected: boolean | null;
  /** Reads that ended in an error rather than in bytes. */
  failedReads: number;
  link: LinkSnapshot;
  host: HostLiveFacts;
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
    link: live.link,
    state: facts.state,
    host: { ...live.host, bun: facts.runtime.bun },
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
