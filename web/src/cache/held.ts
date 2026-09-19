/**
 * Which titles are on this machine in full.
 *
 * A set is held when every chunk of every part is on disk, which makes it
 * playable with no Telegram at all — the claim the shelf badge makes. Worked
 * out by counting files rather than by reading them: a part of `n` bytes is
 * `ceil(n / CACHE_CHUNK)` chunks, and the cache names every chunk after its
 * index, so the count on disk against the count the index implies is the
 * whole question.
 *
 * **Counting proves presence, not integrity.** A truncated chunk passes it.
 * That is survivable rather than ignored: `ChunkCache.get` checks a chunk's
 * length on the way out and removes one that is wrong, so a badge that was
 * over-confident costs a refetch, not a failed play. Reading 12,000 files to
 * be certain would cost more than the answer is worth.
 */

import { readdir } from "node:fs/promises";
import { join } from "node:path";
import type { Database } from "bun:sqlite";
import { CACHE_CHUNK } from "./key";

/** How long a reading is trusted before another scan is started. */
const TTL_MS = 30_000;

/**
 * Chunks each set would need, from the index alone.
 *
 * Summed in SQL because the arithmetic is integer division and SQLite does it
 * in one pass over a table that is already indexed by set. Only `done` parts
 * count: a set still uploading cannot be complete on a player's disk either.
 *
 * Folded once by the caller and never again — the catalog is read-only for
 * the life of the process, so what a set needs cannot change under us.
 */
export function expectedChunks(db: Database): Map<string, number> {
  const rows = db
    .query(
      `SELECT set_id AS setId, SUM((byte_length + ${CACHE_CHUNK - 1}) / ${CACHE_CHUNK}) AS chunks
         FROM parts
        WHERE status = 'done'
        GROUP BY set_id`,
    )
    .all() as { setId: string; chunks: number }[];

  const expected = new Map<string, number>();
  for (const row of rows) {
    if (row.chunks > 0) expected.set(row.setId, row.chunks);
  }
  return expected;
}

/**
 * The held sets, answered now and rescanned in the background.
 *
 * Synchronous on the way out because the catalog route builds every row in one
 * pass and cannot await per title. The scan is cheap — a few milliseconds for
 * four hundred sets — but it is still IO, and doing it inside the pass would
 * put it on the request's critical path for an answer that barely changes.
 *
 * So a reading is served from the last scan and a new one is started when it
 * has gone stale. The first page load after startup is already right because
 * `refresh` is awaited once before the server listens.
 */
export class HeldSets {
  private held: ReadonlySet<string> = new Set();
  private scannedAt = 0;
  /** In flight, so a burst of requests shares one scan rather than each starting one. */
  private scanning: Promise<void> | null = null;

  constructor(
    /** The cache directory itself; the chunk size is a level inside it. */
    private readonly root: string,
    private readonly expected: Map<string, number>,
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** Whether this set plays without the network, as of the last scan. */
  has(setId: string): boolean {
    return this.held.has(setId);
  }

  /** How many are held, for anything that wants to say so. */
  get count(): number {
    return this.held.size;
  }

  /** Starts a scan if the reading has gone stale. Does not wait for it. */
  refreshIfStale(): void {
    if (this.now() - this.scannedAt < TTL_MS) return;
    void this.refresh();
  }

  /**
   * Rescans now. Shared when one is already running.
   *
   * Deliberately not `async`: that would wrap the shared promise in a fresh
   * one per caller, so two callers would hold two promises over one scan and
   * nothing could see that the scan was shared.
   */
  refresh(): Promise<void> {
    if (this.scanning) return this.scanning;
    this.scanning = this.scan()
      .then((held) => {
        this.held = held;
        this.scannedAt = this.now();
      })
      // A cache directory that cannot be read is a cache that answers nothing,
      // not a player that stops working. The previous reading stands.
      .catch(() => {})
      .finally(() => {
        this.scanning = null;
      });
    return this.scanning;
  }

  private async scan(): Promise<Set<string>> {
    const root = join(this.root, String(CACHE_CHUNK));
    const held = new Set<string>();

    for (const [setId, want] of this.expected) {
      let have = 0;
      try {
        for (const part of await readdir(join(root, setId), { withFileTypes: true })) {
          if (!part.isDirectory()) continue;
          have += (await readdir(join(root, setId, part.name))).length;
        }
      } catch {
        // Nothing cached for this set, which is the ordinary case.
        continue;
      }
      // `>=` rather than `===`: a chunk left over from a part layout that has
      // since changed would make the count exceed what is needed, and every
      // chunk that is needed is still there.
      if (have >= want) held.add(setId);
    }
    return held;
  }
}
