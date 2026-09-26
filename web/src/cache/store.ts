/**
 * Chunks on disk, under a quota.
 *
 * A media cache with no ceiling fills whatever it is given, and this library
 * is tens of gigabytes — tens of thousands of chunk files — on a machine with
 * other work to do. The budget is the point of the thing: eviction is
 * least-recently-used, driven by each file's access time, so a series being
 * watched stays resident while last month's film falls out.
 *
 * Writes go to a temporary name and are renamed into place, which is atomic
 * on the same filesystem. A reader therefore never sees a half-written chunk,
 * and an interrupted write leaves a stray temporary rather than a plausible
 * lie.
 */

import { mkdir, readdir, rename, rm, stat, utimes, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";

import { chunkPath } from "./key";

interface Entry {
  path: string;
  size: number;
  usedAt: number;
}

/** What the cache has done since the process started. */
export interface CacheStats {
  hits: number;
  misses: number;
  /** Chunks discarded to stay under the budget. */
  evicted: number;
}

export class ChunkCache {
  /** Chunks being written right now, so two viewers do not race one file. */
  private readonly inFlight = new Map<string, Promise<void>>();

  /**
   * Bytes of a chunk whose write has not landed on disk yet.
   *
   * The reader that fetched them does not wait for `put` before serving them
   * or before a second reader of the same chunk asks: without this, that
   * second reader would miss on disk and pay for the same bytes from
   * Telegram a second time, mid-write. Cleared the moment the write settles,
   * whatever its outcome — a failed write leaves nothing here to serve
   * either.
   */
  private readonly pending = new Map<string, Uint8Array>();

  /** The one eviction scan running now, if any. */
  private evicting: Promise<number> | null = null;
  /** A write landed while a scan was already running; run it again once. */
  private evictAgain = false;

  /**
   * Counters, kept in memory and only ever incremented.
   *
   * Plain integers rather than anything structured: `get` is on the byte path
   * and runs for every 512 KiB of every stream, so the bookkeeping has to
   * cost nothing. They are a running total since startup, not a rate — a
   * reader that wants a rate can take two readings.
   */
  private hits = 0;
  private misses = 0;
  private evicted = 0;

  constructor(
    private readonly root: string,
    private maxBytes: number,
  ) {}

  /** What this cache has done so far, and what it is allowed to hold. */
  stats(): CacheStats {
    return {
      hits: this.hits,
      misses: this.misses,
      evicted: this.evicted,
    };
  }

  /** The budget this cache was given, in bytes. */
  get budget(): number {
    return this.maxBytes;
  }

  /**
   * Changes the budget and, when it shrank, evicts down to it.
   *
   * Applied before eviction runs, so a crash mid-evict still starts next time
   * at the new budget rather than the old one: the caller persists this
   * number first (Settings' own precedence), and this only ever makes the
   * on-disk cache agree with what is already recorded.
   */
  async setBudget(bytes: number): Promise<{ freedBytes: number }> {
    this.maxBytes = bytes;
    const freedBytes = await this.scheduleEviction();
    return { freedBytes };
  }

  /**
   * A chunk's bytes, or `null` on a miss.
   *
   * `expectedSize` is checked when the caller knows it: a file of the wrong
   * length is a partial write, not data, and is removed rather than served.
   * The last chunk of a part is legitimately short, so callers only pass a
   * size when they know a full chunk was stored.
   */
  async get(
    setId: string,
    partIdx: number,
    index: number,
    expectedSize?: number,
  ): Promise<Uint8Array | null> {
    const path = chunkPath(this.root, setId, partIdx, index);

    const held = this.pending.get(path);
    if (held !== undefined) {
      if (expectedSize !== undefined && held.byteLength !== expectedSize) {
        this.misses += 1;
        return null;
      }
      this.hits += 1;
      return held;
    }

    try {
      const bytes = new Uint8Array(await Bun.file(path).arrayBuffer());
      if (expectedSize !== undefined && bytes.byteLength !== expectedSize) {
        // A partial write is a miss, not a hit: the caller has to fetch it
        // either way, and counting it as a hit would flatter the cache.
        await rm(path, { force: true });
        this.misses += 1;
        return null;
      }
      // Mark the use, which is what eviction orders by. Best effort: a cache
      // that cannot record a touch is still a working cache.
      const now = new Date();
      void utimes(path, now, now).catch(() => {});
      this.hits += 1;
      return bytes;
    } catch {
      this.misses += 1;
      return null;
    }
  }

  /**
   * Whether a chunk is held, without reading its bytes.
   *
   * For callers that only need to know a chunk is already there — deciding
   * where a fetch run may stop, or which of a title's chunks are still
   * missing — reading the whole 512 KiB just to test for `null` cost as much
   * disk I/O as serving it, twice over during steady playback. A `stat` is
   * one syscall's worth of metadata and does not count as a hit or a miss:
   * it is not a read on anyone's behalf, so the status page's rate would
   * otherwise flatter itself on every probe.
   *
   * Also does not bump the chunk's access time. Eviction should not be told
   * a chunk was used because something asked whether it was there —
   * `get` is what a real read touches, and stays the only thing that does.
   */
  async has(setId: string, partIdx: number, index: number, expectedSize?: number): Promise<boolean> {
    const path = chunkPath(this.root, setId, partIdx, index);

    const held = this.pending.get(path);
    if (held !== undefined) return expectedSize === undefined || held.byteLength === expectedSize;

    try {
      const info = await stat(path);
      return expectedSize === undefined || info.size === expectedSize;
    } catch {
      return false;
    }
  }

  /**
   * Attempts to store a chunk and enforce the budget without interrupting playback.
   * Write failures are ignored and eviction failures are logged; successful
   * resolution guarantees neither persistence nor compliance with the budget.
   */
  async put(setId: string, partIdx: number, index: number, bytes: Uint8Array): Promise<void> {
    const path = chunkPath(this.root, setId, partIdx, index);

    const existing = this.inFlight.get(path);
    if (existing) return existing;

    this.pending.set(path, bytes);
    const write = this.writeChunk(path, bytes).finally(() => {
      this.inFlight.delete(path);
      this.pending.delete(path);
    });
    this.inFlight.set(path, write);
    return write;
  }

  private async writeChunk(path: string, bytes: Uint8Array): Promise<void> {
    try {
      await mkdir(dirname(path), { recursive: true });
      // Renamed into place so a reader never sees a partial file.
      const temporary = `${path}.${process.pid}.${Math.random().toString(36).slice(2)}.tmp`;
      await writeFile(temporary, bytes);
      await rename(temporary, path);
    } catch {
      // A cache that cannot write is a slow cache, not a broken player.
      return;
    }
    // Maintenance must not reject bytes already fetched for playback. An
    // explicit evict() still reports failures to its caller.
    await this.scheduleEviction().catch((error) => console.warn("cache eviction failed:", error));
  }

  /** Total bytes currently held. */
  async sizeOnDisk(): Promise<number> {
    return (await this.entries()).reduce((total, entry) => total + entry.size, 0);
  }

  /**
   * Removes least-recently-used chunks until the cache fits its budget.
   *
   * Returns the bytes freed. Delegates to the same coalesced scan a write
   * schedules: an explicit call made while one is already running joins it
   * rather than walking the disk a second time in parallel, and still
   * resolves to a freed-bytes total once it settles.
   */
  evict(): Promise<number> {
    return this.scheduleEviction();
  }

  /**
   * Runs at most one scan at a time.
   *
   * A run of writes each schedule eviction, and each used to pay for its own
   * full walk of the cache — the point a viewer actually waits on, since a
   * write does not resolve until its eviction does. A write that lands while
   * a scan is already in flight cannot have been seen by it, so rather than
   * starting a second walk alongside the first it marks the running one to
   * go again once, and both callers share its result.
   */
  private scheduleEviction(): Promise<number> {
    if (this.evicting) {
      this.evictAgain = true;
      return this.evicting;
    }
    const run = async (): Promise<number> => {
      let freed = 0;
      do {
        this.evictAgain = false;
        freed += await this.runEviction();
      } while (this.evictAgain);
      return freed;
    };
    this.evicting = run().finally(() => { this.evicting = null; });
    return this.evicting;
  }

  private async runEviction(): Promise<number> {
    const entries = await this.entries();
    let total = entries.reduce((sum, entry) => sum + entry.size, 0);
    if (total <= this.maxBytes) return 0;

    entries.sort((a, b) => a.usedAt - b.usedAt);
    let freed = 0;
    for (const entry of entries) {
      if (total <= this.maxBytes) break;
      await rm(entry.path, { force: true });
      total -= entry.size;
      freed += entry.size;
      this.evicted += 1;
    }
    return freed;
  }

  /** Every chunk file, with its size and last use. */
  private async entries(): Promise<Entry[]> {
    const found: Entry[] = [];
    const walk = async (directory: string): Promise<void> => {
      let listing;
      try {
        listing = await readdir(directory, { withFileTypes: true });
      } catch (error) {
        if (isMissing(error)) return;
        throw error;
      }
      // A scan walks the whole cache — tens of thousands of files — and a
      // write waits on the scan it schedules, so a directory's entries are
      // looked at together rather than one by one. Order is free: eviction
      // sorts by last use and the total only sums.
      await Promise.all(
        listing.map(async (item) => {
          const path = join(directory, item.name);
          if (item.isDirectory()) {
            await walk(path);
          } else if (!item.name.endsWith(".tmp")) {
            try {
              const info = await stat(path);
              found.push({ path, size: info.size, usedAt: info.atimeMs });
            } catch (error) {
              // Evicted by someone else between the listing and the stat.
              if (!isMissing(error)) throw error;
            }
          }
        }),
      );
    };
    await walk(this.root);
    return found;
  }
}

function isMissing(error: unknown): boolean {
  return error instanceof Error && "code" in error && error.code === "ENOENT";
}
