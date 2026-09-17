/**
 * When to fetch more than was asked for.
 *
 * Caching only what was requested helps a second viewing and does nothing for
 * the first: playback walks forward, so every chunk is a miss until someone
 * has already watched it. Each miss is a Telegram round trip of 150-450 ms,
 * and a 4 Mbit/s stream wants a 512 KiB chunk about every second — misses in
 * series are what makes playback stutter.
 *
 * Reading ahead amortises one round trip over several chunks. It is done only
 * when reads look sequential: a scrub bar produces scattered reads, and
 * reading ahead from each jump would spend the uplink on bytes nobody will
 * watch.
 */

/** Sets tracked at once. A long-running server must not grow a map forever. */
const TRACKED = 64;

/** Where a part was last read to, so the next read can be recognised. */
interface Position {
  /** Byte offset just past the last read. */
  nextOffset: number;
  /** How many reads have continued in sequence. */
  run: number;
}

export class ReadaheadTracker {
  private readonly positions = new Map<string, Position>();

  /**
   * @param maxAhead chunks to read ahead once a sequence is established;
   *                 0 turns readahead off.
   */
  constructor(private readonly maxAhead: number) {}

  /**
   * How many extra chunks to fetch for this read.
   *
   * Grows with the length of the run rather than jumping straight to the
   * maximum: a single continuation may be coincidence, while a fifth is
   * someone watching.
   */
  aheadFor(setId: string, partIdx: number, start: number, length: number): number {
    if (this.maxAhead <= 0) return 0;

    const key = `${setId}/${partIdx}`;
    const previous = this.positions.get(key);
    const sequential = previous !== undefined && start === previous.nextOffset;
    const run = sequential ? previous.run + 1 : 0;

    // Re-inserting keeps this key newest for the eviction below.
    this.positions.delete(key);
    this.positions.set(key, { nextOffset: start + length, run });

    if (this.positions.size > TRACKED) {
      // Oldest first: Map preserves insertion order.
      const oldest = this.positions.keys().next();
      if (!oldest.done) this.positions.delete(oldest.value);
    }

    return Math.min(run, this.maxAhead);
  }

  /** Tracked positions, for the test that memory stays bounded. */
  size(): number {
    return this.positions.size;
  }
}
