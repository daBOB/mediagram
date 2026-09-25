/**
 * Chunks on disk, under a quota.
 *
 * A media cache with no ceiling fills whatever it is given, and this library
 * is tens of gigabytes on a machine with other work to do. The budget is the
 * point of the thing: eviction is least-recently-used, driven by each file's
 * access time, so a series being watched stays resident while last month's
 * film falls out.
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
    private readonly maxBytes: number,
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
   * Attempts to store a chunk and enforce the budget without interrupting playback.
   * Write failures are ignored and eviction failures are logged; successful
   * resolution guarantees neither persistence nor compliance with the budget.
   */
  async put(setId: string, partIdx: number, index: number, bytes: Uint8Array): Promise<void> {
    const path = chunkPath(this.root, setId, partIdx, index);

    const existing = this.inFlight.get(path);
    if (existing) return existing;

    const write = this.writeChunk(path, bytes).finally(() => this.inFlight.delete(path));
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
    await this.evict().catch((error) => console.warn("cache eviction failed:", error));
  }

  /** Total bytes currently held. */
  async sizeOnDisk(): Promise<number> {
    return (await this.entries()).reduce((total, entry) => total + entry.size, 0);
  }

  /**
   * Removes least-recently-used chunks until the cache fits its budget.
   *
   * Returns the bytes freed. Scanning on each write is affordable because a
   * cache of this size holds thousands of files, not millions, and it keeps
   * the truth on disk rather than in an index that can drift from it.
   */
  async evict(): Promise<number> {
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
      // Every write scans the whole cache, and a viewer waits on that write,
      // so a directory's entries are looked at together rather than one by
      // one. Order is free: eviction sorts by last use and the total only sums.
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
