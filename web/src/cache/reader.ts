/**
 * Reading a part's bytes through the cache.
 *
 * Any range becomes a list of fixed aligned chunks. Chunks already on disk
 * are served from there; the rest are fetched — and this is the part that
 * matters — in *runs*, so a stretch of missing chunks costs one upstream
 * request rather than one each.
 *
 * Fetching per chunk turned a streaming download into a series of round
 * trips: a 20 MB read became forty of them, which is slower than having no
 * cache at all, and slow enough that ffmpeg could not get through a file.
 *
 * Runs are capped, though. A whole-file request over a cold cache is one
 * unbroken run of misses, and an uncapped one would fetch the rest of the
 * film in a single call: gigabytes held in memory, nothing yielded until it
 * finished, and a player that gave up long before.
 */

import { CACHE_CHUNK, chunksCovering } from "./key";
import { ReadaheadTracker } from "./strategy";
import type { ChunkCache } from "./store";

/**
 * The most a single upstream request may cover.
 *
 * Large enough that a sequential read is still batched rather than made a
 * chunk at a time, small enough that the first bytes reach the viewer in
 * about a second and memory stays flat however long the range is.
 */
export const MAX_RUN_BYTES = 8 * CACHE_CHUNK;

/** Fetches `length` bytes at `offset` within a part, from upstream. */
export type FetchRange = (offset: number, length: number) => Promise<Uint8Array>;

/** The requested range and the enclosing part's cache/upstream identity. */
export interface CachedReadRequest {
  setId: string;
  partIdx: number;
  start: number;
  length: number;
  partLength: number;
  /** Omit for disk-only reads: a missing/invalid chunk fails, without readahead. */
  fetch?: FetchRange;
}

/** A stretch of consecutive chunk indexes that all need fetching. */
interface Run {
  first: number;
  last: number;
}

export class CachedReader {
  private readonly tracker: ReadaheadTracker;
  /** Readahead fetches in flight, so tests and shutdown can wait for them. */
  private readonly warming = new Set<Promise<void>>();
  /**
   * Bytes that actually crossed the wire, as opposed to coming off disk.
   *
   * The number the cache's hit count cannot give: hits are counted in chunks,
   * and what matters upstream is bytes. Together they are the difference
   * between "this is coming off disk" and "every byte of this is being
   * fetched again".
   */
  private fetchedBytes = 0;

  constructor(
    private readonly cache: ChunkCache,
    maxAhead = 0,
  ) {
    this.tracker = new ReadaheadTracker(maxAhead);
  }

  /** Bytes fetched upstream since startup, readahead included. */
  stats(): { fetchedBytes: number } {
    return { fetchedBytes: this.fetchedBytes };
  }

  /** Resolves once speculative fetches have finished. For tests. */
  async settle(): Promise<void> {
    await Promise.all([...this.warming]);
  }

  /**
   * `length` bytes at `start`, yielded as they become available.
   *
   * Streaming rather than returning one array is what lets the response send
   * its headers immediately and keeps memory flat: a request without a Range
   * covers a whole film, and buffering that would hold gigabytes.
   *
   * Cached chunks are yielded as they are read; a run of missing ones is
   * fetched in a single request and then yielded chunk by chunk.
   */
  async *readStream(
    { setId, partIdx, start, length, partLength, fetch }: CachedReadRequest,
  ): AsyncGenerator<Uint8Array, void, unknown> {
    const slices = chunksCovering(start, length);
    let at = 0;

    while (at < slices.length) {
      const slice = slices[at]!;
      const held = await this.cache.get(
        setId,
        partIdx,
        slice.index,
        expectedSize(slice.index, partLength),
      );
      if (held !== null) {
        yield held.subarray(slice.skip, slice.skip + slice.take);
        at += 1;
        continue;
      }
      if (fetch === undefined) {
        throw new Error(`cached chunk ${slice.index} of part ${partIdx} is unavailable`);
      }

      // How far the miss runs, so it can be fetched in one request — up to
      // the cap, past which a run is split rather than grown.
      const maxChunks = MAX_RUN_BYTES / CACHE_CHUNK;
      let end = at;
      while (end + 1 < slices.length && end + 1 - at < maxChunks) {
        const next = slices[end + 1]!;
        const cached = await this.cache.get(
          setId,
          partIdx,
          next.index,
          expectedSize(next.index, partLength),
        );
        if (cached !== null) break;
        end += 1;
      }

      const run = { first: slice.index, last: slices[end]!.index };
      const chunks = new Map<number, Uint8Array>();
      await this.fillRun(setId, partIdx, run, partLength, fetch, chunks);

      for (let i = at; i <= end; i++) {
        const piece = slices[i]!;
        const chunk = chunks.get(piece.index);
        // `fillRun` throws on a short answer, so this cannot happen; breaking
        // here instead of saying so is what turned a failed fetch into a hole
        // in the middle of a file.
        if (!chunk) throw new Error(`chunk ${piece.index} of part ${partIdx} did not arrive`);
        yield chunk.subarray(piece.skip, piece.skip + piece.take);
      }
      at = end + 1;
    }

    if (fetch !== undefined) this.warm(setId, partIdx, start, length, partLength, fetch);
  }

  /**
   * Fetches every chunk of a part that is not on disk yet, and keeps none of
   * it in memory beyond the run in hand.
   *
   * For taking a whole title ahead of time. No readahead: that exists to stay
   * in front of a viewer, and there is no viewer here — it would only fetch
   * the chunks this loop is about to fetch anyway.
   */
  async fill(setId: string, partIdx: number, partLength: number, fetch: FetchRange): Promise<void> {
    const lastInPart = Math.floor((partLength - 1) / CACHE_CHUNK);
    const missing: number[] = [];
    for (let index = 0; index <= lastInPart; index++) {
      const held = await this.cache.get(setId, partIdx, index, expectedSize(index, partLength));
      if (held === null) missing.push(index);
    }
    for (const run of runsOf(missing)) {
      await this.fillRun(setId, partIdx, run, partLength, fetch, new Map());
    }
  }

  /**
   * Fetches one run in a single request and caches each chunk of it.
   *
   * A short answer throws rather than caching what arrived. The end of a part
   * is already accounted for by clamping to `partLength`, so anything shorter
   * than that is bytes that did not come back — and a reader that carried on
   * would serve the next run's bytes where these belong, which is a corrupt
   * video under a Content-Length that says it is whole.
   */
  private async fillRun(
    setId: string,
    partIdx: number,
    run: Run,
    partLength: number,
    fetch: FetchRange,
    into: Map<number, Uint8Array>,
  ): Promise<void> {
    const offset = run.first * CACHE_CHUNK;
    const end = Math.min((run.last + 1) * CACHE_CHUNK, partLength);
    const wanted = end - offset;
    const bytes = await fetch(offset, wanted);
    this.fetchedBytes += bytes.length;
    if (bytes.length < wanted) {
      throw new Error(
        `short read of part ${partIdx}: asked for ${wanted} bytes at ${offset}, got ${bytes.length}`,
      );
    }

    for (let index = run.first; index <= run.last; index++) {
      const at = (index - run.first) * CACHE_CHUNK;
      const chunk = bytes.subarray(at, at + Math.min(CACHE_CHUNK, bytes.length - at));
      if (chunk.length === 0) break;
      into.set(index, chunk);
      // Cached per chunk, not per run, so a later read of any part of this
      // stretch hits without knowing how it was fetched.
      await this.cache.put(setId, partIdx, index, chunk);
    }
  }

  /**
   * Fetches the chunks after a sequential read, in the background.
   *
   * Deliberately not awaited: the point is that the next chunk is already
   * arriving when the browser asks, not that this read waits for it. Failures
   * are ignored — a speculative fetch that does not arrive costs a cache miss
   * later and nothing else.
   */
  private warm(
    setId: string,
    partIdx: number,
    start: number,
    length: number,
    partLength: number,
    fetch: FetchRange,
  ): void {
    const ahead = this.tracker.aheadFor(setId, partIdx, start, length);
    if (ahead === 0) return;

    const firstAfter = Math.floor((start + length - 1) / CACHE_CHUNK) + 1;
    const lastInPart = Math.floor((partLength - 1) / CACHE_CHUNK);
    const last = Math.min(firstAfter + ahead - 1, lastInPart);
    if (last < firstAfter) return;

    const task = (async () => {
      const missing: number[] = [];
      for (let index = firstAfter; index <= last; index++) {
        const held = await this.cache.get(setId, partIdx, index, expectedSize(index, partLength));
        if (held === null) missing.push(index);
      }
      for (const run of runsOf(missing)) {
        await this.fillRun(setId, partIdx, run, partLength, fetch, new Map());
      }
    })()
      .catch(() => {})
      .finally(() => this.warming.delete(task));
    this.warming.add(task);
  }
}

/** A chunk is full unless it is the last one in the part. */
function expectedSize(index: number, partLength: number): number {
  return Math.min(CACHE_CHUNK, partLength - index * CACHE_CHUNK);
}

/** Groups sorted indexes into consecutive runs, none longer than the cap. */
function runsOf(indexes: number[]): Run[] {
  const maxChunks = MAX_RUN_BYTES / CACHE_CHUNK;
  const runs: Run[] = [];
  for (const index of indexes) {
    const last = runs.at(-1);
    const extends_ = last && index === last.last + 1 && last.last - last.first + 1 < maxChunks;
    if (extends_) last.last = index;
    else runs.push({ first: index, last: index });
  }
  return runs;
}
