/**
 * Reading a part's bytes through the cache.
 *
 * Any range becomes a list of fixed aligned chunks. Each is served from disk
 * if it is there and fetched whole if it is not, which is why a scrub bar is
 * cheap the second time: the chunks a viewer already passed through are
 * already on disk, and seeking back costs nothing upstream.
 *
 * Fetching whole chunks also means a small read costs one 512 KiB request —
 * the same as Telegram's own granularity, so nothing is wasted that was not
 * already being transferred.
 */

import { CACHE_CHUNK, chunksCovering } from "./key";
import { ReadaheadTracker } from "./strategy";
import type { ChunkCache } from "./store";

/** Fetches `length` bytes at `offset` within a part, from upstream. */
export type FetchRange = (offset: number, length: number) => Promise<Uint8Array>;

export class CachedReader {
  private readonly tracker: ReadaheadTracker;
  /** Readahead fetches in flight, so tests and shutdown can wait for them. */
  private readonly warming = new Set<Promise<void>>();

  constructor(
    private readonly cache: ChunkCache,
    maxAhead = 0,
  ) {
    this.tracker = new ReadaheadTracker(maxAhead);
  }

  /** Resolves once speculative fetches have finished. For tests. */
  async settle(): Promise<void> {
    await Promise.all([...this.warming]);
  }

  /**
   * `length` bytes at `start` within one part.
   *
   * `partLength` is needed because the last chunk of a part is short, and a
   * short chunk is data rather than a truncated write.
   *
   * `fetch` is passed per read rather than held: every part lives in its own
   * message, so there is no one upstream to bind to. It also keeps this file
   * free of any knowledge of where bytes come from.
   */
  async read(
    setId: string,
    partIdx: number,
    start: number,
    length: number,
    partLength: number,
    fetch: FetchRange,
  ): Promise<Uint8Array> {
    const out = new Uint8Array(length);
    let written = 0;

    for (const slice of chunksCovering(start, length)) {
      // A chunk is full unless it is the last one in the part.
      const expected = Math.min(CACHE_CHUNK, partLength - slice.offset);

      let chunk = await this.cache.get(setId, partIdx, slice.index, expected);
      if (chunk === null) {
        chunk = await fetch(slice.offset, expected);
        // Awaited, not fired and forgotten: a viewer who seeks back a second
        // later must hit the cache, and that is the case the cache exists
        // for. The write is 512 KiB to local disk, and `put` swallows its own
        // failures, so a full or slow disk costs a moment rather than the
        // stream.
        await this.cache.put(setId, partIdx, slice.index, chunk);
      }

      out.set(chunk.subarray(slice.skip, slice.skip + slice.take), written);
      written += slice.take;
    }

    this.warm(setId, partIdx, start, length, partLength, fetch);
    return out;
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

    for (let index = firstAfter; index < firstAfter + ahead && index <= lastInPart; index++) {
      const offset = index * CACHE_CHUNK;
      const expected = Math.min(CACHE_CHUNK, partLength - offset);
      const task = (async () => {
        if ((await this.cache.get(setId, partIdx, index, expected)) !== null) return;
        const bytes = await fetch(offset, expected);
        await this.cache.put(setId, partIdx, index, bytes);
      })()
        .catch(() => {})
        .finally(() => this.warming.delete(task));
      this.warming.add(task);
    }
  }
}
