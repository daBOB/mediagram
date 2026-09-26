/**
 * Assembles the figures that can only be read at the moment of asking.
 *
 * Moved out of `index.ts`, which wired every dependency by hand inline. The
 * shape returned matches `LiveFacts` minus the fields `routes.ts` fills in
 * from its own memoized scans (`cacheHeldBytes`, `transcodeBytes`, `now`).
 */

import { diskFree } from "./disk-free";
import { countSegments } from "./dir-bytes";
import type { LoopLag } from "./loop-lag";
import type { LiveFacts, TranscodeSession } from "./snapshot";
import type { TranscodeMode } from "../transcode/registry";
import type { TranscodeProgress } from "../transcode/progress";

interface CacheStats {
  stats(): { hits: number; misses: number; evicted: number };
}

interface ReaderStats {
  stats(): { fetchedBytes: number };
}

interface TranscodeSessions {
  count(): number;
  capacity: number;
  started: { encode: number; copy: number; hevcCopy: number };
  list(): Array<{
    setId: string;
    seekSeconds: number;
    maxrateBits: number;
    audioTrack: number;
    watchers: number;
    directory: string;
    mode: TranscodeMode;
    progress: TranscodeProgress | null;
  }>;
}

interface ByteSourceStats {
  stats(): { failedReads: number };
}

export interface LiveFactsDeps {
  cache: CacheStats | null;
  reader: ReaderStats | null;
  transcodes: TranscodeSessions;
  telegram: { connected: boolean | null; link(): LiveFacts["link"] };
  bytes: ByteSourceStats;
  loopLag: Pick<LoopLag, "reading">;
  /** Directories to report free space under: the cache and the transcode dir. */
  diskDirs: string[];
}

export async function readLiveFacts(
  deps: LiveFactsDeps,
): Promise<Omit<LiveFacts, "cacheHeldBytes" | "transcodeBytes" | "now">> {
  const cacheStats = deps.cache?.stats();
  const [disks, sessions] = await Promise.all([diskFree(deps.diskDirs), transcodeSessions(deps.transcodes)]);

  return {
    cacheHits: cacheStats?.hits ?? 0,
    cacheMisses: cacheStats?.misses ?? 0,
    cacheEvicted: cacheStats?.evicted ?? 0,
    fetchedBytes: deps.reader?.stats().fetchedBytes ?? 0,
    transcodes: {
      running: deps.transcodes.count(),
      capacity: deps.transcodes.capacity,
      started: deps.transcodes.started,
      sessions,
    },
    telegramConnected: deps.telegram.connected,
    link: deps.telegram.link(),
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

/**
 * Each running session, plus its segment count.
 *
 * The one figure not already sitting on the tracked session: it costs a
 * directory listing, so it is read here, at poll time, rather than kept
 * up to date on every segment ffmpeg writes.
 */
async function transcodeSessions(transcodes: TranscodeSessions): Promise<TranscodeSession[]> {
  return Promise.all(
    transcodes.list().map(async (session) => ({
      setId: session.setId,
      seekSeconds: session.seekSeconds,
      maxrateBits: session.maxrateBits,
      audioTrack: session.audioTrack,
      watchers: session.watchers,
      mode: session.mode,
      speed: session.progress?.speed ?? null,
      fps: session.progress?.fps ?? null,
      outSeconds: session.progress?.outSeconds ?? null,
      cpuPercent: session.progress?.cpuPercent ?? null,
      segments: await countSegments(session.directory),
    })),
  );
}
