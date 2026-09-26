/**
 * Assembles the figures that can only be read at the moment of asking.
 *
 * Moved out of `index.ts`, which wired every dependency by hand inline. The
 * shape returned matches `LiveFacts` minus the fields `routes.ts` fills in
 * from its own memoized scans (`cacheHeldBytes`, `transcodeBytes`, `now`).
 */

import { diskFree } from "./disk-free";
import type { LoopLag } from "./loop-lag";
import type { LiveFacts } from "./snapshot";

interface CacheStats {
  stats(): { hits: number; misses: number; evicted: number };
}

interface ReaderStats {
  stats(): { fetchedBytes: number };
}

interface TranscodeSessions {
  count(): number;
  capacity: number;
  list(): Array<{ setId: string; seekSeconds: number; maxrateBits: number; audioTrack: number; watchers: number }>;
}

interface ByteSourceStats {
  stats(): { failedReads: number };
}

export interface LiveFactsDeps {
  cache: CacheStats | null;
  reader: ReaderStats | null;
  transcodes: TranscodeSessions;
  telegram: { connected: boolean | null };
  bytes: ByteSourceStats;
  loopLag: Pick<LoopLag, "reading">;
  /** Directories to report free space under: the cache and the transcode dir. */
  diskDirs: string[];
}

export async function readLiveFacts(
  deps: LiveFactsDeps,
): Promise<Omit<LiveFacts, "cacheHeldBytes" | "transcodeBytes" | "now">> {
  const cacheStats = deps.cache?.stats();
  const disks = await diskFree(deps.diskDirs);

  return {
    cacheHits: cacheStats?.hits ?? 0,
    cacheMisses: cacheStats?.misses ?? 0,
    cacheEvicted: cacheStats?.evicted ?? 0,
    fetchedBytes: deps.reader?.stats().fetchedBytes ?? 0,
    transcodes: {
      running: deps.transcodes.count(),
      capacity: deps.transcodes.capacity,
      sessions: deps.transcodes.list().map(({ setId, seekSeconds, maxrateBits, audioTrack, watchers }) => ({
        setId,
        seekSeconds,
        maxrateBits,
        audioTrack,
        watchers,
      })),
    },
    telegramConnected: deps.telegram.connected,
    failedReads: deps.bytes.stats().failedReads,
    host: {
      // Resident set size: the figure that says whether a player left running
      // for a week is still the size it started at.
      rssBytes: process.memoryUsage.rss(),
      heapBytes: process.memoryUsage().heapUsed,
      loopLagMs: deps.loopLag.reading(),
      disks,
    },
  };
}
